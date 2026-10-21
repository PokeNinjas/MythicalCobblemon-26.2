/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.CompoundTag
import java.util.UUID

/** UUIDUtil retains the four-int representation used by existing Cobblemon saves. */
fun CompoundTag.putUUID(key: String, value: UUID) = store(key, UUIDUtil.CODEC, value)

/** Required identity fields must fail to load when malformed, never acquire a new identity. */
fun CompoundTag.getUUID(key: String): UUID = read(key, UUIDUtil.CODEC).orElseThrow {
    IllegalArgumentException("Missing or malformed UUID at '$key'")
}

fun CompoundTag.hasUUID(key: String): Boolean = read(key, UUIDUtil.CODEC).isPresent
