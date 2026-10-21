package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.storage.StorageLoadValidation
import com.cobblemon.mod.common.api.storage.adapter.flatfile.StoreFileRecovery
import java.io.IOException
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StorageRecoveryTest {
    @TempDir lateinit var directory: Path
    private fun decode(file: java.io.File): String = file.readText().also { require(it.startsWith("valid:")) }

    @Test fun `missing store is the only case that creates no loaded result`() {
        val file = directory.resolve("party.dat").toFile()
        assertNull(StoreFileRecovery.load(file, ::decode))
        assertFalse(file.exists())
    }

    @Test fun `valid active save wins over older backup`() {
        val file = directory.resolve("party.dat").toFile().apply { writeText("valid:new") }
        val backup = directory.resolve("party.dat.old").toFile().apply { writeText("valid:old") }
        assertEquals("valid:new", StoreFileRecovery.load(file, ::decode))
        assertEquals("valid:old", backup.readText())
        assertEquals(2, directory.toFile().listFiles()!!.size)
    }

    @Test fun `recovery preserves corrupt bytes and valid backup`() {
        val file = directory.resolve("party.dat").toFile().apply { writeText("corrupt:all original bytes") }
        val backup = directory.resolve("party.dat.old").toFile().apply { writeText("valid:old") }
        assertEquals("valid:old", StoreFileRecovery.load(file, ::decode))
        assertEquals("valid:old", file.readText())
        assertEquals("valid:old", backup.readText())
        val preserved = directory.toFile().listFiles()!!.single { it.name.startsWith("party.dat.unreadable-") }
        assertEquals("corrupt:all original bytes", preserved.readText())
    }

    @Test fun `two unreadable saves are retained and never become an empty store`() {
        val file = directory.resolve("party.dat").toFile().apply { writeText("bad active") }
        val backup = directory.resolve("party.dat.old").toFile().apply { writeText("bad backup") }
        val error = assertThrows(IOException::class.java) { StoreFileRecovery.load(file, ::decode) }
        assertEquals(2, error.suppressed.size)
        assertEquals("bad active", file.readText())
        assertEquals("bad backup", backup.readText())
        assertEquals(2, directory.toFile().listFiles()!!.size)
    }

    @Test fun `empty existing save does not count as missing`() {
        val file = directory.resolve("party.dat").toFile().apply { createNewFile() }
        assertThrows(IOException::class.java) { StoreFileRecovery.load(file, ::decode) }
        assertTrue(file.exists())
        assertEquals(0L, file.length())
    }

    @Test fun `missing active save recovers valid backup`() {
        val file = directory.resolve("party.dat").toFile()
        directory.resolve("party.dat.old").toFile().writeText("valid:backup")
        assertEquals("valid:backup", StoreFileRecovery.load(file, ::decode))
        assertEquals("valid:backup", file.readText())
    }

    @Test fun `slot validation allows gaps but rejects out of range and ambiguous keys`() {
        StorageLoadValidation.validateSlotKeys(setOf("SlotCount", "Slot0", "Slot5"), "Slot", 6)
        for (key in listOf("Slot6", "Slot-1", "Slot00", "Slot1x", "Slot999999999999999")) {
            assertThrows(IllegalArgumentException::class.java) { StorageLoadValidation.validateSlotKeys(setOf(key), "Slot", 6) }
        }
    }

    @Test fun `duplicate identities are rejected without deleting either record`() {
        val records = mutableListOf("uuid-a" to "first", "uuid-a" to "second")
        assertThrows(IllegalArgumentException::class.java) { StorageLoadValidation.requireUnique(records) { it.first } }
        assertEquals(listOf("uuid-a" to "first", "uuid-a" to "second"), records)
        StorageLoadValidation.requireUnique(listOf("uuid-a", "uuid-b")) { it }
    }
}
