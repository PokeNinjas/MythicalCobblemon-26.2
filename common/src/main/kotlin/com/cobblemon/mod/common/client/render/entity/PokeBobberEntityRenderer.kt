/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.entity

import com.cobblemon.mod.common.api.fishing.PokeRods
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.Vec3
import net.minecraft.world.item.ItemDisplayContext
import org.joml.Matrix4f
import java.util.WeakHashMap
import kotlin.math.pow

class PokeBobberRenderState : EntityRenderState() {
    val ball = ItemStackRenderState()
    val bait = ItemStackRenderState()
    var bodyTransform = Matrix4f()
    var baitTransform = Matrix4f()
    var lineOffset = Vec3.ZERO
    var lineColor = -16777216
    var ready = false
}

class PokeBobberEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<PokeRodFishingBobberEntity, PokeBobberRenderState>(context) {
    private val itemResolver = context.itemModelResolver
    private class Visual(seed: Long) {
        private val random = java.util.Random(seed)
        val yaw = -180F + random.nextFloat() * 360F
        var pitch = -40F + random.nextFloat() * 80F
        var spin = 0F
        var lastAge = Float.NaN
    }
    private val visuals = WeakHashMap<PokeRodFishingBobberEntity, Visual>()
    override fun createRenderState() = PokeBobberRenderState()
    // The line can be visible while the bobber itself lies outside the frustum, as in vanilla.
    override fun affectedByCulling(entity: PokeRodFishingBobberEntity) = false

    override fun extractRenderState(entity: PokeRodFishingBobberEntity, state: PokeBobberRenderState, partial: Float) {
        super.extractRenderState(entity, state, partial)
        state.ready = false
        state.ball.clear(); state.bait.clear()
        val player = entity.playerOwner ?: return
        val rodId = Identifier.tryParse(entity.entityData.get(PokeRodFishingBobberEntity.POKEROD_ID)) ?: return
        val rod = PokeRods.getPokeRod(rodId) ?: return
        val ball = PokeBalls.getPokeBall(rod.pokeBallId)?.item ?: return
        val bait = entity.entityData.get(PokeRodFishingBobberEntity.POKEBOBBER_BAIT)
        val visual = visuals.getOrPut(entity) { Visual(entity.uuid.mostSignificantBits xor entity.uuid.leastSignificantBits) }
        val age = entity.tickCount + partial
        val elapsed = if (visual.lastAge.isFinite()) (age - visual.lastAge).coerceAtLeast(0F) else 0F
        if (!entity.onGround()) {
            val spinAge = age.coerceAtMost(219.999F)
            val slowdown = 1F + spinAge / if (entity.inOpenWater) 15F else 100F
            if (age < 220F || !visual.lastAge.isFinite()) visual.spin = spinAge * 20F / slowdown % 360F
            if (entity.state == PokeRodFishingBobberEntity.State.BOBBING) {
                // Preserve the original 60-FPS settling rate without advancing once per render pass.
                visual.pitch *= .96.pow((elapsed * 3F).toDouble()).toFloat()
            }
        }
        visual.lastAge = age
        val poses = PoseStack()
        poses.mulPose(Axis.XN.rotationDegrees(visual.pitch))
        poses.mulPose(Axis.YN.rotationDegrees(visual.yaw))
        poses.mulPose(Axis.YN.rotationDegrees(visual.spin))
        poses.scale(.7F, .7F, .7F)
        state.bodyTransform = Matrix4f(poses.last().pose())
        itemResolver.updateForNonLiving(state.ball, ball.defaultInstance, ItemDisplayContext.GROUND, entity)
        poses.scale(.8F, .8F, .8F)
        val berryOffset = when (BuiltInRegistries.ITEM.getKey(bait.item).toString()) {
            "cobblemon:wacan_berry", "cobblemon:cheri_berry" -> .03
            else -> 0.0
        }
        poses.translate(.20 + berryOffset, -.5, 0.0)
        state.baitTransform = Matrix4f(poses.last().pose())
        itemResolver.updateForNonLiving(state.bait, bait, ItemDisplayContext.GROUND, entity)

        var arm = if (player.mainArm == HumanoidArm.RIGHT) 1 else -1
        val main = player.mainHandItem.item
        if (main !is PokerodItem || main.pokeRodId != rodId) arm = -arm
        val minecraft = Minecraft.getInstance()
        val swing = kotlin.math.sin(Mth.sqrt(player.getAttackAnim(partial)) * Mth.PI)
        val camera = entityRenderDispatcher.camera
        val hand = if (camera != null && entityRenderDispatcher.options.cameraType.isFirstPerson && player === minecraft.player) {
            val fov = entityRenderDispatcher.options.fov().get().toFloat()
            val offset = camera.getNearPlane(fov).getPointOnPlane(arm * .525F, -.1F)
                .scale(960.0 / fov).yRot(swing * .5F).xRot(-swing * .7F)
            player.getEyePosition(partial).add(offset)
        } else {
            val yaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD
            val horizontal = arm * .35 * player.scale
            val forward = .8 * player.scale
            player.getEyePosition(partial).add(-kotlin.math.cos(yaw) * horizontal - kotlin.math.sin(yaw) * forward,
                (if (player.isCrouching) -.1875 else 0.0) - .45 * player.scale,
                -kotlin.math.sin(yaw) * horizontal + kotlin.math.cos(yaw) * forward)
        }
        state.lineOffset = hand.subtract(entity.getPosition(partial).add(0.0, .25, 0.0))
        state.lineColor = ARGB.opaque(Integer.decode(rod.lineColor))
        state.ready = true
    }

    override fun submit(state: PokeBobberRenderState, poses: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        if (!state.ready) return
        poses.pushPose()
        poses.mulPose(state.bodyTransform)
        state.ball.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor)
        collector.submitCustomGeometry(poses, HOOK) { pose, buffer ->
            // Both sides use the native no-cull cutout material.
            vertex(buffer, pose, state.lightCoords, .92F, .125F, 0F, 1F)
            vertex(buffer, pose, state.lightCoords, .17F, .125F, 1F, 1F)
            vertex(buffer, pose, state.lightCoords, .17F, .875F, 1F, 0F)
            vertex(buffer, pose, state.lightCoords, .92F, .875F, 0F, 0F)
        }
        poses.popPose()
        poses.pushPose()
        poses.mulPose(state.baitTransform)
        state.bait.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor)
        poses.popPose()
        val width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth
        collector.submitCustomGeometry(poses, RenderTypes.lines()) { pose, buffer ->
            FishingLineGeometry.emit(state.lineOffset) { point, direction ->
                buffer.addVertex(pose, point.x.toFloat(), point.y.toFloat(), point.z.toFloat()).setColor(state.lineColor)
                    .setNormal(pose, direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat()).setLineWidth(width)
            }
        }
        super.submit(state, poses, collector, camera)
    }

    companion object {
        private val HOOK = RenderTypes.entityCutout(cobblemonResource("textures/item/fishing/bobber_hook.png"))
        private fun vertex(buffer: VertexConsumer, pose: PoseStack.Pose, light: Int, x: Float, y: Float, u: Float, v: Float) {
            buffer.addVertex(pose, x - .5F, y - .5F, 0F).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0F, 1F, 0F)
        }
    }
}
