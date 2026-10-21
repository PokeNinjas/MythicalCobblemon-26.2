/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.api.gui.GuiTriangleRenderState
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.world.phys.Vec2
import org.joml.Matrix3x2f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuiTriangleTest {
    @Test fun `deferred triangle retains transform color and independent quad topology`() {
        val pose = Matrix3x2f().translate(10F, 20F).scale(2F)
        val state = GuiTriangleRenderState(pose, Vec2(0F, 0F), Vec2(0F, 2F), Vec2(3F, 0F), 0x9932D7FF.toInt(), null)
        pose.identity().translate(500F, 500F)
        val captured = mutableListOf<MutableList<Number>>()
        state.buildVertices(object : VertexConsumer {
            override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply { captured += mutableListOf<Number>(x, y) }
            override fun setColor(color: Int): VertexConsumer = apply { captured.last() += color }
            override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = this
            override fun setUv(u: Float, v: Float): VertexConsumer = this
            override fun setUv1(u: Int, v: Int): VertexConsumer = this
            override fun setUv2(u: Int, v: Int): VertexConsumer = this
            override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
            override fun setLineWidth(width: Float): VertexConsumer = this
        })
        assertEquals(listOf(listOf<Number>(10F, 20F, 0x9932D7FF.toInt()), listOf<Number>(10F, 24F, 0x9932D7FF.toInt()),
            listOf<Number>(16F, 20F, 0x9932D7FF.toInt()), listOf<Number>(16F, 20F, 0x9932D7FF.toInt())), captured)
        assertEquals(ScreenRectangle(10, 20, 6, 4), state.bounds())
    }

    @Test fun `scissor bounds clip transformed triangles and invalid geometry is rejected`() {
        val clip = ScreenRectangle(12, 21, 2, 2)
        val state = GuiTriangleRenderState(Matrix3x2f().translate(10F, 20F).scale(2F),
            Vec2(0F, 0F), Vec2(0F, 2F), Vec2(3F, 0F), -1, clip)
        assertEquals(clip, state.bounds())
        assertThrows(IllegalArgumentException::class.java) {
            GuiTriangleRenderState(Matrix3x2f(), Vec2(Float.NaN, 0F), Vec2(0F, 2F), Vec2(3F, 0F), -1, null)
        }
    }
}
