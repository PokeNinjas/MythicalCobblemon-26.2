/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.net.serializers

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.syncher.EntityDataSerializer

/** Bounded native NBT codec; each tracked snapshot owns its mutable tag tree. */
object ShoulderTagDataSerializer : EntityDataSerializer<CompoundTag> {
    override fun codec() = ByteBufCodecs.COMPOUND_TAG
    override fun copy(value: CompoundTag) = value.copy()
}
