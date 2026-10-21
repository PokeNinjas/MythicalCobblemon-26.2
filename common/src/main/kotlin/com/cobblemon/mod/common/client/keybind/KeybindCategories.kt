/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.keybind

object KeybindCategories {
    const val COBBLEMON_CATEGORY = "key.cobblemon.categories.cobblemon"
    const val COBBLEMON_DEBUG_CATEGORY = "key.cobblemon.categories.cobblemon.debug"
    private val categories = mutableMapOf<String, net.minecraft.client.KeyMapping.Category>()

    fun nativeCategory(translationKey: String): net.minecraft.client.KeyMapping.Category =
        categories.getOrPut(translationKey) {
            val path = when (translationKey) {
                COBBLEMON_CATEGORY -> "cobblemon"
                COBBLEMON_DEBUG_CATEGORY -> "cobblemon.debug"
                else -> error("Unknown Cobblemon key category: $translationKey")
            }
            net.minecraft.client.KeyMapping.Category.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("cobblemon", path)
            )
        }
}
