/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.CobblemonRecipeCategories;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookTabButton.class)
public abstract class RecipeBookTabButtonMixin {
    @Inject(method = "updateVisibility", at = @At("HEAD"), cancellable = true)
    private void cobblemon$keepCookingSearchVisible(ClientRecipeBook book, CallbackInfoReturnable<Boolean> cir) {
        RecipeBookTabButton self = (RecipeBookTabButton) (Object) this;
        if (self.getCategory() == CobblemonRecipeCategories.INSTANCE.getCOOKING_POT_SEARCH()) {
            self.visible = true;
            cir.setReturnValue(true);
        }
    }
}
