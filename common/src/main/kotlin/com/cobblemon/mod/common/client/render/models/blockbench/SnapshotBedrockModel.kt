/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render.models.blockbench

import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.rendertype.RenderTypes

/** A submitted pose owns its data and contains no entity, world or MoLang references. */
class BedrockModelSnapshot internal constructor(
    val model: SnapshotBedrockModel,
    internal val transforms: FloatArray,
    internal val visible: BooleanArray,
    internal val skipDraw: BooleanArray
)

/** Immutable cubes are shared; drawing joints are separate from the animation engine's joints. */
class SnapshotBedrockModel(val sourceRoot: ModelPart) : Model<BedrockModelSnapshot>(
    copyDrawingTree(sourceRoot), RenderTypes::entityCutoutCull
) {
    private val sourceParts = sourceRoot.allParts.toList()

    fun capture(): BedrockModelSnapshot {
        val transforms = FloatArray(sourceParts.size * 9)
        val visible = BooleanArray(sourceParts.size)
        val skipDraw = BooleanArray(sourceParts.size)
        sourceParts.forEachIndexed { i, p ->
            val offset = i * 9
            transforms[offset] = p.x; transforms[offset + 1] = p.y; transforms[offset + 2] = p.z
            transforms[offset + 3] = p.xRot; transforms[offset + 4] = p.yRot; transforms[offset + 5] = p.zRot
            transforms[offset + 6] = p.xScale; transforms[offset + 7] = p.yScale; transforms[offset + 8] = p.zScale
            visible[i] = p.visible; skipDraw[i] = p.skipDraw
        }
        check(transforms.all(Float::isFinite)) { "Non-finite Bedrock bone transform" }
        return BedrockModelSnapshot(this, transforms, visible, skipDraw)
    }

    override fun setupAnim(state: BedrockModelSnapshot) {
        require(state.model === this) { "Pose submitted to a different Bedrock geometry" }
        allParts().forEachIndexed { i, p ->
            val offset = i * 9
            p.x = state.transforms[offset]; p.y = state.transforms[offset + 1]; p.z = state.transforms[offset + 2]
            p.xRot = state.transforms[offset + 3]; p.yRot = state.transforms[offset + 4]; p.zRot = state.transforms[offset + 5]
            p.xScale = state.transforms[offset + 6]; p.yScale = state.transforms[offset + 7]; p.zScale = state.transforms[offset + 8]
            p.visible = state.visible[i]; p.skipDraw = state.skipDraw[i]
        }
    }

    companion object {
        private fun copyDrawingTree(source: ModelPart): ModelPart = ModelPart(
            source.cubes, source.children.mapValuesTo(linkedMapOf()) { copyDrawingTree(it.value) }
        )
    }
}
