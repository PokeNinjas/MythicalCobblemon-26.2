/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.pokemon.PokemonLayerSubmission
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

class CobblemonBlockRenderState : BlockEntityRenderState() {
    val items = mutableListOf<BlockItemSubmission>()
    val models = mutableListOf<BlockModelSubmission>()
    val quads = mutableListOf<BlockQuadSubmission>()
    val blocks = mutableListOf<BlockPartsSubmission>()
    var movingBlock: net.minecraft.client.renderer.block.MovingBlockRenderState? = null
    var previewEntity: BlockEntityPreview? = null

    fun addBlock(state: net.minecraft.world.level.block.state.BlockState, poses: PoseStack) {
        val parts = mutableListOf<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>()
        Minecraft.getInstance().modelManager.blockStateModelSet.get(state)
            .collectParts(net.minecraft.util.RandomSource.create(state.getSeed(blockPos)), parts)
        blocks += BlockPartsSubmission(Matrix4f(poses.last().pose()), parts.toList(), intArrayOf(), RenderTypes.cutoutMovingBlock())
    }

    fun addExtraModel(model: com.cobblemon.mod.common.BakingOverride, poses: PoseStack,
                      translucent: Boolean = false, color: Int = -1) {
        val parts = mutableListOf<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>()
        model.getModel().collectParts(net.minecraft.util.RandomSource.create(blockPos.asLong()), parts)
        blocks += BlockPartsSubmission(Matrix4f(poses.last().pose()), parts.toList(), intArrayOf(),
            if (translucent) RenderTypes.translucentMovingBlock() else RenderTypes.cutoutMovingBlock(), color)
    }

    fun addItem(stack: ItemStack, context: ItemDisplayContext, poses: PoseStack, level: Level?, seed: Int = 0): ItemStackRenderState {
        val item = ItemStackRenderState()
        Minecraft.getInstance().itemModelResolver.updateForTopItem(item, stack, context, level as? ClientLevel, null, seed)
        if (!item.isEmpty) items += BlockItemSubmission(item, Matrix4f(poses.last().pose()))
        return item
    }
}

data class BlockEntityPreview(val state: net.minecraft.client.renderer.entity.state.EntityRenderState, val transform: Matrix4f)

data class BlockQuadSubmission(val transform: Matrix4f, val texture: Identifier, val color: Int,
                               val light: Int, val width: Float, val height: Float)
data class BlockPartsSubmission(val transform: Matrix4f,
    val parts: List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>, val tintLayers: IntArray,
    val type: net.minecraft.client.renderer.rendertype.RenderType, val color: Int = -1)

data class BlockItemSubmission(val item: ItemStackRenderState, val transform: Matrix4f)
data class BlockModelSubmission(val body: BedrockModelSnapshot, val texture: Identifier, val transform: Matrix4f,
                                val layers: List<PokemonLayerSubmission>, val cull: Boolean = true)

/** Shared native extraction/submission lifecycle; game state is read only during extraction. */
abstract class CobblemonBlockEntityRenderer<T : BlockEntity> : BlockEntityRenderer<T, CobblemonBlockRenderState> {
    override fun createRenderState() = CobblemonBlockRenderState()
    final override fun extractRenderState(entity: T, state: CobblemonBlockRenderState, partialTicks: Float,
                                         camera: Vec3, breakProgress: ModelFeatureRenderer.CrumblingOverlay?) {
        super.extractRenderState(entity, state, partialTicks, camera, breakProgress)
        state.items.clear()
        state.models.clear()
        state.quads.clear()
        state.blocks.clear()
        state.movingBlock = null
        state.previewEntity = null
        extractParts(entity, partialTicks, PoseStack(), state)
    }
    protected abstract fun extractParts(entity: T, partialTicks: Float, poses: PoseStack, state: CobblemonBlockRenderState)

    override fun submit(state: CobblemonBlockRenderState, poses: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        state.movingBlock?.let { collector.submitMovingBlock(poses, it, 0) }
        state.previewEntity?.let { preview ->
            poses.pushPose()
            poses.mulPose(preview.transform)
            Minecraft.getInstance().entityRenderDispatcher.submit(preview.state, camera, 0.0, 0.0, 0.0, poses, collector)
            poses.popPose()
        }
        for (frame in state.quads) {
            poses.pushPose()
            poses.mulPose(frame.transform)
            collector.submitCustomGeometry(poses, RenderTypes.text(frame.texture)) { pose, buffer ->
                fun vertex(x: Float, y: Float, u: Float, v: Float) {
                    buffer.addVertex(pose, x, y, 0F).setColor(frame.color).setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(frame.light).setNormal(pose, 0F, 0F, -1F)
                }
                vertex(0F, 0F, 0F, 1F)
                vertex(0F, frame.height, 0F, 0F)
                vertex(frame.width, frame.height, 1F, 0F)
                vertex(frame.width, 0F, 1F, 1F)
            }
            poses.popPose()
        }
        for (frame in state.blocks) {
            poses.pushPose()
            poses.mulPose(frame.transform)
            if (frame.color == -1) {
                collector.submitBlockModel(poses, frame.type, frame.parts, frame.tintLayers, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)
            } else {
                val light = state.lightCoords
                collector.submitCustomGeometry(poses, frame.type) { pose, buffer ->
                    val quadState = com.mojang.blaze3d.vertex.QuadInstance()
                    quadState.setLightCoords(light)
                    quadState.setOverlayCoords(OverlayTexture.NO_OVERLAY)
                    for (part in frame.parts) for (direction in net.minecraft.core.Direction.entries + listOf(null)) {
                        for (quad in part.getQuads(direction)) {
                            val index = quad.materialInfo().tintIndex()
                            quadState.setColor(if (index in frame.tintLayers.indices) net.minecraft.util.ARGB.multiply(frame.color, frame.tintLayers[index]) else frame.color)
                            buffer.putBakedQuad(pose, quad, quadState)
                        }
                    }
                }
            }
            poses.popPose()
        }
        for (frame in state.items) {
            poses.pushPose()
            poses.mulPose(frame.transform)
            frame.item.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)
            poses.popPose()
        }
        for (frame in state.models) {
            poses.pushPose()
            poses.mulPose(frame.transform)
            collector.submitModel(frame.body.model, frame.body, poses, if (frame.cull) RenderTypes.entityCutoutCull(frame.texture) else RenderTypes.entityCutout(frame.texture),
                state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, 0, state.breakProgress)
            for ((index, layer) in frame.layers.withIndex()) {
                val light = if (layer.emissive) 15728880 else state.lightCoords
                if (layer.uOffset == 0F && layer.vOffset == 0F) {
                    collector.order(index + 1).submitModel(frame.body.model, frame.body, poses, layer.type,
                        light, OverlayTexture.NO_OVERLAY, layer.color, null, 0, state.breakProgress)
                } else {
                    collector.order(index + 1).submitCustomGeometry(poses, layer.type) { pose, buffer ->
                        val drawing = PoseStack(); drawing.last().set(pose)
                        frame.body.model.setupAnim(frame.body)
                        frame.body.model.renderToBuffer(drawing, ScrollingVertexConsumer(buffer, layer.uOffset, layer.vOffset), light,
                            OverlayTexture.NO_OVERLAY, layer.color)
                    }
                }
            }
            poses.popPose()
        }
    }
}
