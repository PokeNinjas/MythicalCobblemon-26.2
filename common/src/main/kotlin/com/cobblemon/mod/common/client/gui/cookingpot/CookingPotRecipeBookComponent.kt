/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonRecipeCategories
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.screens.recipebook.GhostSlots
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.client.gui.screens.recipebook.RecipeCollection
import net.minecraft.network.chat.Component
import net.minecraft.recipebook.PlaceRecipeHelper
import net.minecraft.resources.Identifier
import net.minecraft.util.context.ContextMap
import net.minecraft.world.entity.player.StackedItemContents
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay
import java.util.Optional

class CookingPotRecipeBookComponent(menu: CookingPotMenu) : RecipeBookComponent<CookingPotMenu>(menu, listOf(
    TabInfo(ItemStack(Items.COMPASS), Optional.empty(), CobblemonRecipeCategories.COOKING_POT_SEARCH),
    TabInfo(CobblemonItems.LEEK_AND_POTATO_STEW, CobblemonRecipeCategories.COOKING_POT_FOODS),
    TabInfo(CobblemonItems.POTION, CobblemonRecipeCategories.COOKING_POT_MEDICINES),
    TabInfo(CobblemonItems.APRIJUICE_RED, CobblemonRecipeCategories.COOKING_POT_COMPLEX_DISHES),
    TabInfo(CobblemonItems.PROTEIN, CobblemonRecipeCategories.COOKING_POT_MISC)
)) {
    companion object {
        private val FILTER_SPRITES = WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled_highlighted"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled_highlighted"))
    }
    override fun getFilterButtonTextures() = FILTER_SPRITES
    override fun getRecipeFilterName() = Component.translatable("cobblemon.container.campfire_pot.recipe_book.toggle_recipes")
    override fun isCraftingSlot(slot: Slot) = slot.container === menu.container
    override fun selectMatchingRecipes(collection: RecipeCollection, contents: StackedItemContents) {
        collection.selectRecipes(contents) { display -> when (display) {
            is ShapedCraftingRecipeDisplay -> display.width() <= menu.gridWidth && display.height() <= menu.gridHeight
            is ShapelessCraftingRecipeDisplay -> display.ingredients().size <= menu.gridWidth * menu.gridHeight
            else -> false
        } }
    }
    override fun fillGhostRecipe(ghost: GhostSlots, recipe: RecipeDisplay, context: ContextMap) {
        ghost.setResult(menu.slots[menu.resultSlotIndex], context, recipe.result())
        val inputs = CampfireBlockEntity.CRAFTING_GRID_SLOTS.map { menu.slots[it] }
        when (recipe) {
            is ShapedCraftingRecipeDisplay -> PlaceRecipeHelper.placeRecipe(menu.gridWidth, menu.gridHeight,
                recipe.width(), recipe.height(), recipe.ingredients()) { ingredient, index, _, _ ->
                ghost.setInput(inputs[index], context, ingredient)
            }
            is ShapelessCraftingRecipeDisplay -> recipe.ingredients().take(inputs.size).forEachIndexed { index, ingredient ->
                ghost.setInput(inputs[index], context, ingredient)
            }
        }
    }
}
