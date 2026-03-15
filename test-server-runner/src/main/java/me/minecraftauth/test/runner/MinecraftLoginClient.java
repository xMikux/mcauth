/*
 * Copyright 2021-2026 MinecraftAuth.me
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.minecraftauth.test.runner;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Raw-socket Minecraft offline login client.
 * Implements the Minecraft Login protocol (offline mode, no encryption):
 * Handshake → LoginStart → read Disconnect or LoginSuccess
 */
public class MinecraftLoginClient implements AutoCloseable {

    public enum LoginResult { DISCONNECTED, LOGIN_SUCCESS, TIMEOUT, ERROR }
    public record LoginAttempt(LoginResult result, String message) {}

    private final String host;
    private final int port;
    private final int protocolVersion;
    private final int timeoutMs;

    private Socket socket;

    public MinecraftLoginClient(String host, int port, int protocolVersion, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.protocolVersion = protocolVersion;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Connect as {@code username} using the provided offline UUID.
     */
    public LoginAttempt attemptLogin(String username, UUID playerUUID) throws IOException {
        socket = new Socket();
        socket.setSoTimeout(timeoutMs);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);

        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        try {
            // Send Handshake packet (ID 0x00, state=Handshake)
            sendHandshake(out, host, port);

            // Send LoginStart packet (ID 0x00, state=Login)
            sendLoginStart(out, username, playerUUID);

            // After SetCompression (0x03), packets include an extra "Data Length" VarInt
            // before the packet ID. 0 = uncompressed payload follows; >0 = zlib payload follows.
            boolean compressionEnabled = false;

            // Read response loop
            while (true) {
                int packetLength = readVarInt(in);
                if (packetLength <= 0) {
                    return new LoginAttempt(LoginResult.ERROR, "Invalid packet length: " + packetLength);
                }

                byte[] packetData = in.readNBytes(packetLength);
                DataInputStream packetIn = new DataInputStream(new ByteArrayInputStream(packetData));

                if (compressionEnabled) {
                    int dataLength = readVarInt(packetIn);
                    if (dataLength != 0) {
                        // Compressed data — we shouldn't receive compressed packets during login
                        // (threshold is usually high enough that small login packets stay uncompressed)
                        // but handle it gracefully: just try to read the uncompressed payload via zlib
                        byte[] compressed = packetIn.readAllBytes();
                        byte[] decompressed = new byte[dataLength];
                        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
                        try {
                            inflater.setInput(compressed);
                            inflater.inflate(decompressed);
                        } catch (java.util.zip.DataFormatException e2) {
                            return new LoginAttempt(LoginResult.ERROR, "Zlib decompress failed: " + e2.getMessage());
                        } finally {
                            inflater.end();
                        }
                        packetIn = new DataInputStream(new ByteArrayInputStream(decompressed));
                    }
                    // else dataLength == 0 → uncompressed, packetIn already positioned at packet ID
                }

                int packetId = readVarInt(packetIn);

                switch (packetId) {
                    case 0x00 -> {
                        // Disconnect
                        String json = readString(packetIn);
                        return new LoginAttempt(LoginResult.DISCONNECTED, extractTextFromJsonChat(json) + " [raw=" + json + "]");
                    }
                    case 0x01 -> {
                        // Encryption Request — server is online-mode, shouldn't happen
                        return new LoginAttempt(LoginResult.ERROR, "Server sent Encryption Request (online-mode?)");
                    }
                    case 0x02 -> {
                        // Login Success
                        return new LoginAttempt(LoginResult.LOGIN_SUCCESS, "Login successful");
                    }
                    case 0x03 -> {
                        // Set Compression — read threshold and enable compression mode for subsequent packets
                        readVarInt(packetIn); // threshold, ignored
                        compressionEnabled = true;
                    }
                    case 0x04 -> {
                        // Login Plugin Request (used by Fabric for channel negotiation)
                        // Must respond with Login Plugin Response (0x02): same message ID, successful=false
                        int messageId = readVarInt(packetIn);
                        sendLoginPluginResponse(out, messageId, compressionEnabled);
                    }
                    default -> {
                        return new LoginAttempt(LoginResult.ERROR, "Unexpected packet ID: 0x" + Integer.toHexString(packetId));
                    }
                }
            }
        } catch (EOFException e) {
            // Server closed the connection without sending a proper Disconnect packet
            // (e.g. plugin crashed while encoding the kick message) — treat as DISCONNECTED
            return new LoginAttempt(LoginResult.DISCONNECTED, "Connection closed by server (EOF): " + e.getMessage());
        } catch (SocketTimeoutException e) {
            return new LoginAttempt(LoginResult.TIMEOUT, "Connection timed out");
        }
    }

    private void sendHandshake(DataOutputStream out, String serverHost, int serverPort) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(buf);

        writeVarInt(data, 0x00); // Packet ID: Handshake
        writeVarInt(data, protocolVersion);
        writeString(data, serverHost);
        data.writeShort(serverPort);
        writeVarInt(data, 2); // Next state: Login

        byte[] bytes = buf.toByteArray();
        writeVarInt(out, bytes.length);
        out.write(bytes);
        out.flush();
    }

    private void sendLoginStart(DataOutputStream out, String username, UUID uuid) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(buf);

        writeVarInt(data, 0x00); // Packet ID: LoginStart
        writeString(data, username);
        
        if (protocolVersion >= 764) {
            // 1.20.2+: always UUID
            data.writeLong(uuid.getMostSignificantBits());
            data.writeLong(uuid.getLeastSignificantBits());
        } else if (protocolVersion >= 761) {
            // 1.19.3 to 1.20.1: boolean has_uuid, then UUID if true
            data.writeBoolean(true);
            data.writeLong(uuid.getMostSignificantBits());
            data.writeLong(uuid.getLeastSignificantBits());
        } else if (protocolVersion >= 759) {
            // 1.19.0 to 1.19.2: boolean has_signature, then signature if true
            data.writeBoolean(false);
        }
        // <= 758 (1.18.2 and older): nothing else
        
        byte[] bytes = buf.toByteArray();
        writeVarInt(out, bytes.length);
        out.write(bytes);
        out.flush();
    }

    private void sendLoginPluginResponse(DataOutputStream out, int messageId, boolean compressionEnabled) throws IOException {
        ByteArrayOutputStream payloadBuf = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBuf);

        writeVarInt(payload, 0x02); // Packet ID: Login Plugin Response
        writeVarInt(payload, messageId);
        payload.writeBoolean(false); // not successful — we don't implement this channel

        byte[] payloadBytes = payloadBuf.toByteArray();

        if (compressionEnabled) {
            // Compressed packet format: [Data Length = 0 (uncompressed)] [payload]
            ByteArrayOutputStream compBuf = new ByteArrayOutputStream();
            DataOutputStream compOut = new DataOutputStream(compBuf);
            writeVarInt(compOut, 0); // Data Length = 0 means payload is not compressed
            compOut.write(payloadBytes);
            byte[] compBytes = compBuf.toByteArray();
            writeVarInt(out, compBytes.length);
            out.write(compBytes);
        } else {
            writeVarInt(out, payloadBytes.length);
            out.write(payloadBytes);
        }
        out.flush();
    }

    // VarInt decoding
    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        while (true) {
            int b = in.read();
            if (b == -1) throw new EOFException("Stream ended while reading VarInt");
            currentByte = (byte) b;
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new IOException("VarInt too big");
        }
        return value;
    }

    // VarInt encoding
    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    // String decoding (VarInt length prefix + UTF-8 bytes)
    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = in.readNBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // String encoding
    private static void writeString(DataOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /**
     * Extracts the text field from a Minecraft JSON chat component like {"text":"..."}.
     * Avoids pulling in a JSON library.
     */
    static String extractTextFromJsonChat(String json) {
        if (json == null || json.isBlank()) return json;
        // Try to find "text":"..." or "text": "..."
        int textIdx = json.indexOf("\"text\"");
        if (textIdx == -1) {
            // Could be a plain string like "Disconnected"
            if (json.startsWith("\"") && json.endsWith("\"")) {
                return json.substring(1, json.length() - 1);
            }
            return json;
        }
        int colonIdx = json.indexOf(':', textIdx + 6);
        if (colonIdx == -1) return json;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart == -1) return json;
        int quoteEnd = quoteStart + 1;
        while (quoteEnd < json.length()) {
            if (json.charAt(quoteEnd) == '"' && json.charAt(quoteEnd - 1) != '\\') break;
            quoteEnd++;
        }
        if (quoteEnd >= json.length()) return json;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
