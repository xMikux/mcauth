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

package me.minecraftauth.fabric.server.mixin;

import me.minecraftauth.fabric.server.MinecraftAuthMod;
import me.minecraftauth.lib.exception.LookupException;
import me.minecraftauth.plugin.common.abstracted.event.RealmJoinEvent;
import me.minecraftauth.fabric.server.ComponentHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public abstract class LoginMixin {

    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger(LoginMixin.class);

    @Inject(at = @At("RETURN"), method = "canPlayerLogin", cancellable = true)
    private void init(SocketAddress address, NameAndId profile, CallbackInfoReturnable<Component> returnedMessage) {
        if (returnedMessage.getReturnValue() == null) {
            try {
                MinecraftAuthMod.getInstance().getService().handleRealmJoinEvent(new RealmJoinEvent(
                        profile.id(),
                        profile.name(),
                        ((PlayerList)(Object)this).isOp(profile),
                        null
                ) {
                    @Override
                    public void disallow(String message) {
                        returnedMessage.setReturnValue(errorComponent(message));
                    }
                });
            } catch (LookupException e) {
                returnedMessage.setReturnValue(errorComponent("Unable to verify linked account"));
                LOGGER.error("Failed to verify linked account during login", e);
            }
        }
    }

    private MutableComponent errorComponent(String message) {
        return ComponentHelper.literal(message).withStyle(ChatFormatting.RED);
    }

}
