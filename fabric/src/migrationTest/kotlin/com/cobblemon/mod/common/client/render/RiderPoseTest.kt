/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.player.RiderBonePose
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RiderPoseTest {
    @Test fun `captured rider pose respects each skin geometry and cannot leak another rider's bones`() {
        val animation = ModelPart(emptyList(), emptyMap()).apply { x = 2F; yRot = .5F; xScale = .8F }
        val first = RiderBonePose.capture(animation)
        animation.x = -3F; animation.yRot = -.8F
        val second = RiderBonePose.capture(animation)
        val regular = ModelPart(emptyList(), emptyMap()).apply { setInitialPose(PartPose.offset(-5F, 2F, 0F)); resetPose() }
        val slim = ModelPart(emptyList(), emptyMap()).apply { setInitialPose(PartPose.offset(-5F, 2.5F, 0F)); resetPose() }
        first.apply(regular); second.apply(slim)
        assertEquals(-3F, regular.x); assertEquals(.5F, regular.yRot)
        assertEquals(-8F, slim.x); assertEquals(-.8F, slim.yRot)
        assertEquals(2F, regular.y); assertEquals(2.5F, slim.y)
        regular.resetPose(); first.apply(regular)
        assertEquals(-3F, regular.x); assertEquals(.8F, regular.xScale)
    }

    @Test fun `invalid rider animation values fail before deferred model submission`() {
        val part = ModelPart(emptyList(), emptyMap()).apply { xRot = Float.NaN }
        assertThrows(IllegalArgumentException::class.java) { RiderBonePose.capture(part) }
    }
}
