/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import net.minecraft.world.inventory.RecipeBookType

object CobblemonRecipeBookTypes {
    const val COOKING_POT_NAME = "COBBLEMON_COOKING_POT"
    val COOKING_POT: RecipeBookType = RecipeBookType.valueOf(COOKING_POT_NAME)
}
