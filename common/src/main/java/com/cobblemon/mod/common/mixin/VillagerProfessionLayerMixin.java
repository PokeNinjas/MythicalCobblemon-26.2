/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.client.render.NamedVillagerTexture;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Preserve named nurse textures while native rendering owns model/material submission. */
@Mixin(VillagerProfessionLayer.class)
public abstract class VillagerProfessionLayerMixin {
    @ModifyArg(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/VillagerProfessionLayer;renderColoredCutoutModel(Lnet/minecraft/client/model/Model;Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;II)V"), index = 1)
    private Identifier cobblemon$namedTexture(Identifier texture, @Local(argsOnly = true) LivingEntityRenderState state) {
        var choice = ((NamedVillagerTexture.State) state).cobblemon$getNamedVillagerTexture();
        return choice != null && choice.original().equals(texture) ? choice.replacement() : texture;
    }
}
