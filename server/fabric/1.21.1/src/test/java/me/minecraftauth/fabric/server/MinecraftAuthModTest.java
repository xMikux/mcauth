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

package me.minecraftauth.fabric.server;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class MinecraftAuthModTest {

    @Test
    public void testOnInitializeServer() throws Exception {
        // Prepare config directory
        Path configDir = Paths.get("config");
        if (!Files.exists(configDir)) {
            Files.createDirectory(configDir);
        }

        MinecraftAuthMod mod = new MinecraftAuthMod();
        // onInitializeServer should not throw exceptions even with missing config
        mod.onInitializeServer();

        assertNotNull(MinecraftAuthMod.getInstance(), "Mod instance should be set");
        assertNotNull(mod.getService(), "Authentication service should be initialized");
        
        // Cleanup config file if it was created
        Files.walk(configDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
