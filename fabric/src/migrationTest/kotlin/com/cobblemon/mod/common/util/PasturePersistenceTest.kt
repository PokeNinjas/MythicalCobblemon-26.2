/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.block.entity.PastureSaveData
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PasturePersistenceTest {
    private val owner = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val tether = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val pokemon = UUID.fromString("fedcba98-7654-3210-1234-56789abcdef0")
    private val pc = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210")

    // Literal legacy field names and four-int identities intentionally do not use the production constants.
    private fun legacy() = CompoundTag().apply {
        putIntArray("TetherOwnerId", UUIDUtil.uuidToIntArray(owner))
        putString("TetherOwnerName", "Trainer")
        putIntArray("TetherMinRoamPos", intArrayOf(-18, -64, 300))
        putIntArray("TetherMaxRoamPos", intArrayOf(22, 256, 360))
        put("TetherPokemon", ListTag().apply {
            add(CompoundTag().apply {
                putIntArray("PlayerId", UUIDUtil.uuidToIntArray(owner))
                putIntArray("TetheringId", UUIDUtil.uuidToIntArray(tether))
                putIntArray("UUID", UUIDUtil.uuidToIntArray(pokemon))
                putIntArray("PCId", UUIDUtil.uuidToIntArray(pc))
                putInt("EntityId", 713)
            })
        })
    }

    @Test fun `legacy pasture save preserves every identity position and field through native values`() {
        val original = legacy()
        val input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, original)
        val decoded = input.read(PastureSaveData.CODEC).orElseThrow()
        assertEquals(owner, decoded.ownerId)
        assertEquals(BlockPos(-18, -64, 300), decoded.minRoamPos)
        assertEquals(BlockPos(22, 256, 360), decoded.maxRoamPos)
        assertEquals(PastureSaveData.TetherRecord(owner, tether, pokemon, pc, 713), decoded.tethers.single())
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY)
        output.store(PastureSaveData.CODEC, decoded)
        assertEquals(original, output.buildResult())
        assertTrue(output.buildResult().get("TetherOwnerId") is IntArrayTag)
    }

    @Test fun `missing or malformed required Pokemon identities reject the complete load`() {
        for (key in listOf("PlayerId", "TetheringId", "UUID", "PCId")) {
            for (malformed in listOf(false, true)) {
                val tag = legacy()
                val entry = (tag.get("TetherPokemon") as ListTag)[0] as CompoundTag
                if (malformed) entry.putIntArray(key, intArrayOf(1, 2, 3)) else entry.remove(key)
                assertTrue(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag)
                    .read(PastureSaveData.CODEC).isEmpty, key)
            }
        }
    }

    @Test fun `duplicate tether identities and reversed bounds fail rather than duplicating saved Pokemon`() {
        val duplicate = legacy()
        val entries = duplicate.get("TetherPokemon") as ListTag
        entries.add(entries[0].copy())
        assertTrue(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, duplicate).read(PastureSaveData.CODEC).isEmpty)
        val reversed = legacy().apply { putIntArray("TetherMinRoamPos", intArrayOf(23, -64, 300)) }
        assertTrue(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, reversed).read(PastureSaveData.CODEC).isEmpty)
    }

    @Test fun `optional saved UUID distinguishes absent data from corrupt present data`() {
        val empty = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, CompoundTag())
        assertNull(empty.readOptionalOrThrow("Owner", UUIDUtil.CODEC))
        val broken = CompoundTag().apply { putString("Owner", "invalid-uuid") }
        val input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, broken)
        assertThrows(IllegalStateException::class.java) { input.readOptionalOrThrow("Owner", UUIDUtil.CODEC) }
        val corruptOwner = legacy().apply { putString("TetherOwnerId", "invalid-uuid") }
        assertTrue(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, corruptOwner).read(PastureSaveData.CODEC).isEmpty)
        val good = CompoundTag().apply { putIntArray("Owner", UUIDUtil.uuidToIntArray(owner)) }
        assertEquals(owner, TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, good)
            .readOptionalOrThrow("Owner", UUIDUtil.CODEC))
    }

    @Test fun `ownerless empty pastures and repeated decoding retain stable identity data`() {
        val tag = legacy().apply { remove("TetherOwnerId"); put("TetherPokemon", ListTag()) }
        val first = PastureSaveData.CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow()
        assertNull(first.ownerId)
        assertTrue(first.tethers.isEmpty())
        val input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, legacy())
        val once = input.read(PastureSaveData.CODEC).orElseThrow()
        assertEquals(once, input.read(PastureSaveData.CODEC).orElseThrow())
        assertEquals(1, once.tethers.size)
    }
}
