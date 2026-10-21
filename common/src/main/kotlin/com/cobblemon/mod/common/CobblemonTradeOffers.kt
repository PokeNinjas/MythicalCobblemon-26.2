/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.util.cobblemonResource
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.trading.TradeSet

/** Offers live in native villager_trade resources and additive trade tags in 26.2. */
object CobblemonTradeOffers {
    fun nurseTradeSets(): Int2ObjectMap<ResourceKey<TradeSet>> =
        Int2ObjectOpenHashMap<ResourceKey<TradeSet>>().also { sets ->
            for (level in 1..5) {
                sets.put(level, ResourceKey.create(Registries.TRADE_SET, cobblemonResource("nurse/level_$level")))
            }
        }
}
