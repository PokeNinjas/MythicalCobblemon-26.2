/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokeball

import com.cobblemon.mod.common.client.entity.EmptyPokeBallClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonLayerSubmission
import com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import java.util.WeakHashMap

class PokeBallRenderState : EntityRenderState() {
    lateinit var body: BedrockModelSnapshot
    lateinit var texture: Identifier
    var layers: List<PokemonLayerSubmission> = emptyList()
    var yaw = 0F
    var visible = true
}

class PokeBallRenderer(context: EntityRendererProvider.Context) : EntityRenderer<EmptyPokeBallEntity, PokeBallRenderState>(context) {
    private val models = WeakHashMap<PosableModel, SnapshotBedrockModel>()
    override fun createRenderState() = PokeBallRenderState()
    override fun extractRenderState(entity: EmptyPokeBallEntity, state: PokeBallRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        val delegate = entity.delegate as EmptyPokeBallClientDelegate
        delegate.currentAspects = entity.aspects
        delegate.updateAge(entity.tickCount)
        delegate.updatePartialTicks(partialTicks)
        val poser = VaryingModelRepository.getPoser(entity.pokeBall.name, delegate)
        state.texture = VaryingModelRepository.getTexture(entity.pokeBall.name, delegate)
        state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.yRot)
        state.visible = !entity.isInvisible
        val animation = RenderContext()
        animation.put(RenderContext.POSABLE_STATE, delegate)
        animation.put(RenderContext.ASPECTS, entity.aspects)
        animation.put(RenderContext.ENTITY, entity)
        animation.put(RenderContext.TEXTURE, state.texture)
        animation.put(RenderContext.SCALE, .7F)
        animation.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
        poser.context = animation
        try {
            poser.applyAnimations(entity, delegate, 0F, 0F, entity.tickCount + partialTicks, 0F, 0F)
            val model = models[poser]?.takeIf { it.sourceRoot === poser.rootPart }
                ?: SnapshotBedrockModel(poser.rootPart as ModelPart).also { models[poser] = it }
            state.body = model.capture()
            state.layers = PokemonMaterials.extractLayers(entity.pokeBall.name, delegate)
            val locatorPose = PoseStack()
            applyTransform(state, locatorPose)
            poser.locatorAccess.update(locatorPose, entity, .7F, delegate.locatorStates, isRoot = true)
        } finally {
            poser.setDefault()
            animation.pop()
        }
    }
    private fun applyTransform(state: PokeBallRenderState, poses: PoseStack) {
        poses.mulPose(Axis.YP.rotationDegrees(state.yaw))
        poses.scale(.7F, -.7F, -.7F)
    }
    override fun submit(state: PokeBallRenderState, poses: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        if (state.visible || state.appearsGlowing()) {
            poses.pushPose()
            applyTransform(state, poses)
            collector.submitModel(state.body.model, state.body, poses,
                if (state.visible) RenderTypes.entityCutoutCull(state.texture) else RenderTypes.outline(state.texture),
                state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null)
            if (state.visible) for ((index, layer) in state.layers.withIndex()) {
                val snapshot = state.body
                val light = if (layer.emissive) 15728880 else state.lightCoords
                if (layer.uOffset == 0F && layer.vOffset == 0F) {
                    collector.order(index + 1).submitModel(snapshot.model, snapshot, poses, layer.type, light,
                        OverlayTexture.NO_OVERLAY, layer.color, null, state.outlineColor, null)
                } else {
                    collector.order(index + 1).submitCustomGeometry(poses, layer.type) { pose, buffer ->
                        val drawing = PoseStack(); drawing.last().set(pose)
                        snapshot.model.setupAnim(snapshot)
                        snapshot.model.renderToBuffer(drawing, ScrollingVertexConsumer(buffer, layer.uOffset, layer.vOffset), light, OverlayTexture.NO_OVERLAY, layer.color)
                    }
                }
            }
            poses.popPose()
        }
        super.submit(state, poses, collector, camera)
    }
}
