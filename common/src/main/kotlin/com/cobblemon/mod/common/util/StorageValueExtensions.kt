/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import com.mojang.serialization.Codec
import net.minecraft.world.level.storage.ValueInput

/** Missing optional fields are allowed; corrupt present data must not silently disappear. */
fun <T : Any> ValueInput.readOptionalOrThrow(key: String, codec: Codec<T>): T? =
    read(StrictOptionalFieldCodec(key, codec)).orElseThrow {
        IllegalStateException("Cannot decode saved field '$key'")
    }.orElse(null)
