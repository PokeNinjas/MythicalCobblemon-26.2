/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.item.crafting.CookingPotBookCategory
import net.minecraft.resources.Identifier
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.RecipeBookType
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory
import net.minecraft.world.item.crafting.RecipeBookCategory

/** Recipe categories are registry entries in 26.2, not injected enum constants. */
object CobblemonRecipeCategories {
    val COOKING_POT_SEARCH = object : ExtendedRecipeBookCategory {}
    val COOKING_POT_FOODS = register("cooking_pot_foods")
    val COOKING_POT_MEDICINES = register("cooking_pot_medicines")
    val COOKING_POT_COMPLEX_DISHES = register("cooking_pot_complex_dishes")
    val COOKING_POT_MISC = register("cooking_pot_misc")
    val BREWING_STAND = register("brewing_stand")
    val cookingCategories = listOf(COOKING_POT_FOODS, COOKING_POT_MEDICINES, COOKING_POT_COMPLEX_DISHES, COOKING_POT_MISC)

    private fun register(path: String) = Registry.register(BuiltInRegistries.RECIPE_BOOK_CATEGORY,
        Identifier.fromNamespaceAndPath("cobblemon", path), RecipeBookCategory())
    fun initialize() {}
    fun forCookingCategory(category: CookingPotBookCategory): RecipeBookCategory = when (category) {
        CookingPotBookCategory.FOODS -> COOKING_POT_FOODS
        CookingPotBookCategory.MEDICINES -> COOKING_POT_MEDICINES
        CookingPotBookCategory.COMPLEX_DISHES -> COOKING_POT_COMPLEX_DISHES
        CookingPotBookCategory.MISC -> COOKING_POT_MISC
    }
}
