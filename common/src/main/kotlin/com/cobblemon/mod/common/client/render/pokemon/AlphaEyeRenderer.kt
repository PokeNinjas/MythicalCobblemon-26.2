/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.sqrt

/** Reads the real delegate during extraction; submitted geometry contains only positions and colors. */
fun extractAlphaEyes(entity: PokemonEntity, delegate: PokemonClientDelegate, partialTicks: Float): AlphaEyeRenderState {
    if ("alpha_eyes" !in delegate.currentAspects || entity.isInvisible || delegate.activeSendoutScale <= 0F) {
        delegate.eyeTrailPositions.clear()
        return AlphaEyeGeometry.EMPTY
    }
    val eyes = delegate.locatorStates.mapNotNull { (name, transform) ->
        if ("eye" !in name.lowercase() || !delegate.isLocatorVisible(name)) return@mapNotNull null
        val position = transform.matrix.getTranslation(Vector3f())
        val forward = transform.matrix.transformDirection(Vector3f(0F, 0F, -1F))
        AlphaEyePoint(name, Vec3(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()),
            Vec3(forward.x.toDouble(), forward.y.toDouble(), forward.z.toDouble()))
    }
    val camera = Minecraft.getInstance().gameRenderer.mainCamera()
    val up = camera.upVector()
    return AlphaEyeGeometry.extract(eyes, entity.getPosition(partialTicks), camera.position(),
        Vec3(up.x().toDouble(), up.y().toDouble(), up.z().toDouble()), getHitboxScaling(entity),
        System.currentTimeMillis(), delegate.eyeTrailPositions)
}

fun submitAlphaEyes(state: AlphaEyeRenderState, poses: PoseStack, collector: SubmitNodeCollector) {
    fun submit(vertices: AlphaEyeVertices, bloom: Boolean) {
        if (vertices.size == 0) return
        collector.order(2).submitCustomGeometry(poses, if (bloom) RenderTypes.dragonRays() else RenderTypes.lightning()) { pose, buffer ->
            for (i in 0 until vertices.size) buffer.addVertex(pose.pose(), vertices.positions[i * 3], vertices.positions[i * 3 + 1], vertices.positions[i * 3 + 2]).setColor(vertices.colors[i])
        }
    }
    submit(state.bloom, true)
    submit(state.trail, false)
}

fun getHitboxScaling(entity: PokemonEntity): Float {
    val form = entity.exposedForm
    return sqrt((max(form.hitbox.width, form.hitbox.height) * form.baseScale * entity.pokemon.effectiveScale / 3F).coerceAtLeast(0F))
}
