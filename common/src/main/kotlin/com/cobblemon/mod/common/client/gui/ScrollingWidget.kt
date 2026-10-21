/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import java.util.Optional

/** Cobblemon row artwork backed by native 26.2 entry bounds and input handling. */
abstract class ScrollingWidget<T : ScrollingWidget.Slot<T>>(
    top: Int = 0,
    val left: Int = 0,
    width: Int = 10,
    height: Int = 10,
    slotHeight: Int = 10,
    val scrollBarWidth: Int = 5,
    minecraft: Minecraft = Minecraft.getInstance()
) : ObjectSelectionList<T>(minecraft, width, height, top + height, slotHeight), CobblemonRenderable {
    // Existing callers pass top = visibleY - height. Retain that layout convention.
    init { x = left }

    private var hoveredRow: T? = null
    override fun getHovered(): T? = hoveredRow

    override fun getChildAt(x: Double, y: Double): Optional<GuiEventListener> =
        if (isMouseOver(x, y)) super.getChildAt(x, y) else Optional.empty()

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
        isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, horizontal, vertical)

    override fun clearEntries() {
        super.clearEntries()
        hoveredRow = null
        setScrollAmount(0.0)
    }

    val maxPosition: Int get() = contentHeight()
    val maxScroll: Int get() = maxScrollAmount()

    fun setLeft(left: Int) { x = left; layoutRows() }
    open fun getEntry(index: Int): T = children()[index]
    override fun getRowLeft(): Int = x
    override fun getRowWidth(): Int = width
    override fun scrollBarX(): Int = x + width - scrollBarWidth
    override fun scrollbarWidth(): Int = scrollBarWidth
    override fun contentHeight(): Int = children().sumOf { it.height }

    /** Native hit testing reads these same rectangles, including the unpadded first row. */
    private fun layoutRows() {
        var rowY = y - scrollAmount().toInt()
        children().forEachIndexed { index, entry ->
            entry.rowIndex = index
            entry.x = rowLeft
            entry.y = rowY
            entry.width = rowWidth
            rowY += entry.height
        }
    }

    override fun addEntry(entry: T, height: Int): Int {
        val index = super.addEntry(entry, height)
        layoutRows()
        return index
    }

    override fun setScrollAmount(scrollAmount: Double) {
        super.setScrollAmount(scrollAmount)
        layoutRows()
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        layoutRows()
        hoveredRow = if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) getEntryAtPosition(mouseX.toDouble(), mouseY.toDouble()) else null
        enableScissor(context)
        try {
            renderListItems(context, mouseX, mouseY, delta)
        } finally {
            context.disableScissor()
        }
        if (maxScrollAmount() > 0) renderScrollbar(context, mouseX, mouseY, delta)
    }

    open fun renderListItems(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        children().forEachIndexed { index, entry ->
            if (entry.y + entry.height >= y && entry.y <= bottom) {
                renderItem(context, mouseX, mouseY, delta, index, entry.x, entry.y, entry.width, entry.height)
            }
        }
    }

    open fun renderItem(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float,
                        index: Int, x: Int, y: Int, entryWidth: Int, entryHeight: Int) {
        val entry = getEntry(index)
        entry.extractContent(context, mouseX, mouseY,
            isMouseOver(mouseX.toDouble(), mouseY.toDouble()) && entry.isMouseOver(mouseX.toDouble(), mouseY.toDouble()), delta)
    }

    open fun renderScrollbar(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val barHeight = height.coerceAtLeast(1)
        val thumbHeight = ((barHeight.toDouble() * barHeight / maxPosition.coerceAtLeast(1)).toInt())
            .coerceIn(minOf(32, barHeight), barHeight)
        val thumbY = y + (scrollAmount() * (barHeight - thumbHeight) / maxScrollAmount().coerceAtLeast(1)).toInt()
        val barX = scrollBarX()
        context.fill(barX, y, barX + scrollBarWidth, bottom, 0xFF000000.toInt())
        context.fill(barX, thumbY, barX + scrollBarWidth, thumbY + thumbHeight, 0xFF808080.toInt())
        context.fill(barX, thumbY, barX + scrollBarWidth - 1, thumbY + thumbHeight - 1, 0xFFC0C0C0.toInt())
    }

    abstract class Slot<T : Slot<T>> : ObjectSelectionList.Entry<T>() {
        internal var rowIndex: Int = 0

        final override fun extractContent(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
            extractRenderState(context, rowIndex, y, x, width, height, mouseX, mouseY, hovered, delta)
        }

        abstract fun extractRenderState(context: GuiGraphicsExtractor, index: Int, y: Int, x: Int,
                                        entryWidth: Int, entryHeight: Int, mouseX: Int, mouseY: Int,
                                        hovered: Boolean, tickDelta: Float)
    }
}
