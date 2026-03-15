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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;


public class VelocityServerTest extends BaseServerTest {

    private int velocityPort;

    @BeforeEach
    public void setup() throws Exception {
        serverDir = Util.setupServerDir("velocity");

        // 1. Download latest Velocity server jar
        Path serverJar = serverDir.resolve("server.jar");
        Util.downloadLatestPaperMC("velocity", "3.4.0-SNAPSHOT", serverJar);

        // 2. Setup plugins directory and copy our plugin
        Path pluginsDir = serverDir.resolve("plugins");
        Files.createDirectory(pluginsDir);
        Path ourPlugin = Util.getPluginJar("proxy/velocity", "MinecraftAuth-Velocity");
        Files.copy(ourPlugin, pluginsDir.resolve("MinecraftAuth.jar"));

        // 3. Pre-write plugin config with bypass name and optional token
        // Velocity plugin id is "minecraftauth" — data directory is plugins/minecraftauth/
        String token = System.getenv("MINECRAFTAUTH_TOKEN");
        Util.writePluginConfig(
            serverDir.resolve("plugins/minecraftauth/MinecraftAuth.yml"),
            buildPluginConfig(token)
        );

        // 4. Write minimal velocity.toml (offline-mode, no backend servers needed)
        velocityPort = Util.findAvailablePort();
        int dummyLobbyPort = Util.findAvailablePort();
        Files.writeString(serverDir.resolve("velocity.toml"), buildVelocityToml(velocityPort, dummyLobbyPort));

        // 5. Create forwarding.secret file (Velocity 3.x requires this file to exist even in none-mode)
        Files.writeString(serverDir.resolve("forwarding.secret"), "placeholder");
    }

    private static String buildVelocityToml(int port, int lobbyPort) {
        return "config-version = \"2.7\"\n"
             + "bind = \"0.0.0.0:" + port + "\"\n"
             + "motd = \"Velocity Test Server\"\n"
             + "show-max-players = 500\n"
             + "online-mode = false\n"
             + "force-key-authentication = false\n"
             + "announce-forge = false\n"
             + "kick-existing-players = false\n"
             + "player-info-forwarding-mode = \"none\"\n"
             + "prevent-client-proxy-connections = false\n"
             + "try = [\"lobby\"]\n"
             + "\n"
             + "[servers]\n"
             + "lobby = \"127.0.0.1:" + lobbyPort + "\"\n"
             + "\n"
             + "[forced-hosts]\n"
             + "\n"
             + "[advanced]\n"
             + "compression-threshold = 256\n"
             + "compression-level = -1\n"
             + "login-ratelimit = 3000\n"
             + "connection-timeout = 5000\n"
             + "read-timeout = 30000\n"
             + "haproxy-protocol = false\n"
             + "tcp-fast-open = false\n"
             + "bungee-plugin-message-channel = true\n"
             + "show-ping-requests = false\n"
             + "announce-proxy-commands = true\n"
             + "log-command-executions = false\n"
             + "log-player-connections = true\n"
             + "accepts-transfers = false\n"
             + "\n"
             + "[query]\n"
             + "enabled = false\n"
             + "port = " + port + "\n"
             + "map = \"Velocity\"\n"
             + "show-plugins = false\n";
    }

    @Test
    public void testVelocityServerLoadsPluginAndConnections() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-Xmx1G", "-jar", "server.jar"
        );
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();

        ServerStartResult result = waitForServerStart("[VELOCITY] ", 60_000, DONE_PATTERN, ENABLE_PATTERN, ERROR_PATTERN);

        assertPluginStarted(result, "VELOCITY");

        // Velocity 3.4.0-SNAPSHOT latest build supports 1.21.11 (protocol 774)
        runConnectionTests(velocityPort, 774);
        gracefulShutdown("end");
    }
}
