/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tms

import com.google.gson.annotations.SerializedName
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.crafting.Ingredient

/**
 * DTO used for [TechnicalMachineRecipe] JSON loading because Gson cannot deserialize Minecraft Ingredients.
 */
data class TechnicalMachineRecipeDTO(
    val item: Identifier?,
    val tag: Identifier?,
    @SerializedName("count") private val _count: Int?,
) {
    private val count: Int
        get() = _count ?: 1

    fun toTechnicalMachineRecipe(): TechnicalMachineRecipe {
        require((item != null) != (tag != null)) { "TM recipe entry must define exactly one item or tag" }
        require(count > 0) { "TM ingredient count must be positive" }
        val ingredient = when {
            item != null ->
                Ingredient.of(
                    com.cobblemon.mod.common.util.adapters.RegistryOpsProvider.lookupOrThrow()
                        .lookupOrThrow(Registries.ITEM).getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ITEM, item)).value()
                )

            tag != null ->
                Ingredient.of(
                    com.cobblemon.mod.common.util.adapters.RegistryOpsProvider.lookupOrThrow()
                        .lookupOrThrow(Registries.ITEM).getOrThrow(TagKey.create(Registries.ITEM, tag))
                )

            else ->
                error("Recipe entry must define either 'item' or 'tag'")
        }

        return TechnicalMachineRecipe(
            ingredient = ingredient,
            count = count
        )
    }
}