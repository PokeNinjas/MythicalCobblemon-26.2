/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cooking

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import java.util.Optional

object CookingRecipeGrid {
    fun <T : Any> pad(width: Int, height: Int, pattern: List<Optional<T>>): List<Optional<T>> {
        require(width in 1..3 && height in 1..3 && pattern.size == width * height)
        val result = MutableList(9) { Optional.empty<T>() }
        val left = if (width == 1) 1 else 0
        val top = if (height == 1) 1 else 0
        for (y in 0 until height) for (x in 0 until width) result[(y + top) * 3 + x + left] = pattern[y * width + x]
        return result
    }

    fun matches(ingredients: List<Ingredient>, items: List<ItemStack>): Boolean =
        matchAssignments(ingredients.size, items.size) { ingredient, item -> ingredients[ingredient].test(items[item]) }

    /** Augmenting paths allow an earlier broad ingredient to give up an item to a narrower one. */
    fun matchAssignments(ingredientCount: Int, itemCount: Int, accepts: (Int, Int) -> Boolean): Boolean {
        if (ingredientCount != itemCount || ingredientCount !in 1..9) return false
        val assigned = IntArray(itemCount) { -1 }
        fun assign(ingredient: Int, seen: BooleanArray): Boolean {
            for (item in 0 until itemCount) {
                if (seen[item] || !accepts(ingredient, item)) continue
                seen[item] = true
                if (assigned[item] == -1 || assign(assigned[item], seen)) {
                    assigned[item] = ingredient
                    return true
                }
            }
            return false
        }
        return (0 until ingredientCount).all { assign(it, BooleanArray(itemCount)) }
    }
}
