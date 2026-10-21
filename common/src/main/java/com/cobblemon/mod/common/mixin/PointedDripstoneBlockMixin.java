/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.api.tags.CobblemonBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpeleothemBlock.class)
public abstract class PointedDripstoneBlockMixin {
    @Inject(method = "canGrow", at = @At("HEAD"), cancellable = true)
    private void cobblemon$growthSupport(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PointedDripstoneBlock) {
            cir.setReturnValue(level.getBlockState(pos.above()).is(CobblemonBlockTags.DRIPSTONE_GROWABLE));
        }
    }
}
