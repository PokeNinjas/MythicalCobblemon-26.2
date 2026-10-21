/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.entity.FishingLineGeometry
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FishingLineTest {
    @Test fun `colored fishing line has exact hook and hand endpoints with independent segment pairs`() {
        val offset = Vec3(5.0, -3.0, 12.0)
        val vertices = mutableListOf<Pair<Vec3, Vec3>>()
        FishingLineGeometry.emit(offset) { point, direction -> vertices += point to direction }
        assertEquals(32, vertices.size)
        assertEquals(Vec3(0.0, .15, 0.0), vertices.first().first)
        assertEquals(offset.add(0.0, .25, 0.0), vertices.last().first)
        for (i in 0 until 15) assertEquals(vertices[i * 2 + 1].first, vertices[i * 2 + 2].first)
        for ((_, direction) in vertices) assertEquals(1.0, direction.length(), 1E-9)
        for (i in 0 until 16) assertEquals(vertices[i * 2].second.reverse(), vertices[i * 2 + 1].second)
    }

    @Test fun `coincident and nearly coincident owner positions never produce invalid line normals`() {
        for (offset in listOf(Vec3.ZERO, Vec3(1E-14, -.1, 1E-14), Vec3(0.0, -3.2, 0.0))) {
            FishingLineGeometry.emit(offset) { point, direction ->
                assertTrue(point.x.isFinite() && point.y.isFinite() && point.z.isFinite())
                assertTrue(direction.x.isFinite() && direction.y.isFinite() && direction.z.isFinite())
                assertEquals(1.0, direction.length(), 1E-9)
            }
        }
    }
}
