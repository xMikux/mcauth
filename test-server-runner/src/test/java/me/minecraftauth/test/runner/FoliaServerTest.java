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


public class FoliaServerTest extends BaseServerTest {

    private int port;

    @BeforeEach
    public void setup() throws Exception {
        serverDir = Util.setupServerDir("folia");

        // 1. Download latest Folia 1.21.11 server jar
        Path serverJar = serverDir.resolve("server.jar");
        Util.downloadLatestPaperMC("folia", "1.21.11", serverJar);

        // 2. Setup plugins directory and copy our plugin
        Path pluginsDir = serverDir.resolve("plugins");
        Files.createDirectory(pluginsDir);
        Path ourPlugin = Util.getPluginJar("server/bukkit", "MinecraftAuth-Bukkit");
        Files.copy(ourPlugin, pluginsDir.resolve("MinecraftAuth.jar"));

        // 3. Pre-write plugin config with bypass name and optional token
        String token = System.getenv("MINECRAFTAUTH_TOKEN");
        Util.writePluginConfig(
            serverDir.resolve("plugins/MinecraftAuth/config.yml"),
            buildPluginConfig(token)
        );

        port = Util.findAvailablePort();
        writeOptimizedServerConfigs(serverDir, port);
    }

    @Test
    public void testFoliaServerLoadsPluginAndConnections() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-Xmx1G", "-jar", "server.jar", "nogui"
        );
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();

        ServerStartResult result = waitForServerStart("[FOLIA] ", 120_000, DONE_PATTERN, ENABLE_PATTERN, ERROR_PATTERN);

        assertPluginStarted(result, "FOLIA");

        runConnectionTests(port, 774);
        gracefulShutdown("stop");
    }
}
