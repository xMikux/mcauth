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

package me.minecraftauth.forge.server;

import github.scarsz.configuralize.DynamicConfig;
import github.scarsz.configuralize.ParseException;
import lombok.Getter;
import me.minecraftauth.plugin.common.abstracted.Log4jLogger;
import me.minecraftauth.plugin.common.service.AuthenticationService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

@Mod(MinecraftAuthMod.MOD_ID)
public class MinecraftAuthMod {

    public static final String MOD_ID = "minecraftauth";
    @Getter private static final Logger logger = LogManager.getLogger();
    @Getter private static MinecraftAuthMod instance;

    @Getter private AuthenticationService service;

    public MinecraftAuthMod() {
        MinecraftAuthMod.instance = this;
        MinecraftForge.EVENT_BUS.register(this);

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
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        new Command(event.getDispatcher());
    }

}
