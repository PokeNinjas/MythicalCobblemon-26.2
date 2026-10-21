/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.api.gui.renderSprite
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.toEulerXYZDegrees
import com.cobblemon.mod.common.util.toHex
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import com.cobblemon.mod.common.api.gui.blitk
import net.minecraft.client.Minecraft
import net.minecraft.util.LightCoordsUtil as LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan

enum class ProfileTransformType {
    PROFILE,
    NONE,
    POKEDEX,
    SUMMARY,
}

fun drawProfilePokemon(
    renderablePokemon: RenderablePokemon,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    profileTransformType: ProfileTransformType = ProfileTransformType.PROFILE,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) = drawProfilePokemon(
    species = renderablePokemon.species.resourceIdentifier,
    context = context,
    rotation = rotation,
    poseType = poseType,
    state = state.also { it.currentAspects = renderablePokemon.aspects },
    partialTicks = partialTicks,
    scale = scale,
    profileTransformType = profileTransformType,
    applyBaseScale = applyBaseScale,
    r = r,
    g = g,
    b = b,
    a = a,
    headYaw = headYaw,
    headPitch = headPitch,
    blockLight = blockLight,
    heldItem = renderablePokemon.heldItem
)

fun drawProfilePokemon(
    species: Identifier,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    profileTransformType: ProfileTransformType = ProfileTransformType.PROFILE,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13,
    heldItem: ItemStack = ItemStack.EMPTY
) {
    val sprite = VaryingModelRepository.getSprite(species, state, SpriteType.PROFILE)
    if (sprite != null) {
        blitk(context, sprite, -scale, 0, width = scale * 2, height = scale * 2, red = r, green = g, blue = b, alpha = a)
        return
    }
    val baseScale = PokemonSpecies.getByIdentifier(species)!!.getForm(state.currentAspects).baseScale
    NativeGuiModels.draw(context, species, state, partialTicks, poseType, baseScale, doQuirks,
        toHex(r, g, b, a), LightTexture.pack(blockLight, 0), headYaw, headPitch, item = heldItem) { model, modelPose ->
        // The native PIP renderer owns the final depth-axis flip.
        modelPose.scale(scale, scale, scale)
        if (profileTransformType == ProfileTransformType.NONE) {
            modelPose.translate(0F, 0F, -4F)
            if (applyBaseScale) modelPose.scale(baseScale, baseScale, 1F / baseScale)
        } else {
            val profileScale = when (profileTransformType) {
                ProfileTransformType.POKEDEX -> model.profilePokedexScale ?: model.profileScale
                ProfileTransformType.SUMMARY -> model.profileSummaryScale ?: model.profileScale
                else -> model.profileScale
            }
            val translation = when (profileTransformType) {
                ProfileTransformType.POKEDEX -> model.profilePokedexTranslation ?: model.profileTranslation
                ProfileTransformType.SUMMARY -> model.profileSummaryTranslation ?: model.profileTranslation
                else -> model.profileTranslation
            }
            modelPose.translate(translation.x, translation.y + 1.5 * profileScale, translation.z - 4.0)
            modelPose.scale(profileScale, profileScale, 1F / profileScale)
        }
        modelPose.mulPose(rotation)
    }
}

const val HEAD_YAW_FACTOR = 40f
const val HEAD_PITCH_FACTOR = 20f
const val SMOOTHING_FACTOR = 0.05f

fun calculateHeadYawAndPitch(
    xCoordinate: Float,
    yCoordinate: Float,
    rotation: Quaternionf,
    mouseX: Int,
    mouseY: Int,
    currentYaw: Float,
    currentPitch: Float,
    resetToCenter: Boolean,
): Pair<Float, Float> {
    val horizontalAngle = atan(((xCoordinate - mouseX) / 40.0f).toDouble()).toFloat()
    val verticalAngle = atan(((yCoordinate - mouseY) / 40.0f).toDouble()).toFloat()

    val eulerXYZRotationDegrees = rotation.toEulerXYZDegrees()
    val targetYaw = if (!resetToCenter) horizontalAngle * HEAD_YAW_FACTOR - eulerXYZRotationDegrees.y else 0f
    val targetPitch = if (!resetToCenter) -verticalAngle * HEAD_PITCH_FACTOR - eulerXYZRotationDegrees.x else 0f

    val newYaw = Mth.rotLerp(SMOOTHING_FACTOR, currentYaw, targetYaw)
    val newPitch = Mth.rotLerp(SMOOTHING_FACTOR, currentPitch, targetPitch)

    return Pair(newYaw, newPitch)
}