package com.cobblemon.mod.common

import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.storage.molang.NbtMoLangDataStoreFactory
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.UUID

class MoLangStorageTest {
    @TempDir lateinit var directory: Path
    private val store = NbtMoLangDataStoreFactory
    private val id = UUID.randomUUID()
    private fun file(folder: String = "playermolangdata") = directory.resolve("$folder/${id.toString().take(2)}/$id.dat").toFile()

    @BeforeEach fun setup() { store.savePath = directory; store.cache.clear(); store.dirty.clear() }
    @AfterEach fun cleanup() { store.cache.clear(); store.dirty.clear() }

    @Test fun `custom path loads existing data even when default path is absent`() {
        val custom = file("custom").apply { parentFile.mkdirs() }
        NbtIo.writeCompressed(CompoundTag().apply { putDouble("progress", 7.0) }, custom.toPath())
        assertEquals(7.0, (store.load(id, "custom/").map["progress"] as DoubleValue).value)
        assertFalse(file().exists())
    }

    @Test fun `atomic saves and recovery preserve MoLang values and damaged bytes`() {
        val data = VariableStruct().apply { map["progress"] = DoubleValue(7.0) }
        store.cache[id] = data
        store.markDirty(id)
        store.save(id)
        data.map["progress"] = DoubleValue(9.0)
        store.markDirty(id)
        store.save(id)
        assertFalse(id in store.dirty)
        store.cache.clear()
        assertEquals(9.0, (store.load(id, null).map["progress"] as DoubleValue).value)
        val backup = file().resolveSibling(file().name + ".old")
        val backupBytes = backup.readBytes()
        file().writeText("intentionally damaged test bytes")
        store.cache.clear()
        assertEquals(7.0, (store.load(id, null).map["progress"] as DoubleValue).value)
        assertArrayEquals(backupBytes, backup.readBytes())
        assertArrayEquals(backupBytes, file().readBytes())
        assertEquals("intentionally damaged test bytes", file().parentFile.listFiles()!!.single { ".unreadable-" in it.name }.readText())
    }

    @Test fun `damaged MoLang data without valid backup cannot silently reset progress`() {
        val active = file().apply { parentFile.mkdirs(); writeText("broken active") }
        val backup = active.resolveSibling(active.name + ".old").apply { writeText("broken backup") }
        assertThrows(IOException::class.java) { store.load(id, null) }
        assertFalse(store.cache.containsKey(id))
        assertEquals("broken active", active.readText())
        assertEquals("broken backup", backup.readText())
    }
}
