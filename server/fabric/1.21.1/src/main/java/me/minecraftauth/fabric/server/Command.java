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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import github.scarsz.configuralize.ParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class Command {

    // Cache the sendSuccess Method — Supplier<Component> overload (1.20.1+) or Component overload (pre-1.20.1)
    private static final java.lang.reflect.Method SEND_SUCCESS_METHOD;
    private static final boolean MODERN_SEND_SUCCESS;
    static {
        java.lang.reflect.Method method = null;
        boolean modern = false;
        try {
            method = CommandSourceStack.class.getMethod("sendSuccess", java.util.function.Supplier.class, boolean.class);
            modern = true;
        } catch (NoSuchMethodException ignored) {
            try {
                method = CommandSourceStack.class.getMethod("sendSuccess", Component.class, boolean.class);
            } catch (NoSuchMethodException ignored2) {}
        }
        SEND_SUCCESS_METHOD = method;
        MODERN_SEND_SUCCESS = modern;
    }

    private static void sendSuccess(CommandSourceStack source, Component msg, boolean broadcast) {
        if (SEND_SUCCESS_METHOD == null) return;
        try {
            if (MODERN_SEND_SUCCESS) {
                SEND_SUCCESS_METHOD.invoke(source, (java.util.function.Supplier<Component>) () -> msg, broadcast);
            } else {
                SEND_SUCCESS_METHOD.invoke(source, msg, broadcast);
            }
        } catch (Exception ignored) {}
    }

    public Command(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minecraftauth")
                .then(Commands.literal("reload")
                        .requires(cs -> cs.hasPermission(3))
                        .executes(context -> reload(context, context.getSource()))
                )
        );
    }

    private int reload(CommandContext<CommandSourceStack> context, CommandSourceStack source) {
        try {
            MinecraftAuthMod.getInstance().getService().fullReload();
            sendSuccess(source, ComponentHelper.literal("MinecraftAuth config reloaded").withStyle(ChatFormatting.RED), true);
            return 1;
        } catch (IOException e) {
            source.sendFailure(ComponentHelper.literal("IO exception while reading config: " + e.getMessage()).withStyle(ChatFormatting.RED));
            e.printStackTrace();
        } catch (ParseException e) {
            source.sendFailure(ComponentHelper.literal("Exception while parsing config: " + e.getMessage()).withStyle(ChatFormatting.RED));
            e.printStackTrace();
        }
        return -1;
    }

}
