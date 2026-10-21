/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import net.minecraft.client.model.geom.ModelPart
import org.joml.Matrix4f

data class MountedRiderPose(val bones: Map<String, RiderBonePose>, val rootTransform: Matrix4f)

data class RiderBonePose(val x: Float, val y: Float, val z: Float, val xRot: Float, val yRot: Float, val zRot: Float,
                         val xScale: Float, val yScale: Float, val zScale: Float) {
    init { require(listOf(x, y, z, xRot, yRot, zRot, xScale, yScale, zScale).all(Float::isFinite)) { "Non-finite rider bone transform" } }
    fun apply(part: ModelPart) {
        part.x += x; part.y += y; part.z += z
        part.xRot = xRot; part.yRot = yRot; part.zRot = zRot
        part.xScale *= xScale; part.yScale *= yScale; part.zScale *= zScale
    }
    companion object {
        fun capture(part: ModelPart) = RiderBonePose(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)
    }
}

interface MountedLivingRenderState {
    var riderHidden: Boolean
    var seatTransform: Matrix4f?
    var riderPose: MountedRiderPose?
}
