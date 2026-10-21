/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

import com.mojang.blaze3d.vertex.VertexConsumer

/** Frame-owned UV offsets, applied as vertices are emitted by the native submission queue. */
class ScrollingVertexConsumer(private val target: VertexConsumer, private val uOffset: Float, private val vOffset: Float) : VertexConsumer {
    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply { target.addVertex(x, y, z) }
    override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = apply { target.setColor(r, g, b, a) }
    override fun setColor(color: Int): VertexConsumer = apply { target.setColor(color) }
    override fun setUv(u: Float, v: Float): VertexConsumer = apply { target.setUv(u + uOffset, v + vOffset) }
    override fun setUv1(u: Int, v: Int): VertexConsumer = apply { target.setUv1(u, v) }
    override fun setUv2(u: Int, v: Int): VertexConsumer = apply { target.setUv2(u, v) }
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = apply { target.setNormal(x, y, z) }
    override fun setLineWidth(width: Float): VertexConsumer = apply { target.setLineWidth(width) }
}
