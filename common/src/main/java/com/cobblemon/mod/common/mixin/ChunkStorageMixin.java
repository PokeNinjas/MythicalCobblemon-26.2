/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.datafixer.CobblemonSchemas;
import com.cobblemon.mod.common.util.LegacyEntityAttributeMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/** Run Cobblemon upgrades after vanilla for both terrain/block entities and entity regions. */
@Mixin(SimpleRegionStorage.class)
public abstract class ChunkStorageMixin {
    @Shadow @Final private DataFixTypes dataFixType;

    @ModifyReturnValue(method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private CompoundTag cobblemon$upgradeRegion(CompoundTag vanillaFixed) {
        if (dataFixType != DataFixTypes.CHUNK && dataFixType != DataFixTypes.ENTITY_CHUNK) return vanillaFixed;
        if (dataFixType == DataFixTypes.ENTITY_CHUNK) LegacyEntityAttributeMigration.migrateRegion(vanillaFixed);
        int version = vanillaFixed.contains(CobblemonSchemas.VERSION_KEY)
            ? vanillaFixed.getInt(CobblemonSchemas.VERSION_KEY).orElseThrow() : 0;
        if (version >= CobblemonSchemas.DATA_VERSION) return vanillaFixed;
        CompoundTag updated = dataFixType.update(CobblemonSchemas.getDATA_FIXER(), vanillaFixed, version, CobblemonSchemas.DATA_VERSION);
        updated.putInt(CobblemonSchemas.VERSION_KEY, CobblemonSchemas.DATA_VERSION);
        return updated;
    }
}
