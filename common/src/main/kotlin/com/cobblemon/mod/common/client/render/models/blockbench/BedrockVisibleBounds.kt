/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package com.cobblemon.mod.common.client.render.models.blockbench

import kotlin.math.abs
import kotlin.math.sqrt

/** An origin-centered sphere encloses the declared Bedrock box under every rider/body rotation. */
object BedrockVisibleBounds {
    fun radius(width: Float, height: Float, offset: List<Float>?): Double? {
        if (!width.isFinite() || !height.isFinite() || width <= 0F || height <= 0F ||
            offset == null || offset.size != 3 || offset.any { !it.isFinite() }) return null
        val x = width.toDouble() / 2 + abs(offset[0].toDouble())
        val y = height.toDouble() / 2 + abs(offset[1].toDouble())
        val z = width.toDouble() / 2 + abs(offset[2].toDouble())
        return sqrt(x * x + y * y + z * z)
    }
}
