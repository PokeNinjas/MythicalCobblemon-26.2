/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.gui.pc.IconButton
import com.cobblemon.mod.common.client.gui.pc.PCGUI
import com.cobblemon.mod.common.client.gui.pc.StorageWidget
import com.cobblemon.mod.common.client.gui.pc.WallpapersScrollingWidget
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PCWallpaperInputTest {
    // Skip renderer/client initialization; exercise the real PC screen and native
    // ContainerEventHandler routing with the same overlapping child order as init.
    private class Fixture {
        val screen = mock(PCGUI::class.java, CALLS_REAL_METHODS)
        val storage = mock(StorageWidget::class.java)
        val wallpaper = mock(WallpapersScrollingWidget::class.java)
        val button = mock(IconButton::class.java)

        init {
            field("storageWidget", storage)
            field("wallpaperWidget", wallpaper)
            field("optionButtons", mutableListOf(button))
            doReturn(listOf<GuiEventListener>(storage, wallpaper, button)).`when`(screen).children()
            doAnswer { call ->
                val x = call.getArgument<Double>(0); val y = call.getArgument<Double>(1)
                x >= 85 && x < 348 && y >= 27 && y < 182
            }.`when`(storage).isMouseOver(anyDouble(), anyDouble())
            doAnswer { call ->
                val x = call.getArgument<Double>(0); val y = call.getArgument<Double>(1)
                wallpaper.visible && x >= 274 && x < 342 && y >= 29 && y < 175
            }.`when`(wallpaper).isMouseOver(anyDouble(), anyDouble())
            doAnswer { call ->
                val x = call.getArgument<Double>(0); val y = call.getArgument<Double>(1)
                button.visible && x >= 242 && x < 252 && y >= 31 && y < 41
            }.`when`(button).isMouseOver(anyDouble(), anyDouble())
            button.visible = true
            wallpaper.visible = true
        }

        private fun field(name: String, value: Any) {
            PCGUI::class.java.getDeclaredField(name).apply { isAccessible = true }.set(screen, value)
        }
    }

    @Test fun `wallpaper settings and previews receive native clicks ahead of overlapping storage`() {
        val f = Fixture()
        val nativeRouting = object : AbstractContainerEventHandler() {
            override fun children(): List<GuiEventListener> = f.screen.children()
        }
        assertSame(f.storage, nativeRouting.getChildAt(245.0, 35.0).orElseThrow())
        assertSame(f.storage, nativeRouting.getChildAt(280.0, 40.0).orElseThrow())
        assertSame(f.button, f.screen.getChildAt(245.0, 35.0).orElseThrow())
        assertSame(f.wallpaper, f.screen.getChildAt(280.0, 40.0).orElseThrow())
        val buttonClick = MouseButtonEvent(245.0, 35.0, MouseButtonInfo(0, 0))
        val previewClick = MouseButtonEvent(280.0, 40.0, MouseButtonInfo(0, 1))
        f.screen.mouseClicked(buttonClick, false)
        f.screen.mouseClicked(previewClick, true)
        verify(f.button).mouseClicked(buttonClick, false)
        verify(f.wallpaper).mouseClicked(previewClick, true)
        verify(f.storage, never()).mouseClicked(buttonClick, false)
        verify(f.storage, never()).mouseClicked(previewClick, true)
    }

    @Test fun `hidden wallpaper controls leave party and box input with storage`() {
        val f = Fixture()
        f.wallpaper.visible = false
        f.button.visible = false
        assertSame(f.storage, f.screen.getChildAt(280.0, 40.0).orElseThrow())
        assertSame(f.storage, f.screen.getChildAt(245.0, 35.0).orElseThrow())
        assertSame(f.storage, f.screen.getChildAt(100.0, 80.0).orElseThrow())
        assertTrue(f.screen.getChildAt(500.0, 500.0).isEmpty)
    }

    @Test fun `wallpaper wheel event is delivered exactly once and outside panel does not scroll it`() {
        val f = Fixture()
        doReturn(true).`when`(f.wallpaper).mouseScrolled(280.0, 40.0, 0.0, -1.0)
        // This flag used to trigger an extra manual call before native dispatch.
        doReturn(true).`when`(f.wallpaper).isHovered()
        assertTrue(f.screen.mouseScrolled(280.0, 40.0, 0.0, -1.0))
        verify(f.wallpaper, times(1)).mouseScrolled(280.0, 40.0, 0.0, -1.0)
        f.screen.mouseScrolled(500.0, 500.0, 0.0, -1.0)
        verify(f.wallpaper, never()).mouseScrolled(500.0, 500.0, 0.0, -1.0)
    }
}
