/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.duck.ShoulderPokemonData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerShoulderDataMixin implements ShoulderPokemonData {
    @Unique private static final EntityDataAccessor<CompoundTag> cobblemon$leftShoulder =
        SynchedEntityData.defineId(Player.class, com.cobblemon.mod.common.api.net.serializers.ShoulderTagDataSerializer.INSTANCE);
    @Unique private static final EntityDataAccessor<CompoundTag> cobblemon$rightShoulder =
        SynchedEntityData.defineId(Player.class, com.cobblemon.mod.common.api.net.serializers.ShoulderTagDataSerializer.INSTANCE);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void cobblemon$defineShoulders(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(cobblemon$leftShoulder, new CompoundTag());
        builder.define(cobblemon$rightShoulder, new CompoundTag());
    }
    @Override public CompoundTag cobblemon$getShoulderPokemon(boolean left) {
        return ((Player)(Object)this).getEntityData().get(left ? cobblemon$leftShoulder : cobblemon$rightShoulder);
    }
    @Override public void cobblemon$setShoulderPokemon(boolean left, CompoundTag tag) {
        // Copies prevent in-place NBT edits from bypassing native dirty tracking.
        ((Player)(Object)this).getEntityData().set(left ? cobblemon$leftShoulder : cobblemon$rightShoulder, tag.copy());
    }
}
