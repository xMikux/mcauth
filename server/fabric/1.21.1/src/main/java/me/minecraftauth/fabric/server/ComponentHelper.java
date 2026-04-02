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

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Cross-version helper for creating text Components.
 * <ul>
 *   <li>1.19+  – {@code Component.literal(String)}</li>
 *   <li>1.18.x – {@code new TextComponent(String)}</li>
 * </ul>
 */
public final class ComponentHelper {

    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger(ComponentHelper.class);

    private static final Method COMPONENT_LITERAL;
    private static final Constructor<?> TEXT_COMPONENT_CTOR;

    static {
        Method literal = null;
        Constructor<?> ctor = null;
        try {
            literal = Component.class.getMethod("literal", String.class);
        } catch (NoSuchMethodException ignored) {
            try {
                ctor = Class.forName("net.minecraft.network.chat.TextComponent")
                        .getConstructor(String.class);
            } catch (Exception ex) {
                LOGGER.error("Failed to resolve Component.literal or TextComponent constructor", ex);
            }
        }
        COMPONENT_LITERAL = literal;
        TEXT_COMPONENT_CTOR = ctor;
    }

    private ComponentHelper() {}

    public static MutableComponent literal(String text) {
        try {
            if (COMPONENT_LITERAL != null) {
                return (MutableComponent) COMPONENT_LITERAL.invoke(null, text);
            } else if (TEXT_COMPONENT_CTOR != null) {
                return (MutableComponent) TEXT_COMPONENT_CTOR.newInstance(text);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to create text Component", ex);
        }
        // Last-resort fallback — should never be reached on supported versions
        return Component.literal(text);
    }
}
