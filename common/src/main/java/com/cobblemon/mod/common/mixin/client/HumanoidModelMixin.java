/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.player.MountedLivingRenderState;
import com.cobblemon.mod.common.client.render.player.MountedPlayerRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void cobblemon$applyCapturedRidingPose(HumanoidRenderState state, CallbackInfo ci) {
        var pose = ((MountedLivingRenderState)state).getRiderPose();
        if (pose != null) {
            MountedPlayerRenderer.apply((HumanoidModel<HumanoidRenderState>)(Object)this, state, pose);
            ci.cancel();
        }
    }
}
