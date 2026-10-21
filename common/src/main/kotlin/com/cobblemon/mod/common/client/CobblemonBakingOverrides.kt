/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client

import com.cobblemon.mod.common.BakingOverride
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.Identifier

/**
 * The purpose of this class is to hold models that we want baked, but aren't associated with
 * any block state. Fabric ModelLoadingPlugin registers their native ExtraModelKeys
 */
object CobblemonBakingOverrides {
    val models = mutableListOf<BakingOverride>()

    // Blocks
    val RESTORATION_TANK_FLUID_BUBBLING = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_bubbling")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_1 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_1")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_2 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_2")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_3 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_3")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_4 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_4")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_5 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_5")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_6 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_6")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_7 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_7")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_8 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_8")
    )
    val RESTORATION_TANK_CONNECTOR = registerOverride(
        cobblemonResource("block/restoration_tank_connector")
    )

    val COARSE_MULCH = registerOverride(
        cobblemonResource("block/coarse_mulch")
    )

    val GROWTH_MULCH = registerOverride(
        cobblemonResource("block/growth_mulch")
    )

    val HUMID_MULCH = registerOverride(
        cobblemonResource("block/humid_mulch")
    )

    val LOAMY_MULCH = registerOverride(
        cobblemonResource("block/loamy_mulch")
    )

    val PEAT_MULCH = registerOverride(
        cobblemonResource("block/peat_mulch")
    )

    val RICH_MULCH = registerOverride(
        cobblemonResource("block/rich_mulch")
    )

    val SANDY_MULCH = registerOverride(
        cobblemonResource("block/sandy_mulch")
    )

    val SURPRISE_MULCH = registerOverride(
        cobblemonResource("block/surprise_mulch")
    )

    fun registerOverride(modelLocation: Identifier): BakingOverride {
        val result = BakingOverride(modelLocation)
        models.add(result)
        return result
    }
}
