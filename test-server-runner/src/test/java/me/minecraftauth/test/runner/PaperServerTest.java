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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;


public class PaperServerTest extends BaseServerTest {

    static Stream<Arguments> paperVersions() {
        return Stream.of(
            Arguments.of("1.18.2", 758),
            Arguments.of("1.19.4", 762),
            Arguments.of("1.20.6", 766),
            Arguments.of("1.21.11", 774)
        );
    }

    @ParameterizedTest(name = "Paper {0}")
    @MethodSource("paperVersions")
    public void testPaperServerLoadsPluginAndConnections(String version, int protocolVersion) throws Exception {
        serverDir = Util.setupServerDir("paper-" + version);

        Path serverJar = serverDir.resolve("server.jar");
        Util.downloadLatestPaperMC("paper", version, serverJar);

        Path pluginsDir = serverDir.resolve("plugins");
        Files.createDirectory(pluginsDir);
        Path ourPlugin = Util.getPluginJar("server/bukkit", "MinecraftAuth-Bukkit");
        Files.copy(ourPlugin, pluginsDir.resolve("MinecraftAuth.jar"));

        String token = System.getenv("MINECRAFTAUTH_TOKEN");
        Util.writePluginConfig(
            serverDir.resolve("plugins/MinecraftAuth/config.yml"),
            buildPluginConfig(token)
        );

        int port = Util.findAvailablePort();
        writeOptimizedServerConfigs(serverDir, port);

        ProcessBuilder pb = new ProcessBuilder(
                "java", "-Xmx1G", "-jar", "server.jar", "nogui"
        );
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();

        ServerStartResult result = waitForServerStart("[PAPER " + version + "] ", 180_000, DONE_PATTERN, ENABLE_PATTERN, ERROR_PATTERN);

        assertPluginStarted(result, "PAPER " + version);

        runConnectionTests(port, protocolVersion);
        gracefulShutdown("stop");
    }
}
