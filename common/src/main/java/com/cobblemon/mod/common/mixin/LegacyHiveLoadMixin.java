package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.mixin.accessor.LegacyValueInputAccessor;
import com.cobblemon.mod.common.util.LegacyNbtMapCodec;
import com.cobblemon.mod.common.util.LegacyWorldItems;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.storage.*;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public abstract class LegacyHiveLoadMixin {
    @Unique private static final String COBBLEMON_RETAINED = "cobblemon:unresolved_hive_occupants";
    @Unique private ListTag cobblemon$retained = new ListTag();

    @ModifyVariable(method = "loadAdditional", at = @At("HEAD"), argsOnly = true)
    private ValueInput cobblemon$readLegacyOccupants(ValueInput input) {
        cobblemon$retained = new ListTag();
        if (!(input instanceof LegacyValueInputAccessor reporter)) return input;
        CompoundTag root = input.read(LegacyNbtMapCodec.INSTANCE).orElseThrow().copy();
        ListTag valid = new ListTag();
        var ops = input.lookup().createSerializationContext(NbtOps.INSTANCE);
        for (String key : new String[]{"bees", COBBLEMON_RETAINED}) {
            if (!(root.get(key) instanceof ListTag records)) continue;
            for (Tag raw : records) {
                if (!(raw instanceof CompoundTag record)) { cobblemon$retained.add(raw.copy()); continue; }
                CompoundTag migrated = record.copy();
                CompoundTag data = migrated.getCompound("entity_data").orElse(null);
                if (data != null) {
                    String id = data.getStringOr("id", "");
                    if ((id.equals("cobblemon_pokemon_entity_placeholder") || id.equals("minecraft:cobblemon_pokemon_entity_placeholder"))
                            && !data.getStringOr("pokemonProperties", "").isBlank()) {
                        // Only the old worldgen placeholder changes; properties, timers and all other data remain intact.
                        data.putString("id", "cobblemon:pokemon");
                    }
                }
                if (BeehiveBlockEntity.Occupant.CODEC.parse(ops, migrated).result().isPresent()) valid.add(migrated);
                else cobblemon$retained.add(raw.copy());
            }
        }
        root.put("bees", valid);
        root.remove(COBBLEMON_RETAINED);
        if (!cobblemon$retained.isEmpty()) LoggerFactory.getLogger(LegacyHiveLoadMixin.class).warn(
                "Retained {} undecodable hive occupants in {} without discarding their saved payloads", cobblemon$retained.size(), COBBLEMON_RETAINED);
        return TagValueInput.create(reporter.cobblemon$problemReporter(), input.lookup(), root);
    }

    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void cobblemon$saveUnresolvedOccupants(ValueOutput output, CallbackInfo ci) {
        LegacyWorldItems.retain(output, COBBLEMON_RETAINED, cobblemon$retained);
    }
}
