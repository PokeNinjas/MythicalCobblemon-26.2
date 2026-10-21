/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.util.ARGB

/** Packed CPU storage supports arbitrary rectangular, rotated and animated particle quads. */
class SnowstormParticleVertices : VertexConsumer {
    private var positionsAndUvs = FloatArray(5 * 256)
    private var colorsAndLight = IntArray(2 * 256)
    var size = 0; private set
    fun clear() { size = 0 }
    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
        if (size * 5 == positionsAndUvs.size) {
            positionsAndUvs = positionsAndUvs.copyOf(positionsAndUvs.size * 2)
            colorsAndLight = colorsAndLight.copyOf(colorsAndLight.size * 2)
        }
        val i = size * 5
        positionsAndUvs[i] = x; positionsAndUvs[i + 1] = y; positionsAndUvs[i + 2] = z
        positionsAndUvs[i + 3] = 0F; positionsAndUvs[i + 4] = 0F
        colorsAndLight[size * 2] = -1; colorsAndLight[size * 2 + 1] = 15728880
        size++
    }
    override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = setColor(ARGB.color(a, r, g, b))
    override fun setColor(color: Int): VertexConsumer = apply { colorsAndLight[(size - 1) * 2] = color }
    override fun setUv(u: Float, v: Float): VertexConsumer = apply {
        positionsAndUvs[(size - 1) * 5 + 3] = u; positionsAndUvs[(size - 1) * 5 + 4] = v
    }
    override fun setUv2(u: Int, v: Int): VertexConsumer = apply { colorsAndLight[(size - 1) * 2 + 1] = u or (v shl 16) }
    override fun setUv1(u: Int, v: Int): VertexConsumer = this // Particle vertex format has no overlay attribute.
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this // Particle shader uses billboard lighting.
    override fun setLineWidth(width: Float): VertexConsumer = this // Quad topology, not line geometry.
    fun emit(target: VertexConsumer) {
        check(size % 4 == 0) { "Incomplete Snowstorm quad" }
        for (vertex in 0 until size) {
            val i = vertex * 5
            target.addVertex(positionsAndUvs[i], positionsAndUvs[i + 1], positionsAndUvs[i + 2])
                .setUv(positionsAndUvs[i + 3], positionsAndUvs[i + 4])
                .setColor(colorsAndLight[vertex * 2]).setLight(colorsAndLight[vertex * 2 + 1])
        }
    }
}
