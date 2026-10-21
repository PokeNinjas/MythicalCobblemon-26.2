/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.entity

import net.minecraft.world.phys.Vec3

/** Independent line pairs cannot join other players' lines in a shared native buffer. */
object FishingLineGeometry {
    fun point(offset: Vec3, fraction: Double): Vec3 = Vec3(offset.x * fraction,
        offset.y * (fraction * fraction + fraction) * .5 + .25 - (1.0 - fraction) * .1,
        offset.z * fraction)

    fun emit(offset: Vec3, vertex: (Vec3, Vec3) -> Unit) {
        for (segment in 0 until 16) {
            val from = point(offset, segment / 16.0)
            val to = point(offset, (segment + 1) / 16.0)
            val delta = to.subtract(from)
            if (delta.lengthSqr() < 1E-12) continue
            val direction = delta.normalize()
            vertex(from, direction)
            vertex(to, direction.reverse())
        }
    }
}
