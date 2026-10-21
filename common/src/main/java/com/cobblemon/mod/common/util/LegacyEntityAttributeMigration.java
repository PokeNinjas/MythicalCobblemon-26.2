package com.cobblemon.mod.common.util;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Vanilla's typed attribute renames do not visit unregistered mod entity schemas. */
public final class LegacyEntityAttributeMigration {
    private static final List<String> PREFIXES = List.of("generic.", "horse.", "player.", "zombie.");

    private LegacyEntityAttributeMigration() {}

    /** Run after vanilla has converted the surrounding entity field layout. */
    public static CompoundTag migrateRegion(CompoundTag region) {
        if (region.get("Entities") instanceof ListTag entities) {
            for (var value : entities) if (value instanceof CompoundTag entity) migrateEntity(entity);
        }
        return region;
    }

    private static void migrateEntity(CompoundTag entity) {
        String type = entity.getStringOr("id", "");
        if ((type.equals("cobblemon:pokemon") || type.equals("cobblemon:npc"))
            && entity.get("attributes") instanceof ListTag attributes) {
            for (var value : attributes) {
                if (!(value instanceof CompoundTag attribute)) continue;
                var id = attribute.getString("id");
                if (id.isEmpty()) continue;
                String namespaced = id.get().contains(":") ? id.get() : "minecraft:" + id.get();
                for (String prefix : PREFIXES) {
                    String oldPrefix = "minecraft:" + prefix;
                    if (namespaced.startsWith(oldPrefix)) {
                        attribute.putString("id", "minecraft:" + namespaced.substring(oldPrefix.length()));
                        break;
                    }
                }
            }
        }
        // A mod entity can be a passenger of a vanilla or another mod's vehicle.
        if (entity.get("Passengers") instanceof ListTag passengers) {
            for (var value : passengers) if (value instanceof CompoundTag passenger) migrateEntity(passenger);
        }
    }
}
