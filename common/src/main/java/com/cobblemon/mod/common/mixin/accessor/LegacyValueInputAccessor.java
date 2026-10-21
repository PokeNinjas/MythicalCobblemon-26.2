package com.cobblemon.mod.common.mixin.accessor;

import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagValueInput.class)
public interface LegacyValueInputAccessor {
    @Accessor("problemReporter") ProblemReporter cobblemon$problemReporter();
}
