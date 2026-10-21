/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes

interface BedrockEntityModelState { var bedrockSnapshot: BedrockModelSnapshot? }

/** Native living-entity rendering adapter whose drawing tree comes from the submitted snapshot. */
class SnapshotEntityModel<S>(private val modelYOffset: Float = 24F) : EntityModel<S>(
    ModelPart(emptyList(), linkedMapOf()), RenderTypes::entityCutoutCull
) where S : EntityRenderState, S : BedrockEntityModelState {
    override fun setupAnim(state: S) {
        val snapshot = requireNotNull(state.bedrockSnapshot) { "Bedrock entity submitted before extraction" }
        snapshot.model.setupAnim(snapshot)
        root().y = modelYOffset
        root().children["bedrock"] = snapshot.model.root()
    }
}
