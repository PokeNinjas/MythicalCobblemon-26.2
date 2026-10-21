/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.util.StringUtil

/** Restores prospective value validation removed from vanilla's EditBox in 26.2. */
class FilteredEditBox(font: Font, x: Int, y: Int, width: Int, height: Int, narration: Component) :
    EditBox(font, x, y, width, height, narration) {
    private var valueFilter: (String) -> Boolean = { true }
    private var selectionAnchor = 0
    private var allowedLength = 32

    fun setFilter(filter: (String) -> Boolean) { valueFilter = filter }

    override fun setMaxLength(length: Int) {
        allowedLength = length
        super.setMaxLength(length)
        selectionAnchor = selectionAnchor.coerceIn(0, value.length)
    }

    override fun setHighlightPos(position: Int) {
        super.setHighlightPos(position)
        selectionAnchor = position.coerceIn(0, value.length)
    }

    override fun setValue(value: String) {
        if (valueFilter(value)) super.setValue(value)
    }

    override fun insertText(input: String) {
        val start = minOf(cursorPosition, selectionAnchor)
        val end = maxOf(cursorPosition, selectionAnchor)
        var capacity = allowedLength - value.length + end - start
        if (capacity <= 0) return
        var text = StringUtil.filterText(input)
        if (text.length > capacity) {
            if (text[capacity - 1].isHighSurrogate()) capacity--
            text = text.substring(0, capacity)
        }
        if (valueFilter(value.replaceRange(start, end, text))) super.insertText(input)
    }

    override fun deleteCharsToPos(position: Int) {
        if (selectionAnchor != cursorPosition) {
            // Vanilla dispatches selected deletion through insertText, retaining validation.
            super.deleteCharsToPos(position)
        } else {
            val target = position.coerceIn(0, value.length)
            val candidate = value.removeRange(minOf(target, cursorPosition), maxOf(target, cursorPosition))
            if (valueFilter(candidate)) super.deleteCharsToPos(target)
        }
    }
}
