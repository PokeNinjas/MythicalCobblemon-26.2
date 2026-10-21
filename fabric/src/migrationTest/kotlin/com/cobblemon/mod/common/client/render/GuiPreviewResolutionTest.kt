/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.gui.previewRasterScale
import org.joml.Matrix3x2f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuiPreviewResolutionTest {
    @Test fun `PC enlargement gets enough source pixels at each native GUI scale`() {
        for (guiScale in 1..4) {
            val raster = previewRasterScale(Matrix3x2f().scale(2.5F), guiScale)
            assertTrue(raster >= 2.5 * guiScale)
            assertTrue(raster < 2.5 * guiScale + 1.0)
            assertEquals(guiScale, previewRasterScale(Matrix3x2f().scale(0.5F), guiScale))
        }
    }

    @Test fun `translation rotation and reflection do not lose the largest displayed scale`() {
        val transform = Matrix3x2f().translate(250F, 140F).rotate(0.6F).scale(-2.5F, 1.5F).rotate(-0.4F)
        val raster = previewRasterScale(transform, 2)
        assertTrue(raster in 5..6)
        for (i in 0 until 360) {
            val direction = org.joml.Vector2f(kotlin.math.cos(i.toDouble()).toFloat(), kotlin.math.sin(i.toDouble()).toFloat())
            transform.transformDirection(direction)
            assertTrue(raster + 1E-5 >= 2 * direction.length(), "under-resolved direction $i")
        }
    }
}
