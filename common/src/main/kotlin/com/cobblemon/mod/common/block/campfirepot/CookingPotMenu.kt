/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.campfirepot

import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.CobblemonRecipeBookTypes.COOKING_POT
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.api.cooking.Seasonings
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.item.crafting.CookingPotRecipe
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase
import net.minecraft.recipebook.ServerPlaceRecipe
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.StackedItemContents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerListener
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.inventory.RecipeBookMenu
import net.minecraft.world.inventory.RecipeBookType
import net.minecraft.world.inventory.ResultContainer
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import java.util.*

class CookingPotMenu : RecipeBookMenu, ContainerListener {
    private val player: Player
    private val level: Level
    private val playerInventory: Inventory
    val container: CraftingContainer
    val containerData: ContainerData
    var currentActiveRecipe: RecipeHolder<CookingPotRecipeBase>? = null
    var previewItem = ItemStack.EMPTY
    private var placingRecipe = false

    constructor(containerId: Int, playerInventory: Inventory) : super(CobblemonMenuType.COOKING_POT, containerId) {
        this.playerInventory = playerInventory
        this.container = CookingPotContainer(this,
            CampfireBlockEntity.CRAFTING_GRID_WIDTH,
            CampfireBlockEntity.CRAFTING_GRID_WIDTH
        )
        this.containerData = SimpleContainerData(4)
        this.addDataSlots(containerData)
        this.player = playerInventory.player
        this.level = playerInventory.player.level()
        initializeSlots(playerInventory)
        this.addSlotListener(this)
    }

    constructor(containerId: Int, playerInventory: Inventory, container: CraftingContainer, containerData: ContainerData) : super(
        CobblemonMenuType.COOKING_POT, containerId) {
        this.playerInventory = playerInventory
        this.container = container
        this.containerData = containerData
        this.addDataSlots(containerData)
        this.player = playerInventory.player
        this.level = playerInventory.player.level()
        container.startOpen(playerInventory.player)
        initializeSlots(playerInventory)
        this.addSlotListener(this)
    }

    private fun initializeSlots(playerInventory: Inventory) {
        val resultSlotX = 128
        val resultSlotY = 55

        addSlot(CookingPotResultSlot(this.container, CampfireBlockEntity.RESULT_SLOT, resultSlotX, resultSlotY))

        for ((index, slotIndex) in CampfireBlockEntity.CRAFTING_GRID_SLOTS.withIndex()) {
            val i = index / CampfireBlockEntity.CRAFTING_GRID_WIDTH
            val j = index % CampfireBlockEntity.CRAFTING_GRID_WIDTH
            addSlot(Slot(this.container, slotIndex, 33 + j * 18, 18 + i * 18))
        }

        for ((index, slotIndex) in CampfireBlockEntity.SEASONING_SLOTS.withIndex()) {
            addSlot(SeasoningSlot(this, this.container, slotIndex, 110 + index * 18, 18))
        }

        for ((index, _) in CampfireBlockEntity.PLAYER_INVENTORY_SLOTS.withIndex()) {
            val i = index / CampfireBlockEntity.PLAYER_INVENTORY_WIDTH
            val j = index % CampfireBlockEntity.PLAYER_INVENTORY_WIDTH
            addSlot(Slot(playerInventory, index + 9, 8 + j * 18, 84 + i * 18))
        }

        for ((index, _) in CampfireBlockEntity.PLAYER_HOTBAR_SLOTS.withIndex()) {
            addSlot(Slot(playerInventory, index, 8 + index * 18, 142))
        }
    }

    override fun broadcastChanges() {
        super.broadcastChanges()
    }

    override fun handlePlacement(useMaxItems: Boolean, allowDroppingItemsToClear: Boolean,
        recipe: RecipeHolder<*>, level: ServerLevel, inventory: Inventory): PostPlaceAction {
        require(recipe.value() is CookingPotRecipeBase) { "Unsupported cooking recipe: ${recipe.id()}" }
        @Suppress("UNCHECKED_CAST")
        val typedRecipe = recipe as RecipeHolder<CookingPotRecipeBase>
        val inputs = CampfireBlockEntity.CRAFTING_GRID_SLOTS.map { slots[it] }
        placingRecipe = true
        try {
            return ServerPlaceRecipe.placeRecipe(object : ServerPlaceRecipe.CraftingMenuAccess<CookingPotRecipeBase> {
                override fun fillCraftSlotsStackedContents(contents: StackedItemContents) =
                    this@CookingPotMenu.fillCraftSlotsStackedContents(contents)
                override fun clearCraftingContent() {
                    // Completed output and all three seasonings belong to the machine, not recipe placement.
                    for (slot in inputs) slot.set(ItemStack.EMPTY)
                }
                override fun recipeMatches(candidate: RecipeHolder<CookingPotRecipeBase>) =
                    candidate.value().matches(craftingInput(), level)
            }, gridWidth, gridHeight, inputs, inputs, inventory, typedRecipe, useMaxItems, allowDroppingItemsToClear)
        } finally {
            placingRecipe = false
            recalculateRecipe()
            broadcastChanges()
        }
    }

    override fun removed(player: Player) {
        super.removed(player)
        container.stopOpen(player)
    }

    override fun fillCraftSlotsStackedContents(contents: StackedItemContents) {
        for (slot in CampfireBlockEntity.CRAFTING_GRID_SLOTS) contents.accountSimpleStack(container.getItem(slot))
    }

    private fun craftingInput() = CraftingInput.of(gridWidth, gridHeight,
        CampfireBlockEntity.CRAFTING_GRID_SLOTS.map(container::getItem))

    override fun slotsChanged(container: Container) {
        if (!placingRecipe) recalculateRecipe()
        super.slotsChanged(container)
    }

    private fun recalculateRecipe() {
        val craftInput = craftingInput()
        fun <T : CookingPotRecipeBase> fetchRecipe(
            recipeType: RecipeType<T>
        ): Optional<RecipeHolder<CookingPotRecipeBase>> {
            val optional = (level.recipeAccess() as FabricRecipeAccess).synchronizedRecipes.getFirstMatch(recipeType, craftInput, level)
            @Suppress("UNCHECKED_CAST")
            return optional.map { it as RecipeHolder<CookingPotRecipeBase> }
        }

        val recipe = fetchRecipe(CobblemonRecipeTypes.COOKING_POT_COOKING)
            .orElseGet { fetchRecipe(CobblemonRecipeTypes.COOKING_POT_SHAPELESS).orElse(null) }

        currentActiveRecipe = recipe
        if (recipe != null) {
            previewItem = recipe.value().assemble(craftInput)
            // Apply seasoning to the preview item
            recipe.value().applySeasoning(
                previewItem,
                container.items.subList(CampfireBlockEntity.SEASONING_SLOTS.first,
                    CampfireBlockEntity.SEASONING_SLOTS.last + 1)
                    .filterNotNull()
                    .filter { !it.isEmpty && it.`is`(recipe.value().seasoningTag) })

        } else previewItem = ItemStack.EMPTY
    }

    val resultSlotIndex get() = CampfireBlockEntity.RESULT_SLOT

    val gridWidth get() = CampfireBlockEntity.CRAFTING_GRID_WIDTH

    val gridHeight get() = CampfireBlockEntity.CRAFTING_GRID_WIDTH

    fun getSize(): Int {
        return CampfireBlockEntity.ITEMS_SIZE
    }

    fun getBurnProgress(): Float {
        val i = this.containerData.get(0)
        val j = this.containerData.get(1)
        return if (j != 0 && i != 0) {
            Mth.clamp((i.toFloat() / j.toFloat()), 0.0F, 1.0F)
        } else {
            0.0F;
        }
    }

    override fun getRecipeBookType(): RecipeBookType {
        return COOKING_POT
    }


    override fun quickMoveStack(
        player: Player,
        index: Int
    ): ItemStack {
        var itemStack = ItemStack.EMPTY
        val slot = slots[index]

        if (slot.hasItem()) {
            val slotItemStack = slot.item
            itemStack = slotItemStack.copy()

            if (index == CampfireBlockEntity.RESULT_SLOT) {
                if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.PLAYER_INVENTORY_SLOTS.first, CampfireBlockEntity.PLAYER_HOTBAR_SLOTS.last + 1, false)) {
                    return ItemStack.EMPTY
                }

                slot.onQuickCraft(slotItemStack, itemStack);
            } else if (index in CampfireBlockEntity.PLAYER_INVENTORY_SLOTS || index in CampfireBlockEntity.PLAYER_HOTBAR_SLOTS) {
                if (Seasonings.isSeasoning(slotItemStack)) {
                    if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.CRAFTING_GRID_SLOTS.first, CampfireBlockEntity.CRAFTING_GRID_SLOTS.last + 1, false) &&
                        !this.moveItemStackTo(slotItemStack, CampfireBlockEntity.SEASONING_SLOTS.first, CampfireBlockEntity.SEASONING_SLOTS.last + 1, false)
                    ) {
                        return ItemStack.EMPTY
                    }
                } else if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.CRAFTING_GRID_SLOTS.first, CampfireBlockEntity.CRAFTING_GRID_SLOTS.last + 1, false)) {
                    return ItemStack.EMPTY
                }
            } else if (index in CampfireBlockEntity.CRAFTING_GRID_SLOTS || index in CampfireBlockEntity.SEASONING_SLOTS) {
                if (!this.moveItemStackTo(slotItemStack, CampfireBlockEntity.PLAYER_INVENTORY_SLOTS.first, CampfireBlockEntity.PLAYER_HOTBAR_SLOTS.last + 1, false)) {
                    return ItemStack.EMPTY
                }
            }

            if (slotItemStack.isEmpty) {
                slot.setByPlayer(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }

            if (slotItemStack.count == itemStack.count) {
                return ItemStack.EMPTY
            }

            slot.onTake(player, slotItemStack)
        }

        return itemStack
    }

    override fun stillValid(player: Player): Boolean {
        return this.container.stillValid(player)
    }

    override fun slotChanged(containerToSend: AbstractContainerMenu, dataSlotIndex: Int, stack: ItemStack) {
        if (!placingRecipe && (dataSlotIndex in CampfireBlockEntity.CRAFTING_GRID_SLOTS || dataSlotIndex in CampfireBlockEntity.SEASONING_SLOTS)) recalculateRecipe()
    }

    override fun dataChanged(
        containerMenu: AbstractContainerMenu,
        dataSlotIndex: Int,
        value: Int
    ) {
    }
}
