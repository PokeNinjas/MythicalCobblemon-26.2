/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.block.SaccharineLeafBlock;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.world.entity.animal.bee.Bee$BeeGrowCropGoal")
public abstract class BeeEntityMixin {
    // Supply the native growth result; native code retains growth cadence, effects and the bee's pollination quota.
    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 1)
    private BlockState cobblemon$growHoney(BlockState growth, @Local(ordinal = 0) BlockState belowState) {
        if (growth == null && belowState.getBlock() instanceof SaccharineLeafBlock
            && !belowState.getValue(BlockStateProperties.WATERLOGGED)) {
            var age = SaccharineLeafBlock.Companion.getAGE();
            if (belowState.getValue(age) < 2) return belowState.setValue(age, belowState.getValue(age) + 1);
        }
        return growth;
    }
}
