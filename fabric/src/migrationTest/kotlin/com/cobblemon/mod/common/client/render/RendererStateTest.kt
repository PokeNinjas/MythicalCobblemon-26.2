/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.SnapshotBedrockModel
import com.cobblemon.mod.common.client.render.models.blockbench.SnapshotEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.BedrockEntityModelState
import com.cobblemon.mod.common.client.render.models.blockbench.BedrockModelSnapshot
import net.minecraft.client.renderer.entity.state.EntityRenderState
import com.cobblemon.mod.common.client.render.pokemon.AlphaEyeGeometry
import com.cobblemon.mod.common.client.render.pokemon.AlphaEyePoint
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RendererStateTest {
    private class NativeEntityState(override var bedrockSnapshot: BedrockModelSnapshot?) : EntityRenderState(), BedrockEntityModelState

    @Test fun `native living model switches real geometry and restores each submitted pose`() {
        val wing = ModelPart(emptyList(), emptyMap())
        val firstModel = SnapshotBedrockModel(ModelPart(emptyList(), mapOf("wing" to wing)))
        wing.xRot = .4F
        val first = NativeEntityState(firstModel.capture())
        val secondModel = SnapshotBedrockModel(ModelPart(emptyList(), mapOf("arm" to ModelPart(emptyList(), emptyMap()))))
        val second = NativeEntityState(secondModel.capture())
        val native = SnapshotEntityModel<NativeEntityState>()
        native.setupAnim(first)
        assertSame(firstModel.root(), native.root().getChild("bedrock"))
        assertEquals(24F, native.root().y)
        native.setupAnim(second)
        assertTrue(native.root().getChild("bedrock").hasChild("arm"))
        assertFalse(native.root().getChild("bedrock").hasChild("wing"))
        native.setupAnim(first)
        assertEquals(.4F, native.root().getChild("bedrock").getChild("wing").xRot)
    }

    @Test
    fun `interleaved submitted poses never change source joints or other instances`() {
        val wing = ModelPart(emptyList(), emptyMap())
        val root = ModelPart(emptyList(), mapOf("wing" to wing))
        val model = SnapshotBedrockModel(root)
        wing.xRot = 0.3F
        wing.xScale = 0.75F
        val first = model.capture()
        wing.xRot = 1.2F
        wing.visible = false
        val second = model.capture()
        model.setupAnim(first)
        val drawingWing = model.root().getChild("wing")
        assertEquals(0.3F, drawingWing.xRot)
        assertEquals(0.75F, drawingWing.xScale)
        assertTrue(drawingWing.visible)
        assertEquals(1.2F, wing.xRot)
        assertFalse(wing.visible)
        model.setupAnim(second)
        assertEquals(1.2F, drawingWing.xRot)
        assertFalse(drawingWing.visible)
        model.setupAnim(first)
        assertEquals(0.3F, drawingWing.xRot)
        assertTrue(drawingWing.visible)
    }

    @Test
    fun `invalid bone transforms are rejected before submission`() {
        val root = ModelPart(emptyList(), emptyMap())
        val model = SnapshotBedrockModel(root)
        root.yScale = Float.NaN
        assertThrows(IllegalStateException::class.java) { model.capture() }
    }

    @Test fun `declared Charizard bounds include every corner through arbitrary rotation`() {
        val radius = com.cobblemon.mod.common.client.render.models.blockbench.BedrockVisibleBounds
            .radius(10F, 5F, listOf(0F, 1.5F, 0F))!!
        val rotation = org.joml.Quaterniond().rotationXYZ(0.7, 1.2, -0.4)
        for (x in listOf(-5.0, 5.0)) for (y in listOf(-1.0, 4.0)) for (z in listOf(-5.0, 5.0)) {
            val point = rotation.transform(org.joml.Vector3d(x, y, z))
            assertTrue(point.length() <= radius + 1e-10)
        }
        assertEquals(kotlin.math.sqrt(66.0), radius, 1e-10)
    }

    @Test fun `missing or invalid declared bounds fall back instead of corrupting culling`() {
        val bounds = com.cobblemon.mod.common.client.render.models.blockbench.BedrockVisibleBounds
        assertNull(bounds.radius(0F, 2F, listOf(0F, 0F, 0F)))
        assertNull(bounds.radius(Float.NaN, 2F, listOf(0F, 0F, 0F)))
        assertNull(bounds.radius(2F, 2F, null))
        assertNull(bounds.radius(2F, 2F, listOf(0F, Float.POSITIVE_INFINITY, 0F)))
        assertNull(bounds.radius(2F, 2F, listOf(0F, 0F)))
    }

    private val eye = AlphaEyePoint("eye", Vec3.ZERO, Vec3(0.0, 0.0, -1.0))
    private val up = Vec3(0.0, 1.0, 0.0)
    private val camera = Vec3(0.0, 0.0, -5.0)

    @Test
    fun `bloom is visible from the front and disappears behind the eye`() {
        val front = AlphaEyeGeometry.extract(listOf(eye), Vec3.ZERO, camera, up, 1F, 0L, mutableMapOf())
        assertTrue(front.bloom.size > 0)
        assertEquals(0, front.bloom.size % 3)
        assertTrue(front.bloom.positions.all(Float::isFinite))
        val back = AlphaEyeGeometry.extract(listOf(eye), Vec3.ZERO, camera.scale(-1.0), up, 1F, 0L, mutableMapOf())
        assertEquals(0, back.bloom.size)
    }

    @Test
    fun `trails expire and removed locators do not leave state behind`() {
        val history = mutableMapOf<String, ArrayDeque<Pair<Vec3, Long>>>()
        AlphaEyeGeometry.extract(listOf(eye), Vec3.ZERO, camera, up, 1F, 0L, history)
        val moving = AlphaEyeGeometry.extract(listOf(eye), Vec3(0.1, 0.0, 0.0), camera, up, 1F, 100L, history)
        assertTrue(moving.trail.size > 0)
        assertEquals(0, moving.trail.size % 4)
        assertTrue(moving.trail.positions.all(Float::isFinite))
        val expired = AlphaEyeGeometry.extract(listOf(eye), Vec3(0.1, 0.0, 0.0), camera, up, 1F, 501L, history)
        assertEquals(0, expired.trail.size)
        AlphaEyeGeometry.extract(emptyList(), Vec3.ZERO, camera, up, 1F, 502L, history)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `high frame rates remain bounded and teleports do not draw long ribbons`() {
        val history = mutableMapOf<String, ArrayDeque<Pair<Vec3, Long>>>()
        repeat(500) { AlphaEyeGeometry.extract(listOf(eye), Vec3.ZERO, camera, up, 1F, 0L, history) }
        assertEquals(AlphaEyeGeometry.MAX_TRAIL_SAMPLES, history.getValue("eye").size)
        val teleported = AlphaEyeGeometry.extract(listOf(eye), Vec3(1000.0, 0.0, 0.0), camera, up, 1F, 1L, history)
        assertEquals(0, teleported.trail.size)
        assertEquals(1, history.getValue("eye").size)
    }
}
