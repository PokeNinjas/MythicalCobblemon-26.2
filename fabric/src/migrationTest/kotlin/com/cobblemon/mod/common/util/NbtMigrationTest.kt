/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.api.net.serializers.OptionalUUIDDataSerializer
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

class NbtMigrationTest {
    private val identity = UUID.fromString("d2bef119-6ea9-432b-a214-708baf9d21cc")
    private val lookup = RegistryAccess.EMPTY

    @Test
    fun `legacy four-int UUID keeps its identity and encoding`() {
        val old = CompoundTag().apply { putIntArray("UUID", UUIDUtil.uuidToIntArray(identity)) }
        assertEquals(identity, old.getUUID("UUID"))
        val saved = CompoundTag().apply { putUUID("UUID", old.getUUID("UUID")) }
        assertEquals(old, saved)
        assertTrue(saved.get("UUID") is IntArrayTag)
    }

    @Test
    fun `missing and malformed required identities fail instead of being regenerated`() {
        val tag = CompoundTag()
        assertFalse(tag.hasUUID("UUID"))
        assertThrows(IllegalArgumentException::class.java) { tag.getUUID("UUID") }
        tag.putIntArray("UUID", intArrayOf(1, 2, 3))
        assertFalse(tag.hasUUID("UUID"))
        assertThrows(IllegalArgumentException::class.java) { tag.getUUID("UUID") }
    }

    @Test
    fun `value API preserves nested custom data and top-level key layout`() {
        val original = CompoundTag().apply {
            putUUID("UUID", identity)
            putString("Species", "cobblemon:magnemite")
            putByte("Flags", 5)
            putFloat("ScaleModifier", 1.25F)
            put("Aspects", ListTag().apply { add(StringTag.valueOf("alpha")) })
            put("PersistentData", CompoundTag().apply {
                putString("third_party:key", "preserve me")
                putLong("Experience", 9876543210L)
            })
        }
        val problems = ProblemReporter.Collector()
        val input = TagValueInput.create(problems, lookup, original)
        val decoded = input.read(LegacyNbtMapCodec.INSTANCE).orElseThrow()
        val output = TagValueOutput.createWithoutContext(problems)
        output.putString("VanillaField", "untouched")
        output.store(LegacyNbtMapCodec.INSTANCE, decoded)
        val expected = original.copy().apply { putString("VanillaField", "untouched") }
        assertEquals(expected, output.buildResult())
        assertTrue(problems.isEmpty, problems.report)
    }

    @Test
    fun `optional battle UUID survives network serialization without becoming an entity reference`() {
        for (value in listOf(Optional.empty(), Optional.of(identity))) {
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), lookup)
            try {
                OptionalUUIDDataSerializer.codec().encode(buffer, value)
                assertEquals(if (value.isPresent) 17 else 1, buffer.readableBytes())
                assertEquals(value, OptionalUUIDDataSerializer.codec().decode(buffer))
                assertEquals(0, buffer.readableBytes())
            } finally {
                buffer.release()
            }
        }
    }

    @Test
    fun `shoulder synchronization preserves nested Pokemon identity aspects and item components`() {
        val original = CompoundTag().apply {
            putString("id", "cobblemon:pokemon")
            putUUID("ShoulderUUID", identity)
            putFloat("ScaleModifier", 1.25F)
            put("Aspects", ListTag().apply { add(StringTag.valueOf("shiny")); add(StringTag.valueOf("female")) })
            put("Item", CompoundTag().apply {
                putString("id", "minecraft:player_head")
                put("components", CompoundTag().apply { putString("minecraft:custom_name", "Visible item") })
            })
        }
        val serializer = com.cobblemon.mod.common.api.net.serializers.ShoulderTagDataSerializer
        val buffer = Unpooled.buffer()
        try {
            serializer.codec().encode(buffer, original)
            val decoded = serializer.codec().decode(buffer)
            assertEquals(original, decoded)
            assertEquals(0, buffer.readableBytes())
            val copy = serializer.copy(decoded)
            copy.getCompoundOrEmpty("Item").getCompoundOrEmpty("components").putString("minecraft:custom_name", "Changed")
            assertEquals("Visible item", decoded.getCompoundOrEmpty("Item").getCompoundOrEmpty("components").getStringOr("minecraft:custom_name", ""))
            assertNotEquals(copy, decoded)
        } finally { buffer.release() }
    }

    @Test
    fun `shoulder clear uses a valid empty tracked tag`() {
        val serializer = com.cobblemon.mod.common.api.net.serializers.ShoulderTagDataSerializer
        val buffer = Unpooled.buffer()
        try {
            serializer.codec().encode(buffer, CompoundTag())
            assertTrue(serializer.codec().decode(buffer).isEmpty)
            assertEquals(0, buffer.readableBytes())
        } finally { buffer.release() }
    }
}
