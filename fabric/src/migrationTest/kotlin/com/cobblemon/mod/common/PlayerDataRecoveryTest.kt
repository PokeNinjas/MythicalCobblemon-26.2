package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreType
import com.cobblemon.mod.common.api.storage.player.adapter.FileBasedPlayerDataStoreBackend
import com.cobblemon.mod.common.api.storage.player.client.ClientInstancedPlayerData
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.UUID

class PlayerDataRecoveryTest {
    @TempDir lateinit var directory: Path
    private data class Data(override val uuid: UUID, val value: String) : InstancedPlayerData {
        var initialized = false
        override fun initialize() { initialized = true }
        override fun toClientData(): ClientInstancedPlayerData = error("Not used by file recovery")
    }
    private class Backend(path: Path) : FileBasedPlayerDataStoreBackend<Data>(
        "fixture", PlayerInstancedDataStoreType(Identifier.parse("cobblemon:fixture"), { error("unused") }, {}), "txt"
    ) {
        var saves = 0
        override val defaultData: (UUID) -> Data = { Data(it, "new-player") }
        init { savePath = path }
        override fun save(playerData: Data) {
            saves++
            filePath(playerData.uuid).apply { parentFile.mkdirs(); writeText("${playerData.uuid}|${playerData.value}") }
        }
        override fun load(uuid: UUID): Data = loadWithFallback(uuid) {
            val pieces = it.readText().split('|', limit = 2)
            Data(UUID.fromString(pieces[0]), pieces[1])
        }
    }

    @Test fun `new player defaults are saved and initialized`() {
        val backend = Backend(directory)
        val id = UUID.randomUUID()
        val loaded = backend.load(id)
        assertEquals(id, loaded.uuid)
        assertEquals("new-player", loaded.value)
        assertTrue(loaded.initialized)
        assertEquals(1, backend.saves)
    }

    @Test fun `damaged primary and backup remain unchanged without default save`() {
        val backend = Backend(directory)
        val id = UUID.randomUUID()
        val primary = backend.filePath(id).apply { parentFile.mkdirs(); writeText("broken current") }
        val backup = backend.filePath(id, "old").apply { writeText("broken backup") }
        assertThrows(IOException::class.java) { backend.load(id) }
        assertEquals("broken current", primary.readText())
        assertEquals("broken backup", backup.readText())
        assertEquals(0, backend.saves)
    }

    @Test fun `valid backup restores player while retaining damaged primary bytes`() {
        val backend = Backend(directory)
        val id = UUID.randomUUID()
        val primary = backend.filePath(id).apply { parentFile.mkdirs(); writeText("damaged original") }
        backend.filePath(id, "old").writeText("$id|saved-progress")
        val loaded = backend.load(id)
        assertEquals("saved-progress", loaded.value)
        assertTrue(loaded.initialized)
        assertEquals("$id|saved-progress", primary.readText())
        assertEquals("damaged original", primary.parentFile.listFiles()!!.single { it.name.contains(".unreadable-") }.readText())
        assertEquals(0, backend.saves)
    }

    @Test fun `record for another player cannot replace this player's data`() {
        val backend = Backend(directory)
        val id = UUID.randomUUID()
        val primary = backend.filePath(id).apply { parentFile.mkdirs(); writeText("${UUID.randomUUID()}|other-player") }
        val original = primary.readText()
        assertThrows(IOException::class.java) { backend.load(id) }
        assertEquals(original, primary.readText())
        assertEquals(0, backend.saves)
    }
}
