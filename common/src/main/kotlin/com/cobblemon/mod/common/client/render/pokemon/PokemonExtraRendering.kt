/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBallDisplay
import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.BedrockModelSnapshot
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.SnapshotBedrockModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.MiscModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PlatformType
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.util.WeakHashMap
import kotlin.math.*

/** Additional models carry their own extracted transform and use the same isolated bone snapshots. */
data class PokemonExtraModel(val body: BedrockModelSnapshot, val texture: Identifier, val transform: Matrix4f)
data class PokemonRecallBeam(val transform: Matrix4f, val height: Float, val rotation: Float)

class PokemonExtraRendering {
    private val platformModels = mutableMapOf<Identifier, SnapshotBedrockModel>()
    private val ballModels = WeakHashMap<PosableModel, SnapshotBedrockModel>()
    private val ballContext = RenderContext()

    fun extractPlatform(entity: PokemonEntity, delegate: PokemonClientDelegate): PokemonExtraModel? {
        if (entity.platform == PlatformType.NONE || delegate.activeSendoutScale <= 0F) return null
        val (modelId, texture) = PlatformType.getModelWithTexture(entity.platform)
        val root = MiscModelRepository.modelOf(modelId) ?: return null
        val model = platformModels[modelId]?.takeIf { it.sourceRoot === root }
            ?: SnapshotBedrockModel(root).also { platformModels[modelId] = it }
        val pose = PoseStack()
        pose.mulPose(Axis.ZP.rotationDegrees(180F))
        pose.mulPose(Axis.YP.rotationDegrees(entity.entityData.get(PokemonEntity.SPAWN_DIRECTION)))
        val scale = delegate.activeSendoutScale
        pose.scale(scale, scale, scale)
        return PokemonExtraModel(model.capture(), texture, Matrix4f(pose.last().pose()))
    }

    fun extractTransition(entity: PokemonEntity, delegate: PokemonClientDelegate, partialTicks: Float): Pair<PokemonExtraModel?, PokemonRecallBeam?> {
        if (entity.beamMode == 0) return null to null
        val target = delegate.phaseTarget ?: return null to null
        val entityPosition = entity.getPosition(partialTicks)
        val targetPosition = sourcePosition(target, partialTicks)
        if (entity.beamMode == 1 && delegate.sendOutPosition == null) delegate.sendOutPosition = targetPosition
        val source = delegate.sendOutPosition ?: targetPosition
        val progress = delegate.ballOffset.coerceIn(0F, 1F)
        val travel = entityPosition.subtract(source).scale(progress.toDouble())
        // Time-based equivalent of the original rising throw arc; independent of display frame rate.
        val elapsed = if (delegate.ballStartTime > 0) ((System.currentTimeMillis() - delegate.ballStartTime) / 1000.0).coerceAtLeast(0.0) else 0.0
        val arc = min(elapsed, PokemonClientDelegate.POKEBALL_AIR_TIME.toDouble()) * 2.4 * (1.0 - progress)
        val offset = travel.add(0.0, arc, 0.0)
        delegate.sendOutOffset = offset
        var ball: PokemonExtraModel? = null
        if (entity.beamMode == 1 && !delegate.ballDone) {
            val caughtBall = CobblemonClient.storage.party.firstOrNull { it?.uuid == entity.pokemon.uuid }?.caughtBall ?: entity.pokemon.caughtBall
            val display = ClientBallDisplay(caughtBall, emptySet())
            display.updateAge(entity.tickCount)
            display.updatePartialTicks(partialTicks)
            val poser = VaryingModelRepository.getPoser(caughtBall.name, display)
            val texture = VaryingModelRepository.getTexture(caughtBall.name, display)
            val pose = PoseStack()
            val relative = source.add(offset).subtract(entityPosition)
            pose.translate(relative.x, relative.y, relative.z)
            val direction = source.subtract(entityPosition).normalize()
            pose.mulPose(Axis.YP.rotation((-atan2(direction.z, direction.x) + PI * 1.5).toFloat()))
            val beast = caughtBall.name.path.contains("beast")
            if (beast) {
                val facing = travel.normalize()
                pose.mulPose(Axis.XP.rotation(-atan2(facing.y, sqrt(facing.x * facing.x + facing.z * facing.z)).toFloat()))
            }
            pose.scale(0.7F, -0.7F, -0.7F)
            ballContext.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
            ballContext.put(RenderContext.ASPECTS, display.aspects)
            ballContext.put(RenderContext.POSABLE_STATE, display)
            ballContext.put(RenderContext.TEXTURE, texture)
            poser.context = ballContext
            try {
                if (progress == 1F) poser.poses["open"]?.let { poser.moveToPose(display, it) }
                else {
                    pose.translate(0.0, -0.2, 0.0)
                    val rotation = 360F * ceil(source.distanceTo(entityPosition) / 4.0).toInt() * progress
                    pose.mulPose((if (beast) Axis.ZN else Axis.XN).rotationDegrees(rotation))
                    pose.translate(0.0, 0.2, 0.0)
                }
                poser.applyAnimations(null, display, 0F, 0F, display.animationSeconds * 20F, 0F, 0F)
                val model = ballModels[poser]?.takeIf { it.sourceRoot === poser.rootPart }
                    ?: SnapshotBedrockModel(poser.rootPart as ModelPart).also { ballModels[poser] = it }
                ball = PokemonExtraModel(model.capture(), texture, Matrix4f(pose.last().pose()))
            } finally {
                poser.setDefault()
                ballContext.pop()
            }
        }
        if (entity.beamMode != 3) return ball to null
        val center = entityPosition.add(0.0, entity.bbHeight / 2.0 * delegate.activeSendoutScale, 0.0)
        if (source.distanceTo(center) > 20.0) return ball to null
        val beamSource = source.add(0.0, travel.y * (1.0 + PokemonRenderer.ease(progress.toDouble())), 0.0)
        val direction = center.subtract(beamSource)
        val distance = direction.length()
        if (distance < 1E-6) return ball to null
        val elapsedBeam = delegate.secondsSinceBeamEffectStarted
        val extend = PokemonClientDelegate.BEAM_EXTEND_TIME
        val shrink = PokemonClientDelegate.BEAM_SHRINK_TIME
        val ratio = when {
            elapsedBeam < extend -> elapsedBeam / extend
            elapsedBeam > extend + shrink -> 1F - ((elapsedBeam - extend - shrink) / extend).coerceAtMost(1F)
            else -> 1F
        }.coerceIn(0F, 1F)
        if (ratio <= 0F) return ball to null
        val pose = PoseStack()
        val relative = beamSource.subtract(entityPosition)
        pose.translate(relative.x, relative.y, relative.z)
        val normalized = direction.scale(1.0 / distance)
        // rotationTo also handles a beam pointing exactly down, unlike the old cross-product quaternion.
        pose.mulPose(Quaternionf().rotationTo(0F, 1F, 0F, normalized.x.toFloat(), normalized.y.toFloat(), normalized.z.toFloat()))
        return ball to PokemonRecallBeam(Matrix4f(pose.last().pose()), (distance * ratio).toFloat(),
            (Math.floorMod(entity.level().gameTime, 40) + partialTicks) * 2.25F - 45F)
    }

    private fun sourcePosition(target: Entity, partialTicks: Float): Vec3 {
        if (target is NPCEntity) {
            val delegate = target.delegate as NPCClientDelegate
            return delegate.locatorStates["beam"]?.getOrigin()
                ?: target.getPosition(partialTicks).add(0.0, target.bbHeight / 2.0 * target.renderScale, 0.0)
        }
        if (target is PosableEntity) {
            return (target.delegate as PosableState).locatorStates["beam"]?.getOrigin() ?: target.getPosition(partialTicks)
        }
        val local = target.uuid == Minecraft.getInstance().player?.uuid
        val rotation = if (local) PI / 2.0 else PI / 2.0 - Math.toRadians((target.visualRotationYInDegrees - target.xRot).toDouble())
        val side = target.lookAngle.yRot(rotation.toFloat()).multiply(1.0, 0.0, 1.0).normalize()
        return target.getEyePosition(partialTicks).subtract(0.0, if (local) 0.4 else 0.7, 0.0).subtract(side.scale(if (local) 0.3 else 0.4))
    }

    fun submitModel(model: PokemonExtraModel?, poses: PoseStack, collector: SubmitNodeCollector, light: Int) {
        if (model == null) return
        poses.pushPose()
        poses.mulPose(model.transform)
        collector.submitModel(model.body.model, model.body, poses, RenderTypes.entityCutoutCull(model.texture), light,
            OverlayTexture.NO_OVERLAY, -1, null, 0, null)
        poses.popPose()
    }

    fun submitBeam(beam: PokemonRecallBeam?, poses: PoseStack, collector: SubmitNodeCollector) {
        if (beam == null) return
        poses.pushPose()
        poses.mulPose(beam.transform)
        poses.pushPose()
        poses.mulPose(Axis.YP.rotationDegrees(beam.rotation))
        submitTube(poses, collector, RenderTypes.beaconBeam(CobblemonResources.PHASE_BEAM, false), beam.height,
            floatArrayOf(0F, .03F, .03F, 0F, 0F, -.03F, -.03F, 0F), 1F)
        poses.popPose()
        submitTube(poses, collector, RenderTypes.beaconBeam(CobblemonResources.PHASE_BEAM, true), beam.height,
            floatArrayOf(-.07F, -.07F, .07F, -.07F, .07F, .07F, -.07F, .07F), .4F)
        poses.popPose()
    }

    private fun submitTube(poses: PoseStack, collector: SubmitNodeCollector, type: RenderType, height: Float, corners: FloatArray, alpha: Float) {
        collector.order(2).submitCustomGeometry(poses, type) { pose, buffer ->
            for (i in 0..3) {
                val j = (i + 1) % 4
                fun vertex(corner: Int, y: Float, u: Float, v: Float) {
                    buffer.addVertex(pose.pose(), corners[corner * 2], y, corners[corner * 2 + 1])
                        .setColor(1F, .1F, .1F, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(15728880).setNormal(pose, 0F, 1F, 0F)
                }
                vertex(i, height, 1F, 0F); vertex(i, 0F, 1F, 1F)
                vertex(j, 0F, 0F, 1F); vertex(j, height, 0F, 0F)
            }
        }
    }
}
