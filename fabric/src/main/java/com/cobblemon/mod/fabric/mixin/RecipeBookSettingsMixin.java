/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin;

import com.cobblemon.mod.common.CobblemonRecipeBookTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.UnaryOperator;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend the native four-field settings object without changing vanilla's network codec. */
@Mixin(RecipeBookSettings.class)
public abstract class RecipeBookSettingsMixin {
    @Shadow @Final @Mutable public static MapCodec<RecipeBookSettings> MAP_CODEC;
    @Unique private RecipeBookSettings.TypeSettings cobblemon$cooking = new RecipeBookSettings.TypeSettings(true, true);
    @Unique private boolean cobblemon$hasCookingSettings;

    @Inject(method = "getSettings", at = @At("HEAD"), cancellable = true)
    private void cobblemon$getCooking(RecipeBookType type, CallbackInfoReturnable<RecipeBookSettings.TypeSettings> cir) {
        if (type == CobblemonRecipeBookTypes.INSTANCE.getCOOKING_POT()) cir.setReturnValue(cobblemon$cooking);
    }

    @Inject(method = "updateSettings", at = @At("HEAD"), cancellable = true)
    private void cobblemon$updateCooking(RecipeBookType type, UnaryOperator<RecipeBookSettings.TypeSettings> operator, CallbackInfo ci) {
        if (type == CobblemonRecipeBookTypes.INSTANCE.getCOOKING_POT()) {
            cobblemon$cooking = operator.apply(cobblemon$cooking);
            cobblemon$hasCookingSettings = true;
            ci.cancel();
        }
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private void cobblemon$copyCooking(CallbackInfoReturnable<RecipeBookSettings> cir) {
        RecipeBookSettingsMixin copy = (RecipeBookSettingsMixin) (Object) cir.getReturnValue();
        copy.cobblemon$cooking = cobblemon$cooking;
        copy.cobblemon$hasCookingSettings = cobblemon$hasCookingSettings;
    }

    @Inject(method = "replaceFrom", at = @At("RETURN"))
    private void cobblemon$replaceCooking(RecipeBookSettings other, CallbackInfo ci) {
        RecipeBookSettingsMixin source = (RecipeBookSettingsMixin) (Object) other;
        // A vanilla settings packet carries no cooking field; retain the local selection in that case.
        if (source.cobblemon$hasCookingSettings) {
            cobblemon$cooking = source.cobblemon$cooking;
            cobblemon$hasCookingSettings = true;
        }
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void cobblemon$persistCooking(CallbackInfo ci) {
        MapCodec<RecipeBookSettings> vanilla = MAP_CODEC;
        MapCodec<RecipeBookSettings.TypeSettings> cooking = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("cobblemonCookingPotOpen", true).forGetter(RecipeBookSettings.TypeSettings::open),
            Codec.BOOL.optionalFieldOf("cobblemonCookingPotFiltering", true).forGetter(RecipeBookSettings.TypeSettings::filtering)
        ).apply(i, RecipeBookSettings.TypeSettings::new));
        MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            vanilla.forGetter((RecipeBookSettings value) -> value),
            cooking.forGetter((RecipeBookSettings value) -> ((RecipeBookSettingsMixin) (Object) value).cobblemon$cooking)
        ).apply(i, (value, settings) -> {
            RecipeBookSettingsMixin extended = (RecipeBookSettingsMixin) (Object) value;
            extended.cobblemon$cooking = settings;
            extended.cobblemon$hasCookingSettings = true;
            return value;
        }));
    }
}
