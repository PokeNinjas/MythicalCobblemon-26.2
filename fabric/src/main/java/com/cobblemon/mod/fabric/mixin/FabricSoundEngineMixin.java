/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin;

import com.cobblemon.mod.common.api.riding.sound.RideAttenuationModel;
import com.cobblemon.mod.common.api.riding.sound.RideLoopSound;
import com.cobblemon.mod.common.client.sound.instances.AlphaCrySoundInstance;
import com.cobblemon.mod.common.duck.ChannelDuck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Consumer;

@Mixin(SoundEngine.class)
public class FabricSoundEngineMixin {
    @WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private void cobblemon$initialChannel(ChannelAccess.ChannelHandle handle, Consumer<Channel> nativeSetup, Operation<Void> original,
                                          @Local(argsOnly = true) SoundInstance instance,
                                          @Local(name = "attenuationDistance") float distance) {
        RideLoopSound ride = instance instanceof RideLoopSound sound ? sound : null;
        boolean alpha = instance instanceof AlphaCrySoundInstance;
        if (ride == null && !alpha) { original.call(handle, nativeSetup); return; }
        RideAttenuationModel attenuation = ride == null ? null : ride.getRideAttenuation();
        boolean muffle = ride != null && ride.getShouldMuffle();
        float muffleAmount = ride == null ? 0 : ride.getMuffleAmount();
        original.call(handle, (Consumer<Channel>) channel -> {
            nativeSetup.accept(channel);
            if (attenuation == RideAttenuationModel.LINEAR) channel.linearAttenuation(distance);
            else if (attenuation == RideAttenuationModel.EXPONENTIAL) ((ChannelDuck) channel).cobblemon$inverseAttenuation(distance);
            else if (ride != null) channel.disableAttenuation();
            if (ride != null) {
                if (muffle) ((ChannelDuck) channel).cobblemon$applyLowPassFilter(1.0f, muffleAmount);
                else ((ChannelDuck) channel).cobblemon$clearFilters();
            }
            if (alpha) ((ChannelDuck) channel).cobblemon$applyReverb(1.8f, 0.25f, 0.1f, 0.9f);
        });
    }

    @WrapOperation(method = "tickInGameSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void cobblemon$tickChannel(ChannelAccess.ChannelHandle handle, Consumer<Channel> nativeUpdate, Operation<Void> original,
                                       @Local TickableSoundInstance instance) {
        if (!(instance instanceof RideLoopSound ride)) { original.call(handle, nativeUpdate); return; }
        boolean muffle = ride.getShouldMuffle();
        float amount = ride.getMuffleAmount();
        original.call(handle, (Consumer<Channel>) channel -> {
            nativeUpdate.accept(channel);
            if (muffle) ((ChannelDuck) channel).cobblemon$applyLowPassFilter(1.0f, amount);
            else ((ChannelDuck) channel).cobblemon$clearFilters();
        });
    }
}
