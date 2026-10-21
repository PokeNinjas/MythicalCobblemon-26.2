/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.net.serializers

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.syncher.EntityDataSerializer
import java.util.Optional
import java.util.UUID

/** Battle IDs are UUIDs, not entity references. Vanilla removed its UUID serializer in 26.2. */
object OptionalUUIDDataSerializer : EntityDataSerializer<Optional<UUID>> {
    private val streamCodec = StreamCodec.of<RegistryFriendlyByteBuf, Optional<UUID>>(
        { buffer, value ->
            buffer.writeBoolean(value.isPresent)
            value.ifPresent { buffer.writeUUID(it) }
        },
        { buffer -> if (buffer.readBoolean()) Optional.of(buffer.readUUID()) else Optional.empty() }
    )

    override fun codec() = streamCodec
    override fun copy(value: Optional<UUID>) = value
}
