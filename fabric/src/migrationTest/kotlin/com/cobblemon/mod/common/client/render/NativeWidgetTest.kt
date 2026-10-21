/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.api.gui.ParentWidget
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class NativeWidgetTest {
    private class Row : ScrollingWidget.Slot<Row>() {
        override fun getNarration(): Component = Component.literal("row")
        override fun extractRenderState(context: GuiGraphicsExtractor, index: Int, y: Int, x: Int,
            entryWidth: Int, entryHeight: Int, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {}
    }
    private class Rows : ScrollingWidget<Row>(top = 0, left = 10, width = 100, height = 40, slotHeight = 20,
        minecraft = mock(Minecraft::class.java)) {
        fun append() = Row().also { addEntry(it) }
        fun clear() = clearEntries()
    }

    @Test fun `native hit testing uses unpadded row bounds before any rendering`() {
        val list = Rows()
        val first = list.append()
        val second = list.append()
        assertEquals(40, first.y)
        assertEquals(60, second.y)
        assertSame(first, list.getChildAt(11.0, 40.0).orElseThrow())
        assertSame(second, list.getChildAt(11.0, 60.0).orElseThrow())
        assertTrue(list.getChildAt(11.0, 39.0).isEmpty)
        assertTrue(list.getChildAt(11.0, 80.0).isEmpty)
    }

    @Test fun `scrolling clamps and updates hit rectangles without exposing clipped rows`() {
        val list = Rows()
        list.append()
        val middle = list.append()
        val last = list.append()
        list.setScrollAmount(1000.0)
        assertEquals(20.0, list.scrollAmount())
        assertSame(middle, list.getChildAt(11.0, 40.0).orElseThrow())
        assertSame(last, list.getChildAt(11.0, 60.0).orElseThrow())
        assertTrue(list.getChildAt(11.0, 30.0).isEmpty)
        assertFalse(list.mouseScrolled(200.0, 50.0, 0.0, 1.0))
        assertEquals(20.0, list.scrollAmount())
        list.clear()
        assertEquals(0.0, list.scrollAmount())
        assertEquals(0, list.maxScrollAmount())
        assertSame(list.append(), list.getChildAt(11.0, 40.0).orElseThrow())
    }

    @Test fun `parent passes double clicks and supplementary Unicode codepoints unchanged`() {
        val parent = object : ParentWidget(0, 0, 100, 100, Component.empty()) {
            override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}
        }
        val click = MouseButtonEvent(12.0, 34.0, MouseButtonInfo(0, 1))
        val character = CharacterEvent(0x1F600)
        var seenClick: MouseButtonEvent? = null
        var seenCharacter: CharacterEvent? = null
        var seenDouble = false
        parent.children.add(object : GuiEventListener {
            override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
                seenClick = event; seenDouble = doubleClick; return true
            }
            override fun charTyped(event: CharacterEvent): Boolean { seenCharacter = event; return true }
            override fun setFocused(focused: Boolean) {}
            override fun isFocused(): Boolean = false
        })
        assertTrue(parent.mouseClicked(click, true))
        parent.charTyped(character)
        assertSame(click, seenClick)
        assertTrue(seenDouble)
        assertSame(character, seenCharacter)
        assertEquals("\uD83D\uDE00", seenCharacter!!.codepointAsString())
    }
}
