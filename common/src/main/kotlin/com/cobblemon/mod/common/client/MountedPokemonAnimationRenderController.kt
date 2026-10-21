/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.util.Mth

/** Prepare real mount animation and locators before the camera or passenger needs them. */
object MountedPokemonAnimationRenderController {
    private val forcedDeltas = mutableMapOf<Int, Float>()

    fun setup(pokemon: PokemonEntity, partialTickTime: Float) {
        if (forcedDeltas.containsKey(pokemon.id)) return
        val delegate = pokemon.delegate as? PokemonClientDelegate ?: return
        delegate.currentAspects = pokemon.exposedAspects
        val species = pokemon.exposedSpecies.resourceIdentifier
        val model = VaryingModelRepository.getPoser(species, delegate)
        if (!model.isReadyForAnimation()) return
        val freeze = pokemon.entityData.get(PokemonEntity.FREEZE_FRAME)
        val partial = if (freeze >= 0F) freeze * 20F else partialTickTime
        val age = if (freeze >= 0F) freeze * 20F else pokemon.tickCount + partial
        delegate.updateAge(if (freeze >= 0F) 0 else pokemon.tickCount)
        delegate.updatePartialTicks(partial)
        delegate.updateLocatorPosition(pokemon.getPosition(partialTickTime))
        val body = PokemonRenderState()
        val baseScale = PokemonRenderer.prepareBodyTransform(pokemon, delegate, partialTickTime, body)
        val context = RenderContext()
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
        context.put(RenderContext.ENTITY, pokemon)
        context.put(RenderContext.POSABLE_STATE, delegate)
        context.put(RenderContext.SPECIES, species)
        context.put(RenderContext.ASPECTS, delegate.currentAspects)
        context.put(RenderContext.SCALE, baseScale)
        context.put(RenderContext.TEXTURE, VaryingModelRepository.getTexture(species, delegate))
        model.context = context
        try {
            val yaw = Mth.rotLerp(partialTickTime, pokemon.yBodyRotO, pokemon.yBodyRot)
            val headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTickTime, pokemon.yHeadRotO, pokemon.yHeadRot) - yaw)
            val headPitch = Mth.lerp(partialTickTime, pokemon.xRotO, pokemon.xRot)
            model.applyAnimations(pokemon, delegate, pokemon.walkAnimation.position(partialTickTime),
                pokemon.walkAnimation.speed(partialTickTime), age, headYaw, headPitch)
            val poses = PoseStack()
            PokemonRenderer.applyBodyTransform(body, poses)
            if (body.scale > 0F) model.locatorAccess.update(poses, pokemon, body.scale, delegate.locatorStates, isRoot = true)
            forcedDeltas[pokemon.id] = partialTickTime
        } finally {
            model.setDefault()
            context.pop()
        }
    }
    fun getPartialTick(pokemon: PokemonEntity): Float? = forcedDeltas[pokemon.id]
    fun reset() { forcedDeltas.clear() }
}
