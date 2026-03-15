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

import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ComponentHelper on a 1.21.1 classpath where:
 * - Component.literal() DOES exist   → COMPONENT_LITERAL != null
 * - TextComponent(String) does NOT exist → TEXT_COMPONENT_CTOR == null
 */
public class ComponentHelperTest {

    @Test
    void componentLiteralFieldIsNotNull_on1211Classpath() throws Exception {
        Field f = ComponentHelper.class.getDeclaredField("COMPONENT_LITERAL");
        f.setAccessible(true);
        assertNotNull(f.get(null),
                "On 1.21.1 classpath Component.literal() should exist, so COMPONENT_LITERAL must not be null");
    }

    @Test
    void textComponentCtorFieldIsNull_on1211Classpath() throws Exception {
        Field f = ComponentHelper.class.getDeclaredField("TEXT_COMPONENT_CTOR");
        f.setAccessible(true);
        assertNull(f.get(null),
                "On 1.21.1 classpath TextComponent(String) should not exist, so TEXT_COMPONENT_CTOR must be null");
    }

    @Test
    void literalReturnsNonNull() {
        MutableComponent c = ComponentHelper.literal("hello");
        assertNotNull(c);
    }

    @Test
    void literalReturnsCorrectText() {
        MutableComponent c = ComponentHelper.literal("hello");
        assertEquals("hello", c.getString());
    }

    @Test
    void literalWithStyleDoesNotThrow() {
        assertDoesNotThrow(() ->
                ComponentHelper.literal("test").withStyle(net.minecraft.ChatFormatting.RED));
    }
}
