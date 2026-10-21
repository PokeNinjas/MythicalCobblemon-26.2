/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.mulch.MulchVariant
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import com.cobblemon.mod.common.client.render.atlas.CobblemonAtlases.BERRY_SPRITE_ATLAS
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

class BerryBlockRenderer(context: BlockEntityRendererProvider.Context) : CobblemonBlockEntityRenderer<BerryBlockEntity>() {
    val mulchModels = mutableMapOf(
        MulchVariant.COARSE to CobblemonBakingOverrides.COARSE_MULCH,
        MulchVariant.GROWTH to CobblemonBakingOverrides.GROWTH_MULCH,
        MulchVariant.HUMID to CobblemonBakingOverrides.HUMID_MULCH,
        MulchVariant.LOAMY to CobblemonBakingOverrides.LOAMY_MULCH,
        MulchVariant.PEAT to CobblemonBakingOverrides.PEAT_MULCH,
        MulchVariant.RICH to CobblemonBakingOverrides.RICH_MULCH,
        MulchVariant.SANDY to CobblemonBakingOverrides.SANDY_MULCH,
        MulchVariant.SURPRISE to CobblemonBakingOverrides.SURPRISE_MULCH,
        MulchVariant.NONE to null
    )



    override fun extractParts(entity: BerryBlockEntity, partialTicks: Float, poses: PoseStack, state: CobblemonBlockRenderState) {
        val age = entity.blockState.getValue(BerryBlock.AGE)
        if (age == 0) {
            entity.berry()?.let { berry ->
                val placement = berry.stageOnePositioning
                addBerry(berry.fruitModelIdentifier, placement.position, placement.rotation, poses, state)
            }
        } else if (age > BerryBlock.MATURE_AGE) {
            for ((berry, growth) in entity.berryAndGrowthPoint()) {
                val id = if (age == BerryBlock.FLOWER_AGE) berry.flowerModelIdentifier else berry.fruitModelIdentifier
                addBerry(id, growth.position, growth.rotation, poses, state)
            }
        }
        mulchModels[entity.mulchVariant]?.let { state.addExtraModel(it, poses) }
    }

    private fun addBerry(id: Identifier, position: Vec3, rotation: Vec3, poses: PoseStack, state: CobblemonBlockRenderState) {
        val model = BerryModelRepository.modelOf(id) ?: return
        model.setPos(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
        model.setRotation(Math.toRadians(180 - rotation.x).toFloat(), Math.toRadians(180 + rotation.y).toFloat(), Math.toRadians(rotation.z).toFloat())
        val frame = BerryModelRepository.capture(id) ?: return
        state.models += BlockModelSubmission(frame, BERRY_SPRITE_ATLAS.atlasId, Matrix4f(poses.last().pose()), emptyList(), cull = false)
    }
}
