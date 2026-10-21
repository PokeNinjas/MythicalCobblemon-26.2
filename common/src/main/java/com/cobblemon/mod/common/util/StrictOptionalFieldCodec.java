/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Optional;
import java.util.stream.Stream;

/** Reject corrupt present fields without a partial empty value that ValueInput could recover. */
public final class StrictOptionalFieldCodec<A> extends MapCodec<Optional<A>> {
    private final String name;
    private final Codec<A> codec;

    public StrictOptionalFieldCodec(String name, Codec<A> codec) {
        this.name = name;
        this.codec = codec;
    }

    @Override
    public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(name);
        if (value == null) return DataResult.success(Optional.empty());
        DataResult<A> result = codec.parse(ops, value);
        if (result.isError()) return DataResult.error(() -> "Invalid saved field '" + name + "': " + result.error().orElseThrow().message());
        return DataResult.success(Optional.of(result.getOrThrow()));
    }

    @Override
    public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        if (input.isPresent()) prefix.add(name, codec.encodeStart(ops, input.orElseThrow()));
        return prefix;
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return Stream.of(ops.createString(name));
    }
}
