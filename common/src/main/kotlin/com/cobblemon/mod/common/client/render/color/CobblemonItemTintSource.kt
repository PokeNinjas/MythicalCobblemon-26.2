/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.color

import com.mojang.serialization.MapCodec
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.util.ARGB
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap

object CobblemonItemColors {
    private val providers = IdentityHashMap<Item, ItemColor>()
    fun register(provider: ItemColor, vararg items: Item) {
        items.forEach { providers[it] = provider }
    }
    fun color(stack: ItemStack, index: Int): Int = ARGB.opaque(providers[stack.item]?.getColor(stack, index) ?: -1)
}

data class CobblemonItemTintSource(val index: Int) : ItemTintSource {
    override fun calculate(stack: ItemStack, level: ClientLevel?, owner: LivingEntity?) = CobblemonItemColors.color(stack, index)
    override fun type() = CODEC
    companion object {
        val CODEC: MapCodec<CobblemonItemTintSource> = ExtraCodecs.NON_NEGATIVE_INT.fieldOf("index")
            .xmap(::CobblemonItemTintSource, CobblemonItemTintSource::index)
    }
}
