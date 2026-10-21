package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.mixin.accessor.LegacyValueInputAccessor;
import com.cobblemon.mod.common.util.LegacyNbtMapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains vanilla distance validation; only supplies the explicitly saved legacy attachment coordinates. */
@Mixin(BlockAttachedEntity.class)
public abstract class LegacyBlockAttachedPositionMixin {
    @Unique private CompoundTag cobblemon$unresolvedAttachment;
    @Unique private static final String COBBLEMON_RETAINED = "cobblemon:unresolved_attachment";
    @Unique private static final java.util.concurrent.atomic.AtomicInteger cobblemon$reported = new java.util.concurrent.atomic.AtomicInteger();

    @ModifyVariable(method = "readAdditionalSaveData", at = @At("HEAD"), argsOnly = true)
    private ValueInput cobblemon$legacyAttachment(ValueInput input) {
        cobblemon$unresolvedAttachment = null;
        if (!(input instanceof LegacyValueInputAccessor reporter)) return input;
        CompoundTag root = input.read(LegacyNbtMapCodec.INSTANCE).orElseThrow().copy();
        CompoundTag retained = root.getCompound(COBBLEMON_RETAINED).orElse(null);
        CompoundTag attachment = retained == null ? root : retained;
        // A later native save can contain a fallback position; that is not evidence that the original record recovered.
        if (retained != null) cobblemon$unresolvedAttachment = retained.copy();
        if (!root.contains("block_pos") && attachment.get("TileX") instanceof IntTag x
                && attachment.get("TileY") instanceof IntTag y && attachment.get("TileZ") instanceof IntTag z) {
            root.putIntArray("block_pos", new int[]{x.intValue(), y.intValue(), z.intValue()});
        }
        BlockPos position = root.read("block_pos", BlockPos.CODEC).orElse(null);
        BlockAttachedEntity self = (BlockAttachedEntity) (Object) this;
        if (position == null || !position.closerThan(self.blockPosition(), 16.0D)) {
            // Preserve all original entity payload, including unknown attachment fields, for scoped recovery.
            cobblemon$unresolvedAttachment = attachment.copy();
            cobblemon$unresolvedAttachment.remove(COBBLEMON_RETAINED);
            if (cobblemon$reported.getAndIncrement() < 10) org.slf4j.LoggerFactory.getLogger(LegacyBlockAttachedPositionMixin.class).warn(
                    "Unresolved attachment retained: type={} uuid={} dimension={} keys={}; no attachment coordinates were invented",
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(self.getType()), self.getUUID(), self.level().dimension().identifier(), attachment.keySet());
        }
        return TagValueInput.create(reporter.cobblemon$problemReporter(), input.lookup(), root);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void cobblemon$retainInvalidAttachment(ValueOutput output, CallbackInfo ci) {
        if (cobblemon$unresolvedAttachment == null) return;
        CompoundTag wrapper = new CompoundTag(); wrapper.put(COBBLEMON_RETAINED, cobblemon$unresolvedAttachment.copy());
        output.store(LegacyNbtMapCodec.INSTANCE, wrapper);
    }
}
