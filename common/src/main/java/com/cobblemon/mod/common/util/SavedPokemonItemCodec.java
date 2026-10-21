package com.cobblemon.mod.common.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/** Held/cosmetic items live outside vanilla's player inventory data-fixer path. */
public final class SavedPokemonItemCodec implements Codec<ItemStack> {
    public static final SavedPokemonItemCodec CODEC = new SavedPokemonItemCodec();
    public static final String VERSION_KEY = "cobblemon:minecraft_data_version";
    // Official Minecraft 1.21.1 version.json, the supported source port version.
    private static final int LEGACY_VERSION = 3955;

    private SavedPokemonItemCodec() {}

    @Override
    public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
        return ItemStack.CODEC.encode(stack, ops, prefix).flatMap(encoded ->
            ops.mergeToMap(encoded, ops.createString(VERSION_KEY), ops.createInt(currentVersion())));
    }

    @Override
    public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
        var versionValue = ops.get(input, VERSION_KEY).result();
        T item = ops.remove(input, VERSION_KEY);
        if (versionValue.isPresent()) {
            var parsed = Codec.INT.parse(ops, versionValue.get());
            if (parsed.isError()) return DataResult.error(() -> "Invalid Pokemon item data version");
            int version = parsed.getOrThrow();
            if (version < 0 || version > currentVersion()) {
                return DataResult.error(() -> "Unsupported Pokemon item data version: " + version);
            }
            return version == currentVersion() ? ItemStack.CODEC.decode(ops, item) : upgrade(ops, item, version);
        }

        // Early private 26.2 saves and original 1.21.1 saves were unversioned.
        // Preserve currently readable stacks; only retry failed stacks through
        // the known source-version converter, never reinterpret modern data.
        var current = ItemStack.CODEC.decode(ops, item);
        if (current.isSuccess()) return current;
        return upgrade(ops, item, LEGACY_VERSION);
    }

    private static <T> DataResult<Pair<ItemStack, T>> upgrade(DynamicOps<T> ops, T item, int version) {
        try {
            var converted = DataFixers.getDataFixer().update(References.ITEM_STACK,
                new Dynamic<>(ops, item), version, currentVersion());
            return ItemStack.CODEC.decode(converted);
        } catch (RuntimeException error) {
            return DataResult.error(() -> "Cannot upgrade saved Pokemon item: " + error.getMessage());
        }
    }

    private static int currentVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
}
