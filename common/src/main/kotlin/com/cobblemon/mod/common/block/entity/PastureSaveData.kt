/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.RejectPartialMapCodec
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import java.util.Optional
import java.util.UUID

/** Decode all identities and bounds before changing a live pasture. */
data class PastureSaveData(
    val ownerId: UUID?,
    val ownerName: String,
    val minRoamPos: BlockPos,
    val maxRoamPos: BlockPos,
    val tethers: List<TetherRecord>
) {
    data class TetherRecord(val playerId: UUID, val tetheringId: UUID, val pokemonId: UUID, val pcId: UUID, val entityId: Int) {
        companion object {
            val CODEC: Codec<TetherRecord> = RecordCodecBuilder.create { instance ->
                instance.group(
                    UUIDUtil.CODEC.fieldOf(DataKeys.TETHERING_PLAYER_ID).forGetter(TetherRecord::playerId),
                    UUIDUtil.CODEC.fieldOf(DataKeys.TETHERING_ID).forGetter(TetherRecord::tetheringId),
                    UUIDUtil.CODEC.fieldOf(DataKeys.POKEMON_UUID).forGetter(TetherRecord::pokemonId),
                    UUIDUtil.CODEC.fieldOf(DataKeys.PC_ID).forGetter(TetherRecord::pcId),
                    Codec.INT.fieldOf(DataKeys.TETHERING_ENTITY_ID).forGetter(TetherRecord::entityId)
                ).apply(instance, ::TetherRecord)
            }
        }
    }

    companion object {
        val CODEC: MapCodec<PastureSaveData> = RejectPartialMapCodec(RecordCodecBuilder.mapCodec<PastureSaveData> { instance ->
            instance.group(
                com.cobblemon.mod.common.util.StrictOptionalFieldCodec(DataKeys.TETHER_OWNER_ID, UUIDUtil.CODEC).forGetter { Optional.ofNullable(it.ownerId) },
                Codec.STRING.optionalFieldOf(DataKeys.TETHER_OWNER_NAME, "").forGetter(PastureSaveData::ownerName),
                BlockPos.CODEC.fieldOf(DataKeys.TETHER_MIN_ROAM_POS).forGetter(PastureSaveData::minRoamPos),
                BlockPos.CODEC.fieldOf(DataKeys.TETHER_MAX_ROAM_POS).forGetter(PastureSaveData::maxRoamPos),
                TetherRecord.CODEC.listOf().fieldOf(DataKeys.TETHER_POKEMON).forGetter(PastureSaveData::tethers)
            ).apply(instance) { owner, name, min, max, entries -> PastureSaveData(owner.orElse(null), name, min, max, entries.toList()) }
        }.validate { data ->
            when {
                data.minRoamPos.x > data.maxRoamPos.x || data.minRoamPos.y > data.maxRoamPos.y || data.minRoamPos.z > data.maxRoamPos.z ->
                    DataResult.error { "Pasture roam bounds are inverted" }
                data.tethers.map { it.tetheringId }.toSet().size != data.tethers.size ->
                    DataResult.error { "Duplicate pasture tether identity" }
                else -> DataResult.success(data)
            }
        })
    }
}
