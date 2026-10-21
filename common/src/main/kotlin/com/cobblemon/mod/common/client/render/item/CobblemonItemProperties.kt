/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.mojang.serialization.MapCodec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap

/** The original per-item predicates, evaluated during native item-state extraction. */
object CobblemonItemProperties {
    fun interface Property {
        fun get(stack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float
    }
    private val properties = IdentityHashMap<Item, MutableMap<Identifier, Property>>()
    fun register(item: Item, id: Identifier, property: Property) {
        properties.getOrPut(item) { mutableMapOf() }[id] = property
    }
    fun get(stack: ItemStack, id: Identifier, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float =
        properties[stack.item]?.get(id)?.get(stack, level, owner, seed) ?: 0F
}

data class CobblemonItemProperty(val id: Identifier) : RangeSelectItemModelProperty {
    override fun get(stack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int) =
        CobblemonItemProperties.get(stack, id, level, owner, seed)
    override fun type() = CODEC
    companion object {
        val CODEC: MapCodec<CobblemonItemProperty> = Identifier.CODEC.fieldOf("predicate")
            .xmap(::CobblemonItemProperty, CobblemonItemProperty::id)
    }
}
