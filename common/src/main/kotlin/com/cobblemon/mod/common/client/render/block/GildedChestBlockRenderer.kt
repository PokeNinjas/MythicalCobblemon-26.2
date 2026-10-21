/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.GildedChestBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.blockentity.BlockEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class GildedChestBlockRenderer(context: BlockEntityRendererProvider.Context) : CobblemonBlockEntityRenderer<GildedChestBlockEntity>() {
    private val models = java.util.WeakHashMap<com.cobblemon.mod.common.client.render.models.blockbench.PosableModel, com.cobblemon.mod.common.client.render.models.blockbench.SnapshotBedrockModel>()
    override fun extractParts(entity: GildedChestBlockEntity, tickDelta: Float, matrices: PoseStack, renderState: CobblemonBlockRenderState) {
        val context = RenderContext().also {
            it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.BLOCK)
            it.put(RenderContext.DO_QUIRKS, true)
        }
        val aspects = emptySet<String>()
        val state = entity.posableState
        state.currentAspects = aspects
        state.updatePartialTicks(tickDelta)

        val poserId = entity.type.poserId

        val model = VaryingModelRepository.getPoser(poserId, state) as BlockEntityModel
        model.context = context
        val texture = VaryingModelRepository.getTexture(poserId, state)
        state.currentModel = model
        context.put(RenderContext.ASPECTS, aspects)
        context.put(RenderContext.TEXTURE, texture)
        context.put(RenderContext.SPECIES, poserId)
        context.put(RenderContext.POSABLE_STATE, state)

        matrices.pushPose()
        matrices.mulPose(Axis.ZP.rotationDegrees(180f))
        matrices.translate(-0.5, 0.0, 0.5)
        matrices.mulPose(Axis.YP.rotationDegrees(entity.blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()))
        matrices.mulPose(Axis.YP.rotationDegrees(180f))

        try {
        model.applyAnimations(
            entity = null,
            state = state,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = state.animationSeconds * 20
        )
        val drawing = models[model]?.takeIf { it.sourceRoot === model.rootPart }
            ?: com.cobblemon.mod.common.client.render.models.blockbench.SnapshotBedrockModel(model.rootPart as net.minecraft.client.model.geom.ModelPart).also { models[model] = it }
        model.locatorAccess.update(matrices, null, 1F, state.locatorStates, isRoot = true)
        renderState.models += BlockModelSubmission(drawing.capture(), texture, org.joml.Matrix4f(matrices.last().pose()),
            com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials.extractLayers(poserId, state))
        } finally {
            model.setDefault()
            context.pop()
        }
        matrices.popPose()

    }
}