/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.gui.FilteredEditBox
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.anyInt

class FilteredEditBoxTest {
    private fun field(): FilteredEditBox {
        val font = mock(Font::class.java)
        `when`(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer { it.getArgument<String>(0) }
        `when`(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer { it.getArgument<String>(0) }
        return FilteredEditBox(font, 0, 0, 100, 20, Component.empty())
    }

    @Test fun `rejected insert paste and replacement leave value cursor and callbacks unchanged`() {
        val field = field()
        field.setFilter { it.toIntOrNull() != null || it.isBlank() || it == "-" }
        field.value = "12"
        val changes = mutableListOf<String>()
        field.setResponder { changes.add(it) }
        field.insertText("x")
        field.value = "invalid"
        assertEquals("12", field.value)
        assertEquals(2, field.cursorPosition)
        assertTrue(changes.isEmpty())
        field.setHighlightPos(0)
        field.insertText("34")
        assertEquals("34", field.value)
        assertEquals(listOf("34"), changes)
    }

    @Test fun `deletion obeys filters while allowing numeric intermediate states`() {
        val field = field()
        field.setFilter { it.toIntOrNull() != null || it.isBlank() || it == "-" }
        field.value = "-1"
        field.deleteCharsToPos(1)
        assertEquals("-", field.value)
        field.deleteCharsToPos(0)
        assertEquals("", field.value)
        field.value = "12"
        field.setFilter { it.isNotEmpty() }
        field.setHighlightPos(0)
        field.deleteCharsToPos(0)
        assertEquals("12", field.value)
    }

    @Test fun `paste validation uses the native length limit and preserves Unicode pairs`() {
        val field = field()
        field.setMaxLength(2)
        field.value = "1"
        field.insertText("\uD83D\uDE00")
        assertEquals("1", field.value)
        field.setHighlightPos(0)
        field.insertText("\uD83D\uDE00")
        assertEquals("\uD83D\uDE00", field.value)
        assertEquals(2, field.cursorPosition)
    }
}
