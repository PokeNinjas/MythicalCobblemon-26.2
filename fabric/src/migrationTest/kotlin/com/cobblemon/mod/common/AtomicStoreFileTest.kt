package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.storage.adapter.flatfile.AtomicStoreFile
import java.io.IOException
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AtomicStoreFileTest {
    @TempDir lateinit var directory: Path

    @Test fun `replacement publishes full new save and rotates previous complete save`() {
        val active = directory.resolve("party.dat").toFile().apply { writeText("current pokemon") }
        val backup = directory.resolve("party.dat.old").toFile().apply { writeText("older pokemon") }
        val temp = directory.resolve("party.dat.tmp").toFile().apply { writeText("new pokemon") }
        AtomicStoreFile.commit(temp.toPath(), active.toPath())
        assertEquals("new pokemon", active.readText())
        assertEquals("current pokemon", backup.readText())
        assertFalse(temp.exists())
        assertEquals(2, directory.toFile().listFiles()!!.size)
    }

    @Test fun `first save publishes without inventing a backup`() {
        val active = directory.resolve("player.json")
        val temp = directory.resolve("player.json.tmp").toFile().apply { writeText("new player") }
        AtomicStoreFile.commit(temp.toPath(), active)
        assertEquals("new player", active.toFile().readText())
        assertEquals(1, directory.toFile().listFiles()!!.size)
    }

    @Test fun `missing or empty prepared save cannot touch current data or backup`() {
        val active = directory.resolve("party.dat").toFile().apply { writeText("current") }
        val backup = directory.resolve("party.dat.old").toFile().apply { writeText("backup") }
        val temp = directory.resolve("party.dat.tmp").toFile()
        assertThrows(IOException::class.java) { AtomicStoreFile.commit(temp.toPath(), active.toPath()) }
        temp.createNewFile()
        assertThrows(IOException::class.java) { AtomicStoreFile.commit(temp.toPath(), active.toPath()) }
        assertEquals("current", active.readText())
        assertEquals("backup", backup.readText())
    }

    @Test fun `backup publication failure preserves active prepared and obstructing files`() {
        val active = directory.resolve("party.dat").toFile().apply { writeText("current") }
        val temp = directory.resolve("party.dat.tmp").toFile().apply { writeText("new") }
        val obstruction = directory.resolve("party.dat.old/keep.txt").toFile().apply { parentFile.mkdirs(); writeText("keep") }
        assertThrows(IOException::class.java) { AtomicStoreFile.commit(temp.toPath(), active.toPath()) }
        assertEquals("current", active.readText())
        assertEquals("new", temp.readText())
        assertEquals("keep", obstruction.readText())
    }

    @Test fun `recovery replacement retains source backup`() {
        val active = directory.resolve("party.dat").toFile().apply { writeText("damaged") }
        val backup = directory.resolve("party.dat.old").toFile().apply { writeText("valid backup") }
        AtomicStoreFile.copyAndReplace(backup.toPath(), active.toPath())
        assertEquals("valid backup", active.readText())
        assertEquals("valid backup", backup.readText())
    }

    @Test fun `active path cannot be used as its own temporary file`() {
        val active = directory.resolve("party.dat").toFile().apply { writeText("current") }
        assertThrows(IllegalArgumentException::class.java) { AtomicStoreFile.commit(active.toPath(), active.toPath()) }
        assertEquals("current", active.readText())
    }
}
