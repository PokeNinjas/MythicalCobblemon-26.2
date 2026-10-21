/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.block.DisplayCaseBlock
import com.cobblemon.mod.common.block.entity.DisplayCaseBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.item.PokedexItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.*
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.level.Level

class DisplayCaseRenderer(ctx: BlockEntityRendererProvider.Context) : CobblemonBlockEntityRenderer<DisplayCaseBlockEntity>() {
    val context = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
    }
    val coinPouchStack: ItemStack by lazy { ItemStack(
        CobblemonItems.RELIC_COIN_POUCH
    ).also { it.set(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(1F), emptyList(), emptyList(), emptyList())) } }
    override fun extractParts(entity: DisplayCaseBlockEntity, tickDelta: Float, matrices: PoseStack, state: CobblemonBlockRenderState) {
        val stack: ItemStack = if (entity.getStack().`is`(CobblemonItems.RELIC_COIN_POUCH)) {
            coinPouchStack
        } else {
            entity.getStack()
        }
        val world = entity.level ?: return
        val posType = getPositioningType(stack, world)
        val blockState = if (entity.level != null) entity.blockState
            else (CobblemonBlocks.DISPLAY_CASE.defaultBlockState().setValue(DisplayCaseBlock.ITEM_DIRECTION, Direction.NORTH))
        val yRot = if (posType == PositioningType.ITEM_MODEL) blockState.getValue(DisplayCaseBlock.ITEM_DIRECTION).opposite.toYRot()
            else blockState.getValue(DisplayCaseBlock.ITEM_DIRECTION).toYRot()

        matrices.pushPose()
        matrices.translate(0.5f, 0.4f, 0.5f)

        matrices.scale(posType.scaleX, posType.scaleY, posType.scaleZ)
        matrices.translate(posType.transX, posType.transY, posType.transZ)

        matrices.mulPose(Axis.YP.rotationDegrees(-yRot))
        matrices.mulPose(Axis.YP.rotationDegrees(posType.rotY))

        state.addItem(stack, ItemDisplayContext.GROUND, matrices, entity.level)

        matrices.popPose()

    }
    companion object {
        private val mobHeads = listOf<Item>(
            Items.SKELETON_SKULL,
            Items.WITHER_SKELETON_SKULL,
            Items.ZOMBIE_HEAD,
            Items.PIGLIN_HEAD,
            Items.PLAYER_HEAD,
            Items.DRAGON_HEAD,
            Items.CREEPER_HEAD
        )

        private fun usesBlockLighting(stack: ItemStack, world: Level): Boolean {
            val renderState = net.minecraft.client.renderer.item.ItemStackRenderState()
            Minecraft.getInstance().itemModelResolver.updateForTopItem(renderState, stack, ItemDisplayContext.GUI,
                world as? net.minecraft.client.multiplayer.ClientLevel, null, 0)
            return renderState.usesBlockLight()
        }

        private fun getPositioningType(stack: ItemStack, world: Level) = when {
            mobHeads.contains(stack.item) -> PositioningType.MOB_HEAD
            stack.item is BedItem -> PositioningType.BED
            stack.item is BannerItem -> PositioningType.BANNER
            stack.`is`(CobblemonItemTags.POKE_BALLS) -> PositioningType.POKE_BALL
            stack.`is`(CobblemonItemTags.PLAQUES) -> PositioningType.ITEM_MODEL
            stack.item is PokedexItem -> PositioningType.POKEDEX
            stack.item == CobblemonItems.RELIC_COIN_POUCH -> PositioningType.COIN_POUCH
            stack.item == CobblemonItems.PASTURE -> PositioningType.PASTURE
            //stack.item == CobblemonItems.POKEMON_MODEL -> PositioningType.ITEM_MODEL
            stack.item == Items.SHIELD -> PositioningType.SHIELD
            stack.item == Items.DECORATED_POT -> PositioningType.MOB_HEAD
            usesBlockLighting(stack, world) -> PositioningType.BLOCK_MODEL
            else -> PositioningType.ITEM_MODEL
        }
    }

    private enum class PositioningType(
        val scaleX: Float, val scaleY: Float, val scaleZ: Float,
        val transX: Float, val transY: Float, val transZ: Float,
        val rotY: Float = 0f
    ) {
        POKE_BALL(1f, 1f, 1f, 0f, 0.04f, 0f),
        POKEDEX(1f, 1f, 1f, 0f, 0.04f, 0f, 180f),
        BLOCK_MODEL(1f, 1f, 1f, 0f, -0.15f, 0f),
        ITEM_MODEL(1f, 1f, 1f, 0f, 0.04f, 0f),
        BED(1f, 1f, 1f, 0f, -0.02f, 0f),
        BANNER(1f, 1f, 1f, 0f, -0.02f, 0f, 180f),
        MOB_HEAD(1f, 1f, 1f, 0f, -0.025f, 0f, 180f),
        SHIELD(1f, 1f, 1f, 0f, -0.045f, 0f, 180f),
        PASTURE(1f, 1f, 1f, 0f, 0.0375f, 0f),
        COIN_POUCH(1f, 1f, 1f, 0f, 0.415f, 0f)
    }
}