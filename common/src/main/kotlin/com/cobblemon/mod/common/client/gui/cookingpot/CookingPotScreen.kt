/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendToServer
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.block.campfirepot.CampfirePotColor
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.campfirepot.SeasoningSlot
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.COOKING_POT_COLOR_INDEX
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.IS_LID_OPEN_INDEX
import com.cobblemon.mod.common.integration.jei.CobblemonJeiPlugin
import com.cobblemon.mod.common.integration.jei.cooking.CampfirePotRecipeCategory
import com.cobblemon.mod.common.net.messages.client.cooking.ToggleCookingPotLidPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenPosition
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import kotlin.math.ceil

class CookingPotScreen(menu: CookingPotMenu, playerInventory: Inventory, title: Component) :
    AbstractRecipeBookScreen<CookingPotMenu>(menu, CookingPotRecipeBookComponent(menu), playerInventory,
        Component.translatable("cobblemon.container.campfire_pot")) {
    companion object {
        private const val BACKGROUND_HEIGHT = 166
        private const val BACKGROUND_WIDTH = 176
        const val COOK_PROGRESS_HEIGHT = 12
        const val COOK_PROGRESS_WIDTH = 22
        private val BACKGROUND = cobblemonResource("textures/gui/campfirepot/campfire_pot.png")
        val COOK_PROGRESS_SPRITE = cobblemonResource("textures/gui/campfirepot/cook_progress.png")
    }

    private lateinit var cookButton: CookButton
    private fun potColor() = CampfirePotColor.entries[
        menu.containerData.get(COOKING_POT_COLOR_INDEX).coerceIn(0, CampfirePotColor.entries.lastIndex)]

    override fun init() {
        super.init()
        titleLabelX = imageWidth / 2 - font.width(title) / 2
        titleLabelY = 6
        cookButton = addRenderableWidget(CookButton(leftPos + 97, topPos + 56,
            menu.containerData.get(IS_LID_OPEN_INDEX) == 0, potColor()) {
            sendToServer(ToggleCookingPotLidPacket(menu.containerData.get(IS_LID_OPEN_INDEX) == 0))
        })
    }

    override fun getRecipeBookButtonPosition() = ScreenPosition(leftPos + 5, topPos + 35)
    override fun onRecipeBookButtonClick() {
        if (::cookButton.isInitialized) cookButton.setPosition(leftPos + 97, topPos + 56)
    }

    override fun extractLabels(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        context.text(font, title, titleLabelX, titleLabelY, 0xFF404040.toInt(), false)
    }

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(context, mouseX, mouseY, delta)
        blitk(context = context, texture = BACKGROUND, x = leftPos, y = topPos,
            width = BACKGROUND_WIDTH, height = BACKGROUND_HEIGHT)
        val progress = ceil(menu.getBurnProgress() * COOK_PROGRESS_WIDTH).toInt()
        if (progress > 0) blitk(context = context, texture = COOK_PROGRESS_SPRITE,
            x = leftPos + 96, y = topPos + 39, width = progress,
            height = COOK_PROGRESS_HEIGHT, textureWidth = COOK_PROGRESS_WIDTH)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        cookButton.color = potColor()
        cookButton.selected = menu.containerData.get(IS_LID_OPEN_INDEX) == 0
        cookButton.setPosition(leftPos + 97, topPos + 56)
        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    override fun extractSlot(context: GuiGraphicsExtractor, slot: Slot, mouseX: Int, mouseY: Int) {
        if (slot is SeasoningSlot && ((!menu.carried.isEmpty && !slot.mayPlace(menu.carried)) ||
            (slot.hasItem() && !slot.mayPlace(slot.item)))) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 822018048)
        }
        super.extractSlot(context, slot, mouseX, mouseY)
        if (slot == menu.slots[menu.resultSlotIndex] && !slot.hasItem() && !menu.previewItem.isEmpty) {
            // Native ghost-slot presentation keeps item submission immutable and avoids global shader color.
            context.fakeItem(menu.previewItem, slot.x, slot.y)
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 822083583)
            context.itemDecorations(font, menu.previewItem, slot.x, slot.y)
        }
    }

    private fun overProgress(mouseX: Double, mouseY: Double) =
        isHovering(96, 39, COOK_PROGRESS_WIDTH, COOK_PROGRESS_HEIGHT, mouseX, mouseY)

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (Cobblemon.implementation.isModInstalled("jei") && overProgress(event.x(), event.y())) {
            CobblemonJeiPlugin.jeiRuntime?.recipesGui?.showTypes(listOf(CampfirePotRecipeCategory.RECIPE_TYPE))
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun extractTooltip(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (Cobblemon.implementation.isModInstalled("jei") && overProgress(mouseX.toDouble(), mouseY.toDouble())) {
            context.setTooltipForNextFrame(font, Component.translatable("jei.tooltip.show.recipes"), mouseX, mouseY)
            return
        }
        super.extractTooltip(context, mouseX, mouseY)
        val result = menu.slots[menu.resultSlotIndex]
        if (!result.hasItem() && !menu.previewItem.isEmpty && menu.carried.isEmpty &&
            isHovering(result.x, result.y, 16, 16, mouseX.toDouble(), mouseY.toDouble())) {
            context.setTooltipForNextFrame(font, menu.previewItem, mouseX, mouseY)
        }
    }
}
