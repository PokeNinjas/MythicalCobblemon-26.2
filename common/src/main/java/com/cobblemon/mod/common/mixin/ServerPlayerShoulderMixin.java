/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.duck.ShoulderPokemonData;
import com.cobblemon.mod.common.util.CompoundTagUtilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shoulder persistence and removal moved from Player to ServerPlayer in 26.2. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerShoulderMixin {
    @Shadow public abstract CompoundTag getShoulderEntityLeft();
    @Shadow public abstract CompoundTag getShoulderEntityRight();
    @Shadow protected abstract void setShoulderEntityLeft(CompoundTag tag);
    @Shadow protected abstract void setShoulderEntityRight(CompoundTag tag);
    @Shadow private void respawnEntityOnShoulder(CompoundTag tag) { throw new AssertionError(); }
    @Shadow private long timeEntitySatOnShoulder;

    @Inject(method = "setShoulderEntityLeft", at = @At("TAIL"))
    private void cobblemon$syncLeft(CompoundTag tag, CallbackInfo ci) {
        ((ShoulderPokemonData)this).cobblemon$setShoulderPokemon(true,
            CompoundTagUtilities.isShoulderPokemon(tag) ? tag : new CompoundTag());
    }
    @Inject(method = "setShoulderEntityRight", at = @At("TAIL"))
    private void cobblemon$syncRight(CompoundTag tag, CallbackInfo ci) {
        ((ShoulderPokemonData)this).cobblemon$setShoulderPokemon(false,
            CompoundTagUtilities.isShoulderPokemon(tag) ? tag : new CompoundTag());
    }
    @Inject(method = "respawnEntityOnShoulder", at = @At("HEAD"), cancellable = true)
    private void cobblemon$recallShoulderPokemon(CompoundTag tag, CallbackInfo ci) {
        if (!CompoundTagUtilities.isShoulderPokemon(tag)) return;
        ServerPlayer player = (ServerPlayer)(Object)this;
        var uuid = CompoundTagUtilities.getPokemonID(tag);
        for (var pokemon : Cobblemon.INSTANCE.getStorage().getParty(player.getUUID(), player.registryAccess())) {
            if (pokemon.getUuid().equals(uuid)) pokemon.recall();
        }
        ci.cancel();
    }
    @Inject(method = "removeEntitiesOnShoulder", at = @At("HEAD"), cancellable = true)
    private void cobblemon$keepPokemonOnShoulders(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (player.isSpectator() || player.isDeadOrDying()) return;
        if (timeEntitySatOnShoulder + 20L < player.level().getGameTime()) {
            if (!CompoundTagUtilities.isShoulderPokemon(getShoulderEntityLeft())) {
                respawnEntityOnShoulder(getShoulderEntityLeft());
                setShoulderEntityLeft(new CompoundTag());
            }
            if (!CompoundTagUtilities.isShoulderPokemon(getShoulderEntityRight())) {
                respawnEntityOnShoulder(getShoulderEntityRight());
                setShoulderEntityRight(new CompoundTag());
            }
        }
        ci.cancel();
    }
}
