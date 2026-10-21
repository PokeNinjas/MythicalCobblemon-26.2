/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.layer.ShoulderPokemonFrame;
import com.cobblemon.mod.common.client.render.layer.ShoulderRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;

@Mixin(AvatarRenderState.class)
public abstract class AvatarShoulderRenderStateMixin implements ShoulderRenderState {
    @Unique private com.cobblemon.mod.common.client.render.PlayerIconFrame cobblemon$icon;
    @Override public com.cobblemon.mod.common.client.render.PlayerIconFrame getPlayerIcon() { return cobblemon$icon; }
    @Override public void setPlayerIcon(com.cobblemon.mod.common.client.render.PlayerIconFrame frame) { cobblemon$icon = frame; }
    @Unique private List<ShoulderPokemonFrame> cobblemon$shoulders = List.of();
    @Override public List<ShoulderPokemonFrame> getShoulderPokemon() { return cobblemon$shoulders; }
    @Override public void setShoulderPokemon(List<ShoulderPokemonFrame> frames) { cobblemon$shoulders = List.copyOf(frames); }
}
