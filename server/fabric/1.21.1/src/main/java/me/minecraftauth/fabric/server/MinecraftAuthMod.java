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

    /**
     * Registers the /minecraftauth command using whichever Fabric API is present at runtime.
     * <ul>
     *   <li>command.v2 – Fabric API 1.19+ (CommandRegistrationCallback takes 3 args)</li>
     *   <li>command.v1 – Fabric API 1.18.x (CommandRegistrationCallback takes 2 args)</li>
     * </ul>
     * The v1 reference is isolated in {@link #registerCommandsV1()} so the JVM only resolves
     * that class if the v2 path is unavailable, avoiding NoClassDefFoundError on newer servers.
     */
    private static void registerCommands() {
        // Try Fabric API command.v2 first (1.19+)
        try {
            Class<?> cbClass = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object event = cbClass.getField("EVENT").get(null);
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    MinecraftAuthMod.class.getClassLoader(),
                    new Class[]{cbClass},
                    (proxy, method, args) -> {
                        if ("register".equals(method.getName())) {
                            @SuppressWarnings("unchecked")
                            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher =
                                    (com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>) args[0];
                            new Command(dispatcher);
                        }
                        return null;
                    }
            );
            // Use the public Event API class for lookup to avoid IllegalAccessException
            // on the internal ArrayBackedEvent implementation class
            Class<?> eventApiClass = Class.forName("net.fabricmc.fabric.api.event.Event");
            eventApiClass.getMethod("register", Object.class).invoke(event, listener);
            return;
        } catch (ClassNotFoundException ignored) {
            // Fall through to v1
        } catch (Exception e) {
            logger.error("Failed to register commands via Fabric API v2", e);
            return;
        }

        // Fall back to Fabric API command.v1 (1.18.x)
        registerCommandsV1();
    }

    /** Isolated so the v1 class reference is only resolved when this method is actually called. */
    private static void registerCommandsV1() {
        net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback.EVENT
                .register((dispatcher, dedicated) -> new Command(dispatcher));
    }

}
