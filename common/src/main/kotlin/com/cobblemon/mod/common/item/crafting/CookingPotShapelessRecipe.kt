/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.crafting

import com.cobblemon.mod.common.CobblemonRecipeSerializers
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.util.codec.CodecUtils.createByStringCodec
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.NonNullList
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay
import com.cobblemon.mod.common.api.cooking.CookingRecipeGrid
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level

class CookingPotShapelessRecipe(
    override val groupName: String,
    override val category: CookingPotBookCategory,
    override val resultTemplate: ItemStackTemplate,
    val ingredients: List<Ingredient>,
    override val seasoningTag: TagKey<Item>,
    override val seasoningProcessors: List<SeasoningProcessor>
) : Recipe<CraftingInput>, CookingPotRecipeBase {
    override fun getType(): RecipeType<CookingPotShapelessRecipe> = CobblemonRecipeTypes.COOKING_POT_SHAPELESS
    override fun getSerializer() = CobblemonRecipeSerializers.COOKING_POT_SHAPELESS

    private val placement by lazy { PlacementInfo.create(ingredients) }
    override fun placementInfo() = placement
    override fun matches(input: CraftingInput, level: Level) =
        CookingRecipeGrid.matches(ingredients, input.items().filterNot { it.isEmpty })
    override fun display() = listOf(ShapelessCraftingRecipeDisplay(ingredients.map(Ingredient::display),
        resultDisplay(), stationDisplay()))

    object Serializer {
            val CODEC: MapCodec<CookingPotShapelessRecipe> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter { it.groupName },
                    CookingPotBookCategory.CODEC.fieldOf("category").orElse(CookingPotBookCategory.MISC).forGetter { it.category },
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter { it.resultTemplate },
                    Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter { it.ingredients },
                    TagKey.codec(Registries.ITEM).fieldOf("seasoningTag").orElse(CobblemonItemTags.EMPTY).forGetter { recipe -> recipe.seasoningTag },
                    createByStringCodec<SeasoningProcessor>(
                        { SeasoningProcessor.processors[it] },
                        { it.type },
                        { "Unknown seasoning processor: $it" }
                    ).listOf().fieldOf("seasoningProcessors").forGetter { recipe -> recipe.seasoningProcessors }
                ).apply(instance, ::CookingPotShapelessRecipe)
            }

            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CookingPotShapelessRecipe> =
                StreamCodec.of(::toNetwork, ::fromNetwork)

            private fun fromNetwork(buffer: RegistryFriendlyByteBuf): CookingPotShapelessRecipe {
                val group = buffer.readUtf()
                val category = buffer.readEnum(CookingPotBookCategory::class.java)
                val seasoningTag = TagKey.create(Registries.ITEM, buffer.readIdentifier())
                val size = buffer.readVarInt()
                require(size in 1..9) { "Invalid cooking ingredient count: $size" }
                val ingredients = List(size) { Ingredient.CONTENTS_STREAM_CODEC.decode(buffer) }
                val result = ItemStackTemplate.STREAM_CODEC.decode(buffer)
                val seasoningProcessors = buffer.readList {
                    val type = buffer.readString()
                    SeasoningProcessor.processors[type] ?: error("Unknown seasoning processor: $type")
                }
                return CookingPotShapelessRecipe(group, category, result, ingredients, seasoningTag, seasoningProcessors)
            }

            private fun toNetwork(buffer: RegistryFriendlyByteBuf, recipe: CookingPotShapelessRecipe) {
                buffer.writeUtf(recipe.groupName)
                buffer.writeEnum(recipe.category)
                buffer.writeIdentifier(recipe.seasoningTag.location)
                buffer.writeVarInt(recipe.ingredients.size)
                recipe.ingredients.forEach { ingredient ->
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient)
                }
                ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.resultTemplate)
                buffer.writeCollection(recipe.seasoningProcessors) { _, it ->
                    buffer.writeString(it.type)
                }
            }
    }
}
