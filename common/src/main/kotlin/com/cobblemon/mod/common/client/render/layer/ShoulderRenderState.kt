/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.client.render.item.HeldItemRenderState
import com.cobblemon.mod.common.client.render.models.blockbench.BedrockModelSnapshot
import com.cobblemon.mod.common.client.render.pokemon.PokemonLayerSubmission
import net.minecraft.resources.Identifier
import org.joml.Matrix4f

interface ShoulderRenderState {
    var shoulderPokemon: List<ShoulderPokemonFrame>
    var playerIcon: com.cobblemon.mod.common.client.render.PlayerIconFrame?
}

/** No player, world, mutable poser or MoLang context reaches deferred drawing. */
data class ShoulderPokemonFrame(
    val body: BedrockModelSnapshot,
    val texture: Identifier,
    val transform: Matrix4f,
    val layers: List<PokemonLayerSubmission>,
    val items: List<HeldItemRenderState>
)
