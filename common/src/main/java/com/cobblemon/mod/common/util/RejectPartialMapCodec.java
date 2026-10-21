/*
 * Copyright (C) 2026 Cobblemon Contributors
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

/** Prevent ValueInput from accepting incomplete identity-bearing saved records. */
public final class RejectPartialMapCodec<A> extends MapCodec<A> {
    private final MapCodec<A> delegate;

    public RejectPartialMapCodec(MapCodec<A> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
        DataResult<A> result = delegate.decode(ops, input);
        if (result.isError()) return DataResult.error(() -> result.error().orElseThrow().message());
        return result;
    }

    @Override
    public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return delegate.encode(input, ops, prefix);
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return delegate.keys(ops);
    }
}
