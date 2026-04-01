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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


public class FabricServerTest extends BaseServerTest {

    static Stream<Arguments> fabricVersions() {
        // modulePath points to the built JAR used for each MC version.
        // The 1.21.1 JAR is intentionally reused for 1.18.2–1.21.1: Fabric's
        // cross-version compatibility allows one mod JAR to target a wide MC range.
        // 1.21.11 and 26.1 require separate builds due to breaking Fabric API changes.
        return Stream.of(
            Arguments.of("1.18.2",  758, "server/fabric/1.21.1"),
            Arguments.of("1.19.4",  762, "server/fabric/1.21.1"),
            Arguments.of("1.20.6",  766, "server/fabric/1.21.1"),
            Arguments.of("1.21.1",  767, "server/fabric/1.21.1"),
            Arguments.of("1.21.11", 774, "server/fabric/1.21.11"),
            Arguments.of("26.1",    775, "server/fabric/26.1")
        );
    }

    @ParameterizedTest(name = "Fabric {0}")
    @MethodSource("fabricVersions")
    public void testFabricServerLoadsModAndConnections(String mcVersion, int protocolVersion, String modulePath) throws Exception {
        serverDir = Util.setupServerDir("fabric-" + mcVersion);

        // 1. Download latest Fabric installer and loader version
        Path installerJar = serverDir.resolve("fabric-installer.jar");
        Util.downloadLatestFabricInstaller(installerJar);
        String loaderVersion = Util.fetchLatestFabricLoaderVersion();

        // 2. Run installer to setup server for the target MC version
        System.out.println("Running Fabric installer: loader=" + loaderVersion + " mcversion=" + mcVersion);
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", "fabric-installer.jar", "server",
                "-mcversion", mcVersion, "-loader", loaderVersion, "-downloadMinecraft"
        );
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        Process installerProcess = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(installerProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[INSTALLER] " + line);
            }
        }
        installerProcess.waitFor(10, TimeUnit.MINUTES);

        // 3. Setup mods directory and copy our mod (single JAR targets >=1.18)
        Path modsDir = serverDir.resolve("mods");
        Files.createDirectories(modsDir);
        Path ourMod = Util.getPluginJar(modulePath, "MinecraftAuth-Fabric");
        Files.copy(ourMod, modsDir.resolve("MinecraftAuth.jar"));

        // 4. Download latest Fabric API for this MC version from Modrinth
        Util.downloadLatestFabricAPI(mcVersion, modsDir.resolve("fabric-api.jar"));

        // 5. Pre-write mod config with bypass name and optional token
        String token = System.getenv("MINECRAFTAUTH_TOKEN");
        Util.writePluginConfig(
            serverDir.resolve("config/MinecraftAuth.yml"),
            buildPluginConfig(token)
        );

        int port = Util.findAvailablePort();
        writeOptimizedServerConfigs(serverDir, port);

        // 6. Start server and verify mod loads + connections work
        pb = new ProcessBuilder("java", "-Xmx1G", "-jar", "fabric-server-launch.jar", "nogui");
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();

        ServerStartResult result = waitForServerStart("[FABRIC " + mcVersion + "] ", 180_000, DONE_PATTERN, ENABLE_PATTERN, ERROR_PATTERN);

        assertPluginStarted(result, "FABRIC " + mcVersion);

        runConnectionTests(port, protocolVersion);
        gracefulShutdown("stop");
    }
}
