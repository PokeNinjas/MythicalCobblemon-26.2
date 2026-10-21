/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.npc

import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.item.HeldItemRenderState
import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonLayerSubmission
import com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Pose
import java.util.WeakHashMap
import kotlin.math.min

class NPCRenderState : LivingEntityRenderState(), BedrockEntityModelState {
    override var bedrockSnapshot: BedrockModelSnapshot? = null
    lateinit var texture: Identifier
    var materials: List<PokemonLayerSubmission> = emptyList()
    var items: List<HeldItemRenderState> = emptyList()
    var npcScale = 1F
    var npcShadow = .5F
}

/** Vanilla owns living-entity orientation, visibility, overlays, labels and shadow submission. */
class NPCRenderer(context: EntityRendererProvider.Context) : LivingEntityRenderer<NPCEntity, NPCRenderState, SnapshotEntityModel<NPCRenderState>>(
    context, SnapshotEntityModel(), .5F
) {
    private val snapshots = WeakHashMap<PosableModel, SnapshotBedrockModel>()
    private val heldItems = HeldItemRenderer()
    init {
        addLayer(object : RenderLayer<NPCRenderState, SnapshotEntityModel<NPCRenderState>>(this) {
            override fun submit(poses: PoseStack, collector: SubmitNodeCollector, light: Int, state: NPCRenderState, yRot: Float, xRot: Float) {
                if (state.isInvisible && state.isInvisibleToPlayer) return
                val model = parentModel
                val overlay = getOverlayCoords(state, 0F)
                for ((index, layer) in state.materials.withIndex()) {
                    val layerLight = if (layer.emissive) 15728880 else light
                    if (layer.uOffset == 0F && layer.vOffset == 0F) {
                        collector.order(index + 1).submitModel(model, state, poses, layer.type, layerLight, overlay,
                            layer.color, null, state.outlineColor, null)
                    } else {
                        collector.order(index + 1).submitCustomGeometry(poses, layer.type) { pose, buffer ->
                            val drawing = PoseStack(); drawing.last().set(pose)
                            model.setupAnim(state)
                            model.renderToBuffer(drawing, ScrollingVertexConsumer(buffer, layer.uOffset, layer.vOffset), layerLight, overlay, layer.color)
                        }
                    }
                }
            }
        })
    }
    override fun createRenderState() = NPCRenderState()
    override fun getTextureLocation(state: NPCRenderState) = state.texture
    override fun scale(state: NPCRenderState, poses: PoseStack) { poses.scale(state.npcScale, state.npcScale, state.npcScale) }
    override fun getShadowRadius(state: NPCRenderState) = state.npcShadow
    override fun shouldShowName(entity: NPCEntity, distanceToCameraSq: Double) = !entity.hideNameTag && super.shouldShowName(entity, distanceToCameraSq)

    override fun extractRenderState(entity: NPCEntity, state: NPCRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.npcScale = entity.renderScale * entity.npc.modelScale
        state.npcShadow = min(entity.boundingBox.xsize, entity.boundingBox.zsize).toFloat() / 1.5F
        val delegate = entity.delegate as NPCClientDelegate
        delegate.currentAspects = entity.aspects
        delegate.updateAge(entity.tickCount)
        delegate.updatePartialTicks(partialTicks)
        val poser = VaryingModelRepository.getPoser(entity.resourceIdentifier, delegate)
        val texture = VaryingModelRepository.getTexture(entity.resourceIdentifier, delegate)
        val animation = RenderContext()
        animation.put(RenderContext.ENTITY, entity)
        animation.put(RenderContext.POSABLE_STATE, delegate)
        animation.put(RenderContext.ASPECTS, entity.aspects)
        animation.put(RenderContext.TEXTURE, texture)
        animation.put(RenderContext.SCALE, state.npcScale * state.scale)
        animation.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
        poser.context = animation
        try {
            poser.applyAnimations(entity, delegate, state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot)
            val drawing = snapshots[poser]?.takeIf { it.sourceRoot === poser.rootPart }
                ?: SnapshotBedrockModel(poser.rootPart as ModelPart).also { snapshots[poser] = it }
            state.bedrockSnapshot = drawing.capture()
            state.texture = texture
            state.materials = PokemonMaterials.extractLayers(entity.resourceIdentifier, delegate, if (state.isInvisible) 654311423 else -1)
            // Repeat the native living transform during extraction so items and particles use the body frame.
            val locatorPose = PoseStack()
            if (state.hasPose(Pose.SLEEPING)) state.bedOrientation?.let { direction ->
                val offset = state.eyeHeight - .1F
                locatorPose.translate(-direction.stepX * offset, 0F, -direction.stepZ * offset)
            }
            locatorPose.scale(state.scale, state.scale, state.scale)
            setupRotations(state, locatorPose, state.bodyRot, state.scale)
            locatorPose.scale(-1F, -1F, 1F)
            scale(state, locatorPose)
            locatorPose.translate(0F, -.001F, 0F)
            poser.locatorAccess.update(locatorPose, entity, state.npcScale * state.scale, delegate.locatorStates, isRoot = true)
            state.items = if (entity.deathTime < 1 && !state.isInvisible) heldItems.extract(entity.mainHandItem, delegate, entity) else emptyList()
        } finally {
            poser.setDefault()
            animation.pop()
        }
    }

    override fun submit(state: NPCRenderState, poses: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        super.submit(state, poses, collector, camera)
        heldItems.submit(state.items, poses, collector, state.lightCoords, state.outlineColor)
    }
}
