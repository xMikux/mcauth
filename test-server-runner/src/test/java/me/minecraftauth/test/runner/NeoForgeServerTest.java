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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;


public class NeoForgeServerTest extends BaseServerTest {

    private int port;

    @BeforeEach
    public void setup() throws Exception {
        serverDir = Util.setupServerDir("neoforge");

        // 1. Download latest NeoForge installer for 1.21.1
        Path installerJar = serverDir.resolve("neoforge-installer.jar");
        String neoforgeVersion = Util.downloadLatestNeoForgeInstaller("1.21.1", installerJar);
        System.out.println("Using NeoForge: " + neoforgeVersion);

        // 2. Run installer to setup server
        System.out.println("Running NeoForge installer...");
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", "neoforge-installer.jar", "--installServer"
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

        // 3. Setup mods directory and copy our mod
        Path modsDir = serverDir.resolve("mods");
        Files.createDirectories(modsDir);
        Path ourMod = Util.getPluginJar("server/neoforge/1.21.1", "MinecraftAuth-NeoForge");
        Files.copy(ourMod, modsDir.resolve("MinecraftAuth.jar"));

        // 4. Pre-write mod config with bypass name and optional token
        String token = System.getenv("MINECRAFTAUTH_TOKEN");
        Util.writePluginConfig(
            serverDir.resolve("config/MinecraftAuth.yml"),
            buildPluginConfig(token)
        );

        port = Util.findAvailablePort();
        writeOptimizedServerConfigs(serverDir, port);
    }

    @Test
    public void testNeoForgeServerLoadsModAndConnections() throws Exception {
        // NeoForge generates a run.sh for linux/mac environments
        ProcessBuilder pb = new ProcessBuilder("sh", "run.sh");
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();

        // 1.21.1 = protocol 767
        ServerStartResult result = waitForServerStart("[NEOFORGE] ", 240_000, DONE_PATTERN, ENABLE_PATTERN, ERROR_PATTERN);

        assertPluginStarted(result, "NEOFORGE");

        runConnectionTests(port, 767);
        gracefulShutdown("stop");
    }
}
