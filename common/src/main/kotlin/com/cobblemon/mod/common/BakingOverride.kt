/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.resources.Identifier

/** Extra block geometry participates in the native dependency/bake/reload lifecycle. */
class BakingOverride(val modelLocation: Identifier) {
    val key: ExtraModelKey<BlockStateModel> = ExtraModelKey.create { modelLocation.toString() }
    fun getModel(): BlockStateModel = requireNotNull(Minecraft.getInstance().modelManager.getModel(key)) {
        "Cobblemon extra model has not been baked: $modelLocation"
    }
}
