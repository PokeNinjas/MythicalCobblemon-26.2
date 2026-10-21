/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.DiscShelfBlockEntity
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.client.render.color.TechnicalMachineItemColorProvider
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemStack
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import org.joml.Matrix4f

class DiscShelfBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) :
        CobblemonBlockEntityRenderer<DiscShelfBlockEntity>() {

    private val slotWidthPixels = 6
    private val slotHeightPixels = 2
    private val textureSize = 16f

    private val leftMargin = 1f
    private val rightMargin = 1f
    private val topMargin = 1f
    private val interRowSpacing = 0f
    private val tmOverlayDepthOffset = -0.0002f
    private val slotBrightnessMultiplier = 0.65f

    private val tmBaseTexture = cobblemonResource("textures/block/disc_shelf/technical_machine_base.png")
    private val tmOverlayTexture = cobblemonResource("textures/block/disc_shelf/technical_machine_overlay.png")
    private val musicDiscUnknownTexture = cobblemonResource("textures/block/disc_shelf/music_disc_unknown.png")

    override fun extractParts(entity: DiscShelfBlockEntity, partialTicks: Float, poseStack: PoseStack, state: CobblemonBlockRenderState) {
        val facing = entity.blockState.getValue(HorizontalDirectionalBlock.FACING)
        val rotation = when (facing) {
            Direction.NORTH -> 0f
            Direction.SOUTH -> 180f
            Direction.WEST -> 90f
            Direction.EAST -> -90f
            else -> 0f
        }

        val lightGetter = entity.level as? BlockAndTintGetter
        val packedLight = lightGetter?.let {
            net.minecraft.util.LightCoordsUtil.getLightCoords(it, entity.blockPos.relative(facing))
        } ?: if (state.lightCoords != 0) state.lightCoords else 0xF000F0

        poseStack.pushPose()
        poseStack.translate(0.5, 0.5, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation))
        poseStack.translate(-0.5, -0.5, -0.5)

        val slotWidth = slotWidthPixels / textureSize
        val slotHeight = slotHeightPixels / textureSize
        val verticalSpacing = (slotHeightPixels + interRowSpacing) / textureSize
        val startY = topMargin / textureSize

        for ((index, item) in entity.items.withIndex()) {
            if (!item.isEmpty) {
                val row = index / 2
                val col = index % 2
                val visualCol = 1 - col

                val isLeftColumn = visualCol == 0
                val posX = if (isLeftColumn)
                    leftMargin / textureSize
                else
                    (16f - rightMargin - slotWidthPixels) / textureSize

                val posY = startY + (6 - row) * verticalSpacing

                poseStack.pushPose()
                poseStack.translate(posX, posY, -0.001f)

                if (item.item == CobblemonItems.TECHNICAL_MACHINE) {
                    val baseColor = TechnicalMachineItemColorProvider.getColor(item, 0)
                    val overlayColor = TechnicalMachineItemColorProvider.getColor(item, 1)

                    renderSlotQuad(
                            poseStack,
                            state,
                            tmBaseTexture,
                            baseColor,
                            packedLight,
                            slotWidth,
                            slotHeight,
                            tint = true,
                            useTextRenderType = true
                    )

                    poseStack.pushPose()
                    poseStack.translate(0.0, 0.0, tmOverlayDepthOffset.toDouble())
                    renderSlotQuad(
                            poseStack,
                            state,
                            tmOverlayTexture,
                            overlayColor,
                            packedLight,
                            slotWidth,
                            slotHeight,
                            tint = true,
                            useTextRenderType = true
                    )
                    poseStack.popPose()
                } else {
                    val tex = getItemTexture(item)
                    renderSlotQuad(
                            poseStack,
                            state,
                            tex,
                            0xFFFFFF,
                            packedLight,
                            slotWidth,
                            slotHeight,
                            tint = false,
                            useTextRenderType = true
                    )
                }

                poseStack.popPose()
            }
        }

        poseStack.popPose()
    }

    private fun renderSlotQuad(
            poseStack: PoseStack,
            state: CobblemonBlockRenderState,
            texture: Identifier,
            color: Int,
            light: Int,
            width: Float,
            height: Float,
            tint: Boolean,
            useTextRenderType: Boolean
    ) {
        val (rBase, gBase, bBase, a) = if (tint) {
            listOf(
                    ARGB.red(color) / 255f,
                    ARGB.green(color) / 255f,
                    ARGB.blue(color) / 255f,
                    1.0f
            )
        } else listOf(1f, 1f, 1f, 1f)
        val r = (rBase * slotBrightnessMultiplier).coerceIn(0f, 1f)
        val g = (gBase * slotBrightnessMultiplier).coerceIn(0f, 1f)
        val b = (bBase * slotBrightnessMultiplier).coerceIn(0f, 1f)

        state.quads += BlockQuadSubmission(Matrix4f(poseStack.last().pose()), texture,
            ARGB.colorFromFloat(a, r, g, b), light, width, height)
    }

    private fun getItemTexture(itemStack: ItemStack): Identifier {
        val id = BuiltInRegistries.ITEM.getKey(itemStack.item)
        val resource = cobblemonResource("textures/block/disc_shelf/${id.path}.png")

        val resourceManager = Minecraft.getInstance().resourceManager
        if (resourceManager.getResource(resource).isEmpty) {
            return musicDiscUnknownTexture
        }

        return resource
    }
}
