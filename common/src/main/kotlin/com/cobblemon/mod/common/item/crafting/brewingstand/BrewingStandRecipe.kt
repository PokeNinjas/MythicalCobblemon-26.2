/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.crafting.brewingstand

import com.cobblemon.mod.common.CobblemonRecipeSerializers
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.RecipeAccess
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess
import com.cobblemon.mod.common.CobblemonRecipeCategories
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.Level

class BrewingStandRecipe(
    val groupName: String,
    val input: Ingredient,
    val bottle: Ingredient,
    val resultTemplate: ItemStackTemplate
) : Recipe<BrewingStandInput> {

    val result: ItemStack get() = resultTemplate.create()

    override fun getType() = CobblemonRecipeTypes.BREWING_STAND
    override fun group() = groupName
    override fun showNotification() = false
    private val placement by lazy { PlacementInfo.create(listOf(input, bottle)) }
    override fun placementInfo() = placement
    override fun recipeBookCategory() = CobblemonRecipeCategories.BREWING_STAND
    override fun getSerializer() = CobblemonRecipeSerializers.BREWING_STAND
    override fun assemble(input: BrewingStandInput): ItemStack = resultTemplate.create()
    fun getResultItem(registries: HolderLookup.Provider): ItemStack? = resultTemplate.create()

    override fun matches(input: BrewingStandInput, level: Level): Boolean {
        val ingredientMatches = this.input.test(input.getIngredient())
        val validBottles = input.getBottles()
            .filter { !it.isEmpty }
            .all { this.bottle.test(it) }

        return ingredientMatches && validBottles && input.getBottles().any { !it.isEmpty }
    }

    companion object {
        fun isBottle(itemStack: ItemStack, recipeManager: RecipeAccess): Boolean {
            val recipes = (recipeManager as FabricRecipeAccess).synchronizedRecipes.getAllOfType(CobblemonRecipeTypes.BREWING_STAND)
            return recipes.any { recipe ->
                recipe.value().bottle.test(itemStack)
            }
        }

        fun isInput(itemStack: ItemStack, recipeManager: RecipeAccess): Boolean {
            val recipes = (recipeManager as FabricRecipeAccess).synchronizedRecipes.getAllOfType(CobblemonRecipeTypes.BREWING_STAND)
            return recipes.any { recipe ->
                recipe.value().input.test(itemStack)
            }
        }
    }

    object Serializer {
            val CODEC: MapCodec<BrewingStandRecipe> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter { recipe -> recipe.groupName },
                    Ingredient.CODEC.fieldOf("input").forGetter { recipe -> recipe.input },
                    Ingredient.CODEC.fieldOf("bottle").forGetter { recipe -> recipe.bottle },
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter { recipe -> recipe.resultTemplate }
                ).apply(instance, ::BrewingStandRecipe)
            }

            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BrewingStandRecipe> =
                StreamCodec.of(::toNetwork, ::fromNetwork)

            private fun fromNetwork(buffer: RegistryFriendlyByteBuf): BrewingStandRecipe {
                val group = buffer.readUtf(32767)
                val input = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer)
                val bottle = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer)
                val result = ItemStackTemplate.STREAM_CODEC.decode(buffer)
                return BrewingStandRecipe(group, input, bottle, result)
            }

            private fun toNetwork(buffer: RegistryFriendlyByteBuf, recipe: BrewingStandRecipe) {
                buffer.writeUtf(recipe.groupName)
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.input)
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.bottle)
                ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.resultTemplate)
            }
    }
}