/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.api.gui.RegionalBlurState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRegionalBlurStateMixin implements RegionalBlurState {
    @Shadow private int firstStratumAfterBlur;
    @Shadow @Final private List<?> strata;
    @Unique private final List<ScreenRectangle> cobblemon$regions = new ArrayList<>();

    @Override
    public void cobblemon$requestBlur(ScreenRectangle rectangle) {
        if (rectangle.width() <= 0 || rectangle.height() <= 0) return;
        if (firstStratumAfterBlur == Integer.MAX_VALUE) {
            ((GuiRenderState) (Object) this).blurBeforeThisStratum();
        } else if (cobblemon$regions.isEmpty()) {
            return; // The native screen has already requested a full-screen blur.
        }
        cobblemon$regions.add(rectangle);
    }

    @Override
    public List<ScreenRectangle> cobblemon$blurRegions() { return cobblemon$regions; }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), cancellable = true)
    private void cobblemon$preferNativeFullBlur(CallbackInfo ci) {
        if (!cobblemon$regions.isEmpty()) {
            cobblemon$regions.clear();
            firstStratumAfterBlur = strata.size() - 1;
            ci.cancel();
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void cobblemon$clearRegions(CallbackInfo ci) { cobblemon$regions.clear(); }
}
