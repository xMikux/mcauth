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

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class BaseServerTest {

    protected static final String BYPASS_USERNAME  = "BypassPlayer";
    protected static final String REJECTED_USERNAME = "RejectedPlayer";

    protected static final Pattern DONE_PATTERN = Pattern.compile("(?i).*Done \\(.*\\)!.*");
    protected static final Pattern ENABLE_PATTERN = Pattern.compile("(?i).*\\[MinecraftAuth\\].*(ready|Enabling).*|.*Minecraft Authentication service ready.*|.*\\[minecraftauth\\].*");
    protected static final Pattern ERROR_PATTERN = Pattern.compile("(?i)(?!.*No key layers in MapLike)(.*Exception.*|.*Error.*|.*FAILED.*|.*StackTrace.*|.*ClassNotFoundException.*)");

    protected Path serverDir;
    protected Process process;

    @AfterEach
    public void teardown() {
        if (process != null) {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            try { process.waitFor(10, TimeUnit.SECONDS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            FileUtils.deleteDirectory(serverDir.toFile());
        } catch (Exception e) {
            System.err.println("Failed to delete temp dir: " + e.getMessage());
        }
    }

    protected void gracefulShutdown(String command) throws Exception {
        if (process.isAlive()) {
            try {
                process.getOutputStream().write((command + "\n").getBytes());
                process.getOutputStream().flush();
            } catch (IOException ignored) {
                // Server may have already stopped (e.g. crash after test assertions passed)
            }
            process.waitFor(30, TimeUnit.SECONDS);
        }
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
    }

    protected ServerStartResult waitForServerStart(
            String logPrefix, long timeoutMs,
            Pattern donePattern, Pattern enablePattern, Pattern errorPattern
    ) throws InterruptedException {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicBoolean error   = new AtomicBoolean(false);
        CountDownLatch doneLatch = new CountDownLatch(1);

        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean doneSignaled = false;
                while ((line = reader.readLine()) != null) {
                    System.out.println(logPrefix + line);
                    if (enablePattern.matcher(line).matches()) enabled.set(true);
                    if (errorPattern.matcher(line).matches())  error.set(true);
                    if (!doneSignaled && donePattern.matcher(line).matches()) {
                        started.set(true);
                        doneSignaled = true;
                        doneLatch.countDown();
                    }
                }
            } catch (IOException ignored) {}
            doneLatch.countDown();
        });
        logThread.setDaemon(true);
        logThread.start();

        doneLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        return new ServerStartResult(started.get(), enabled.get(), error.get());
    }

    public static class ServerStartResult {
        public final boolean started;
        public final boolean pluginEnabled;
        public final boolean errorEncountered;

        ServerStartResult(boolean started, boolean pluginEnabled, boolean errorEncountered) {
            this.started          = started;
            this.pluginEnabled    = pluginEnabled;
            this.errorEncountered = errorEncountered;
        }
    }

    protected static String buildPluginConfig(String token) {
        return "Authentication: " + (token != null ? token : "test-token-bypass-only") + "\n"
             + "Gatekeeper:\n"
             + "  Admin bypass: false\n"
             + "  Bypass:\n"
             + "    - " + BYPASS_USERNAME + "\n"
             + "  Kick message: \"Not authorized to join\"\n"
             + "  Conditions:\n"
             + "    - TwitchFollower()\n";
    }

    protected void assertPluginStarted(ServerStartResult result, String platform) {
        assertTrue(result.started, "Server did not finish starting within timeout.");
        assertTrue(result.pluginEnabled, "Plugin initialization message not found in logs.");

        String token = System.getenv("MINECRAFTAUTH_TOKEN");
        if (token == null || token.isBlank()) {
            if (result.errorEncountered) {
                System.out.println("[" + platform + "] MINECRAFTAUTH_TOKEN not set — token-related errors in log are expected (running bypass-only test).");
            }
        } else {
            assertFalse(result.errorEncountered, "An error occurred while enabling the plugin.");
        }
    }

    protected void runConnectionTests(int port, int protocolVersion) throws Exception {
        assertTrue(Util.waitForPort("localhost", port, 10_000),
            "Server port " + port + " not open after Done!");

        UUID bypassUUID = Util.computeOfflineUUID(BYPASS_USERNAME);
        try (MinecraftLoginClient client = new MinecraftLoginClient("localhost", port, protocolVersion, 10_000)) {
            MinecraftLoginClient.LoginAttempt r = client.attemptLogin(BYPASS_USERNAME, bypassUUID);
            assertEquals(MinecraftLoginClient.LoginResult.LOGIN_SUCCESS, r.result(),
                "Bypassed player should be allowed: " + r.message());
        }

        String token = System.getenv("MINECRAFTAUTH_TOKEN");
        Assumptions.assumeTrue(token != null && !token.isBlank(),
            "MINECRAFTAUTH_TOKEN not set; skipping rejection test");

        UUID rejectedUUID = Util.computeOfflineUUID(REJECTED_USERNAME);
        try (MinecraftLoginClient client = new MinecraftLoginClient("localhost", port, protocolVersion, 10_000)) {
            MinecraftLoginClient.LoginAttempt r = client.attemptLogin(REJECTED_USERNAME, rejectedUUID);
            assertEquals(MinecraftLoginClient.LoginResult.DISCONNECTED, r.result(),
                "Unauthorized player should be kicked: " + r.message());
        }
    }

    protected void writeOptimizedServerConfigs(Path serverDir, int port) throws IOException {
        Files.writeString(serverDir.resolve("server.properties"),
            "online-mode=false\n" +
            "server-port=" + port + "\n" +
            "allow-nether=false\n" +
            "level-type=FLAT\n" +
            "generate-structures=false\n" +
            "max-tick-time=-1\n" +
            "view-distance=4\n" +
            "spawn-protection=0\n");

        Files.writeString(serverDir.resolve("bukkit.yml"),
            "settings:\n  allow-end: false\n");
    }
}
