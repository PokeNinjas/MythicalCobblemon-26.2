/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.api.gui.RegionalBlurState;
import com.cobblemon.mod.common.api.gui.RegionalGuiBlur;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRegionalBlurRendererMixin {
    @Shadow @Final private GuiRenderState renderState;
    @Unique private final RegionalGuiBlur cobblemon$blur = new RegionalGuiBlur();

    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"))
    private void cobblemon$blurTooltipRegion(GameRenderer renderer, Operation<Void> original) {
        cobblemon$blur.render(RenderSystem.getDevice(), renderer.mainRenderTarget().getColorTexture(),
            renderer.gameRenderState().windowRenderState.guiScale,
            ((RegionalBlurState) renderState).cobblemon$blurRegions(), () -> original.call(renderer));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void cobblemon$expireBackup(CallbackInfo ci) { cobblemon$blur.endFrame(); }

    @Inject(method = "close", at = @At("HEAD"))
    private void cobblemon$closeBackup(CallbackInfo ci) { cobblemon$blur.close(); }
}
