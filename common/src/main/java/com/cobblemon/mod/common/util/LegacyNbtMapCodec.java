/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/** Preserves existing Cobblemon top-level NBT keys through the ValueInput/Output API. */
public final class LegacyNbtMapCodec extends MapCodec<CompoundTag> {
    public static final LegacyNbtMapCodec INSTANCE = new LegacyNbtMapCodec();

    private LegacyNbtMapCodec() {}

    @Override
    public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
        return CompoundTag.CODEC.parse(ops, ops.createMap(input.entries()));
    }

    @Override
    public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        for (var entry : input.entrySet()) {
            prefix.add(entry.getKey(), NbtOps.INSTANCE.convertTo(ops, entry.getValue()));
        }
        return prefix;
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return Stream.empty();
    }
}
