/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import kotlin.math.floor

data class AnimationTextureFrame(val current: Int, val next: Int, val blend: Float)

object TextureInterpolation {
    fun frame(seconds: Float, fps: Float, count: Int, loop: Boolean): AnimationTextureFrame {
        require(count > 0 && fps.isFinite() && fps > 0F && seconds.isFinite()) { "Invalid animated texture timing" }
        val position = seconds.toDouble().coerceAtLeast(0.0) * fps
        if (!loop && position >= count - 1) return AnimationTextureFrame(count - 1, count - 1, 0F)
        val whole = floor(position).toLong()
        val current = Math.floorMod(whole, count)
        return AnimationTextureFrame(current, (current + 1) % count, (position - floor(position)).toFloat())
    }

    /** ARGB interpolation preserves opaque RGB when its neighbour is fully transparent. */
    fun pixel(first: Int, second: Int, blend: Float): Int {
        val a = first ushr 24
        val b = second ushr 24
        val t = blend.coerceIn(0F, 1F)
        fun mix(x: Int, y: Int): Int = floor(x + (y - x) * t).toInt().coerceIn(0, 255)
        fun color(shift: Int): Int {
            val x = (first ushr shift) and 255
            val y = (second ushr shift) and 255
            return when {
                a == 0 && b == 255 -> y
                a == 255 && b == 0 -> x
                else -> mix(x, y)
            }
        }
        return (mix(a, b) shl 24) or (color(16) shl 16) or (color(8) shl 8) or color(0)
    }
}
