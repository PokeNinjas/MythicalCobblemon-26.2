/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.api.gui

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.world.phys.Vec2
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import kotlin.math.ceil
import kotlin.math.floor

/** A triangle represented by one native GUI quad with a degenerate second triangle. */
class GuiTriangleRenderState(pose: Matrix3x2fc, first: Vec2, second: Vec2, third: Vec2,
    private val color: Int, private val scissor: ScreenRectangle?) : GuiElementRenderState {
    private val pose = Matrix3x2f(pose)
    private val points = floatArrayOf(first.x, first.y, second.x, second.y, third.x, third.y)
    private val rectangle: ScreenRectangle?
    init {
        require(points.all { it.isFinite() } && this.pose.isFinite) { "GUI triangle coordinates must be finite" }
        val minX = floor(minOf(first.x, second.x, third.x)).toInt()
        val minY = floor(minOf(first.y, second.y, third.y)).toInt()
        val maxX = ceil(maxOf(first.x, second.x, third.x)).toInt()
        val maxY = ceil(maxOf(first.y, second.y, third.y)).toInt()
        val transformed = ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(this.pose)
        rectangle = if (scissor == null) transformed else transformed.intersection(scissor)
    }
    override fun pipeline() = RenderPipelines.GUI
    override fun textureSetup() = TextureSetup.noTexture()
    override fun scissorArea() = scissor
    override fun bounds() = rectangle
    override fun buildVertices(vertices: VertexConsumer) {
        for (index in intArrayOf(0, 2, 4, 4)) {
            vertices.addVertexWith2DPose(pose, points[index], points[index + 1]).setColor(color)
        }
    }
}
