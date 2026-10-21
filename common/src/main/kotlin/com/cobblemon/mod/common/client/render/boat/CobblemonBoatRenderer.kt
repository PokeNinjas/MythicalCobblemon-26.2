/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.boat

import com.cobblemon.mod.common.entity.boat.CobblemonBoatEntity
import com.cobblemon.mod.common.entity.boat.CobblemonBoatType
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.Model
import net.minecraft.client.model.`object`.boat.BoatModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.BoatRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.joml.Quaternionf

class CobblemonBoatRenderState : BoatRenderState() {
    var boatType = CobblemonBoatType.APRICORN
}

class CobblemonBoatRenderer(ctx: EntityRendererProvider.Context, hasChest: Boolean) :
    EntityRenderer<CobblemonBoatEntity, CobblemonBoatRenderState>(ctx) {
    private val boats = CobblemonBoatType.entries.associateWith { type ->
        val layer = createBoatModelLayer(type, hasChest)
        layer.model().withPath { "textures/entity/$it.png" } to BoatModel(ctx.bakeLayer(layer))
    }
    private val waterPatch = Model.Simple(ctx.bakeLayer(ModelLayers.BOAT_WATER_PATCH)) { RenderTypes.waterMask() }
    init { shadowRadius = .8F }
    override fun createRenderState() = CobblemonBoatRenderState()
    override fun extractRenderState(entity: CobblemonBoatEntity, state: CobblemonBoatRenderState, partial: Float) {
        super.extractRenderState(entity, state, partial)
        state.boatType = entity.boatType
        state.yRot = entity.getYRot(partial)
        state.hurtTime = entity.hurtTime - partial
        state.hurtDir = entity.hurtDir
        state.damageTime = (entity.damage - partial).coerceAtLeast(0F)
        state.bubbleAngle = entity.getBubbleAngle(partial)
        state.isUnderWater = entity.isUnderWater
        state.rowingTimeLeft = entity.getRowingTime(0, partial)
        state.rowingTimeRight = entity.getRowingTime(1, partial)
    }
    override fun submit(state: CobblemonBoatRenderState, poses: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        val (texture, model) = boats.getValue(state.boatType)
        poses.pushPose()
        poses.translate(0F, .375F, 0F)
        poses.mulPose(Axis.YP.rotationDegrees(180F - state.yRot))
        if (state.hurtTime > 0F) poses.mulPose(Axis.XP.rotationDegrees(kotlin.math.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10F * state.hurtDir))
        if (!state.isUnderWater && !Mth.equal(state.bubbleAngle, 0F)) {
            poses.mulPose(Quaternionf().setAngleAxis(state.bubbleAngle * Mth.DEG_TO_RAD, 1F, 0F, 1F))
        }
        poses.scale(-1F, -1F, 1F)
        poses.mulPose(Axis.YP.rotationDegrees(90F))
        collector.submitModel(model, state, poses, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null)
        if (!state.isUnderWater) collector.submitModel(waterPatch, net.minecraft.util.Unit.INSTANCE, poses,
            texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null)
        poses.popPose()
        super.submit(state, poses, collector, camera)
    }
    companion object {
        internal fun createBoatModelLayer(type: CobblemonBoatType, hasChest: Boolean): ModelLayerLocation =
            ModelLayerLocation(cobblemonResource("${if (hasChest) "chest_boat" else "boat"}/${type.name.lowercase()}"), "main")
    }
}
