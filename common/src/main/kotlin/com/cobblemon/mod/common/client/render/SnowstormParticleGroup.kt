/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.ParticleGroup
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState
import net.minecraft.util.ARGB

/** Native particle-group lifetime, ticking, limits and frustum culling with frame-owned geometry. */
class SnowstormParticleGroup(engine: ParticleEngine) : ParticleGroup<SnowstormParticle>(engine) {
    companion object { val TYPE = ParticleRenderType("cobblemon:snowstorm", "CS") }
    private val renderState = SnowstormParticleRenderState()
    override fun extractRenderState(frustum: Frustum, camera: Camera, partialTickTime: Float): ParticleGroupRenderState {
        for (particle in particles) {
            if (!particle.invisible && frustum.isVisible(particle.boundingBox)) {
                particle.extractVertices(renderState.vertices(particle.particleTextureSheet), camera, partialTickTime)
            }
        }
        return renderState
    }
}

class SnowstormParticleRenderState : ParticleGroupRenderState {
    private val materials = linkedMapOf<RenderType, SnowstormParticleVertices>()
    fun vertices(material: RenderType) = materials.getOrPut(material) { SnowstormParticleVertices() }
    override fun clear() { materials.values.forEach(SnowstormParticleVertices::clear) }
    override fun submit(collector: SubmitNodeCollector, camera: CameraRenderState) {
        val poses = PoseStack()
        for ((material, vertices) in materials) {
            if (vertices.size > 0) collector.submitCustomGeometry(poses, material) { _, target -> vertices.emit(target) }
        }
    }
}

