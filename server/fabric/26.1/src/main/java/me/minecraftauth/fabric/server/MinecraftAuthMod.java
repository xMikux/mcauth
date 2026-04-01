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

import github.scarsz.configuralize.DynamicConfig;
import github.scarsz.configuralize.ParseException;
import lombok.Getter;
import me.minecraftauth.plugin.common.abstracted.Log4jLogger;
import me.minecraftauth.plugin.common.service.AuthenticationService;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class MinecraftAuthMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "minecraftauth";
    @Getter private static final Logger logger = LogManager.getLogger();
    @Getter private static MinecraftAuthMod instance;

    @Getter private AuthenticationService service;

    @Override
    public void onInitializeServer() {
        MinecraftAuthMod.instance = this;

        DynamicConfig config = new DynamicConfig();
        try {
            config.addSource(MinecraftAuthMod.class, "game-config", new File("config", "MinecraftAuth.yml"));
            config.saveAllDefaults();
            config.loadAll();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
            return;
        }

        try {
            service = new AuthenticationService.Builder()
                    .withConfig(config)
                    .withLogger(new Log4jLogger(logger))
                    .build();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        registerCommands();
    }

    private static void registerCommands() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> new Command(dispatcher));
    }

}
