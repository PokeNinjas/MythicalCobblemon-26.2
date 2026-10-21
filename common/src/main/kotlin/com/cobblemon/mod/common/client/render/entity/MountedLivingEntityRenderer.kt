/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.entity

import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.player.MountedLivingRenderState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

object MountedLivingEntityRenderer {
    @JvmStatic fun extract(entity: LivingEntity, pokemon: PokemonEntity, partial: Float, bodyRot: Float, state: MountedLivingRenderState): Matrix4f? {
        val delegate = pokemon.delegate as? PokemonClientDelegate ?: return null
        MountedPokemonAnimationRenderController.setup(pokemon, partial)
        val locator = delegate.getSeatLocator(entity)?.let { delegate.locatorStates[it] } ?: return null
        if (!locator.matrix.isFinite) return null
        // Sneasler's basket deliberately collapses the seat bone to hide its passenger.
        // Preserve that visibility signal without extracting a NaN rotation from zero scale.
        val seat = locator.matrix
        val collapsed = Vector3f(seat.m00(), seat.m01(), seat.m02()).lengthSquared() <= 1E-12F &&
            Vector3f(seat.m10(), seat.m11(), seat.m12()).lengthSquared() <= 1E-12F &&
            Vector3f(seat.m20(), seat.m21(), seat.m22()).lengthSquared() <= 1E-12F
        if (collapsed) {
            state.riderHidden = true
            return null
        }
        if (kotlin.math.abs(seat.determinant()) < 1E-8F) return null
        val poses = PoseStack()
        val matrix = poses.last().pose()
        val relative = entity.getPosition(partial).subtract(pokemon.getPosition(partial))
        matrix.translate(relative.toVector3f().negate())
        matrix.translate(locator.matrix.getTranslation(Vector3f()))
        matrix.rotate(locator.matrix.getUnnormalizedRotation(Quaternionf()))
        matrix.rotate(Axis.YP.rotationDegrees(180F + bodyRot))
        matrix.translate(0F, -entity.bbHeight / 2F + .35F, 0F)
        return Matrix4f(matrix)
    }
}
