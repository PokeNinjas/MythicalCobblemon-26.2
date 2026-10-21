package com.cobblemon.mod.common.util;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.LoggerFactory;
import java.util.HashSet;

/** Item payloads inside mod block entities are not traversed by vanilla chunk DFU. */
public final class LegacyWorldItems {
    public static final String RETAINED = "cobblemon:unresolved_items";
    private LegacyWorldItems() {}

    public static ListTag load(ValueInput input, NonNullList<ItemStack> inventory) {
        CompoundTag root = input.read(LegacyNbtMapCodec.INSTANCE).orElseThrow();
        ListTag failed = new ListTag();
        var occupied = new HashSet<Integer>();
        var ops = input.lookup().createSerializationContext(NbtOps.INSTANCE);
        // Current entries win if a player has subsequently used a formerly unresolved slot.
        for (String field : new String[]{"Items", RETAINED}) {
            if (!(root.get(field) instanceof ListTag entries)) continue;
            for (Tag entry : entries) {
                try {
                    if (!(entry instanceof CompoundTag saved)) throw new IllegalArgumentException("Non-compound item entry");
                    int slot = saved.getInt("Slot").orElseThrow();
                    if (slot < 0 || slot >= inventory.size() || occupied.contains(slot)) throw new IllegalArgumentException("Invalid or occupied item slot");
                    CompoundTag item = saved.copy(); item.remove("Slot");
                    var decoded = ItemStack.CODEC.parse(ops, item).result();
                    if (decoded.isEmpty() && hasLegacyEnchantments(item)) {
                        CompoundTag upgraded = migrateEnchantments(item);
                        if (!item.getString("id").equals(upgraded.getString("id"))
                                || item.getIntOr("count", 1) != upgraded.getIntOr("count", 1)) throw new IllegalArgumentException("Migration changed item custody");
                        CompoundTag before = item.getCompound("components").orElseGet(CompoundTag::new);
                        CompoundTag after = upgraded.getCompound("components").orElseGet(CompoundTag::new);
                        for (String key : before.keySet()) {
                            if ((!key.startsWith("minecraft:") || key.equals("minecraft:custom_data"))
                                    && !java.util.Objects.equals(before.get(key), after.get(key))) throw new IllegalArgumentException("Migration changed custom item data");
                        }
                        item = upgraded;
                        decoded = ItemStack.CODEC.parse(ops, item).result();
                    }
                    ItemStack stack = decoded.orElseThrow();
                    if (stack.isEmpty() || stack.getCount() != item.getIntOr("count", 1)) throw new IllegalArgumentException("Invalid decoded item quantity");
                    // Do not silently discard components ignored by an unavailable registry/codec.
                    Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElseThrow();
                    if (!(encoded instanceof CompoundTag roundtrip)
                            || !normalizeDefaults(item).equals(normalizeDefaults(roundtrip))) throw new IllegalArgumentException("Item did not round-trip losslessly");
                    inventory.set(slot, stack); occupied.add(slot);
                } catch (RuntimeException failure) {
                    failed.add(entry.copy());
                }
            }
        }
        if (!failed.isEmpty()) LoggerFactory.getLogger(LegacyWorldItems.class).warn(
                "Retained {} undecodable or conflicting Gilded Chest item records in {} for recovery; their source payloads were not discarded", failed.size(), RETAINED);
        return failed;
    }

    private static boolean hasLegacyEnchantments(CompoundTag item) {
        CompoundTag components = item.getCompound("components").orElseGet(CompoundTag::new);
        for (String key : new String[]{"minecraft:enchantments", "minecraft:stored_enchantments"})
            if (components.get(key) instanceof CompoundTag value && value.get("levels") instanceof CompoundTag) return true;
        if (components.get("minecraft:container") instanceof ListTag items)
            for (Tag tag : items) if (tag instanceof CompoundTag entry && entry.get("item") instanceof CompoundTag child && hasLegacyEnchantments(child)) return true;
        for (String key : new String[]{"minecraft:bundle_contents", "minecraft:charged_projectiles"})
            if (components.get(key) instanceof ListTag items)
                for (Tag tag : items) if (tag instanceof CompoundTag child && hasLegacyEnchantments(child)) return true;
        return false;
    }

    /** Convert just recognized old component shapes, including mixed-format items in already-upgraded chunks. */
    private static CompoundTag migrateEnchantments(CompoundTag source) {
        CompoundTag item = source.copy();
        CompoundTag components = item.getCompound("components").orElseGet(CompoundTag::new);
        CompoundTag legacy = new CompoundTag();
        for (String key : new String[]{"minecraft:enchantments", "minecraft:stored_enchantments"})
            if (components.get(key) instanceof CompoundTag value && value.get("levels") instanceof CompoundTag) legacy.put(key, value.copy());
        if (!legacy.isEmpty()) {
            CompoundTag probe = new CompoundTag(); probe.putString("id", "minecraft:stone"); probe.putInt("count", 1); probe.put("components", legacy);
            Tag fixed = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, probe), 3955,
                    SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
            if (!(fixed instanceof CompoundTag converted)) throw new IllegalArgumentException("Invalid enchantment migration");
            CompoundTag upgraded = converted.getCompound("components").orElseThrow();
            for (String key : legacy.keySet()) if (!upgraded.contains(key)) throw new IllegalArgumentException("Enchantment component lost");
            for (String key : upgraded.keySet()) {
                if (!legacy.contains(key) && components.contains(key) && !java.util.Objects.equals(components.get(key), upgraded.get(key)))
                    throw new IllegalArgumentException("Conflicting native tooltip component");
                components.put(key, upgraded.get(key).copy());
            }
        }
        if (components.get("minecraft:container") instanceof ListTag children)
            for (int i = 0; i < children.size(); i++) if (children.get(i) instanceof CompoundTag slot && slot.get("item") instanceof CompoundTag nested) slot.put("item", migrateEnchantments(nested));
        for (String key : new String[]{"minecraft:bundle_contents", "minecraft:charged_projectiles"})
            if (components.get(key) instanceof ListTag children)
                for (int i = 0; i < children.size(); i++) if (children.get(i) instanceof CompoundTag nested) children.set(i, migrateEnchantments(nested));
        if (!components.isEmpty()) item.put("components", components);
        return item;
    }

    private static CompoundTag normalizeDefaults(CompoundTag source) {
        CompoundTag item = source.copy();
        if (item.getIntOr("count", 1) == 1) item.remove("count");
        CompoundTag components = item.getCompound("components").orElse(null);
        if (components != null) {
            // DFU emits an explicit false flag; the native tooltip codec omits that default.
            // Preserve hidden components, a true flag and every unknown field unchanged.
            if (components.get("minecraft:tooltip_display") instanceof CompoundTag tooltip
                    && tooltip.get("hide_tooltip") instanceof ByteTag hidden && hidden.byteValue() == 0)
                tooltip.remove("hide_tooltip");
            if (components.get("minecraft:container") instanceof ListTag children)
                for (Tag tag : children) if (tag instanceof CompoundTag slot && slot.get("item") instanceof CompoundTag nested) slot.put("item", normalizeDefaults(nested));
            for (String key : new String[]{"minecraft:bundle_contents", "minecraft:charged_projectiles"})
                if (components.get(key) instanceof ListTag children)
                    for (int i = 0; i < children.size(); i++) if (children.get(i) instanceof CompoundTag nested) children.set(i, normalizeDefaults(nested));
            if (components.isEmpty()) item.remove("components");
        }
        return item;
    }

    public static void retain(ValueOutput output, String key, ListTag records) {
        if (records.isEmpty()) return;
        CompoundTag wrapper = new CompoundTag(); wrapper.put(key, records.copy());
        output.store(LegacyNbtMapCodec.INSTANCE, wrapper);
    }
}
