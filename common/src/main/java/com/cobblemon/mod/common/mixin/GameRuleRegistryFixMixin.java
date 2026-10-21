package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.util.LegacyGameRuleMigration;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.fixes.GameRuleRegistryFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRuleRegistryFix.class)
public abstract class GameRuleRegistryFixMixin {
    // Native 26.2's field converter retains unknown rules. Convert ours before
    // the resulting game_rules map is decoded by the registered-rule codec.
    @Inject(method = "lambda$makeRule$2", at = @At("RETURN"), cancellable = true)
    private static void cobblemon$convertLegacyRules(Dynamic<?> input, CallbackInfoReturnable<Dynamic<?>> cir) {
        cir.setReturnValue(LegacyGameRuleMigration.migrate(cir.getReturnValue()));
    }
}
