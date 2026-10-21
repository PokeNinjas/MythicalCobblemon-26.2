/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.riding.RidingAnimation
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.AnimationUtils
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import org.joml.Matrix4f
import java.util.WeakHashMap

/** Evaluate gameplay/animation queries once; native model draws consume only the captured pose. */
object MountedPlayerRenderer {
    private val parts = WeakHashMap<AbstractClientPlayer, Map<String, ModelPart>>()
    val defaultAnimations = listOf(RidingAnimation(fileName = "player", animationName = "animation.player.default_ride_pose"))
    const val RIDING_SITTING_OFFSET = -10.0

    @JvmStatic fun extract(player: AbstractClientPlayer, state: AvatarRenderState): MountedRiderPose? {
        val pokemon = player.vehicle as? PokemonEntity ?: return null
        val seat = pokemon.getSeatForPassenger(player) ?: return null
        val delegate = pokemon.delegate as? PokemonClientDelegate ?: return null
        val rotateHead = pokemon.ifRidingAvailableSupply(false) { behaviour, settings, ridingState ->
            behaviour.shouldRotateRiderHead(settings, ridingState, pokemon)
        }
        val bones = parts.getOrPut(player) {
            listOf("body", "head", "hat", "torso", "arm_left", "arm_right", "leg_left", "leg_right")
                .associateWith { ModelPart(emptyList(), emptyMap()) }
        }
        bones.values.forEach(ModelPart::resetPose)
        delegate.runtime.environment.setSimpleVariable("head_yaw", DoubleValue(if (rotateHead) state.yRot.toDouble() else 0.0))
        delegate.runtime.environment.setSimpleVariable("head_pitch", DoubleValue(if (rotateHead) state.xRot.toDouble() else 0.0))
        val animations = seat.poseAnimations?.firstOrNull {
            it.poseTypes.isEmpty() || it.poseTypes.contains(pokemon.getCurrentPoseType())
        }?.animations ?: defaultAnimations
        for (animation in animations) {
            BedrockAnimationRepository.getAnimationOrNull(animation.fileName, animation.animationName)?.run(
                context = null, relevantPartsByName = bones, rootPart = null, state = delegate,
                animationSeconds = delegate.animationSeconds, limbSwing = state.walkAnimationPos,
                limbSwingAmount = state.walkAnimationSpeed, ageInTicks = state.ageInTicks, intensity = 1F)
        }
        val root = bones.getValue("body")
        val transform = PoseStack()
        transform.translate(-root.x / 16.0, (root.y + RIDING_SITTING_OFFSET) / 16.0, root.z / 16.0)
        transform.mulPose(Axis.ZP.rotation(root.zRot))
        transform.mulPose(Axis.YP.rotation(root.yRot))
        transform.mulPose(Axis.XP.rotation(root.xRot))
        return MountedRiderPose(bones.filterKeys { it != "body" }.mapValues { RiderBonePose.capture(it.value) }, Matrix4f(transform.last().pose()))
    }

    @JvmStatic fun apply(model: HumanoidModel<HumanoidRenderState>, state: HumanoidRenderState, pose: MountedRiderPose) {
        model.resetPose()
        val bones = mapOf("head" to model.head, "hat" to model.hat, "torso" to model.body,
            "arm_left" to model.leftArm, "arm_right" to model.rightArm, "leg_left" to model.leftLeg, "leg_right" to model.rightLeg)
        bones.forEach { (name, part) -> pose.bones[name]?.apply(part) }
        // Vanilla's item use and attack poses still apply after the custom seat animation.
        val rightHanded = state.mainArm == HumanoidArm.RIGHT
        if (state.isUsingItem) {
            if ((state.useItemHand == InteractionHand.MAIN_HAND) == rightHanded) {
                model.poseRightArm(state)
                if (!state.rightArmPose.affectsOffhandPose()) model.poseLeftArm(state)
            } else {
                model.poseLeftArm(state)
                if (!state.leftArmPose.affectsOffhandPose()) model.poseRightArm(state)
            }
        } else {
            val twoHandedOffhand = if (rightHanded) state.leftArmPose.isTwoHanded else state.rightArmPose.isTwoHanded
            if (rightHanded != twoHandedOffhand) {
                model.poseLeftArm(state)
                if (!state.leftArmPose.affectsOffhandPose()) model.poseRightArm(state)
            } else {
                model.poseRightArm(state)
                if (!state.rightArmPose.affectsOffhandPose()) model.poseLeftArm(state)
            }
        }
        model.setupAttackAnimation(state)
        if (state.rightArmPose != HumanoidModel.ArmPose.SPYGLASS) AnimationUtils.bobModelPart(model.rightArm, state.ageInTicks, 1F)
        if (state.leftArmPose != HumanoidModel.ArmPose.SPYGLASS) AnimationUtils.bobModelPart(model.leftArm, state.ageInTicks, -1F)
        // Hat and clothing are native children in 26.2; they inherit parent poses automatically.
    }
}
