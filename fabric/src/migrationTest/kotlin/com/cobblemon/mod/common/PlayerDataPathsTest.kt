package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.storage.player.adapter.PlayerDataPaths
import net.minecraft.world.level.storage.LevelResource
import java.io.IOException
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PlayerDataPathsTest {
    @TempDir lateinit var directory: Path
    private fun file(name: String, content: String) = directory.resolve(name).toFile().apply {
        parentFile.mkdirs(); writeText(content)
    }

    @Test fun `native 26_2 player directory does not determine the Cobblemon root`() {
        val nativePlayers = directory.resolve(LevelResource.PLAYER_DATA_DIR.id)
        assertEquals(directory.resolve("players/data"), nativePlayers)
        assertEquals(directory, PlayerDataPaths.prepare(directory.resolve("."), nativePlayers.parent, "pokedex"))
    }

    @Test fun `original root data remains in place and byte identical`() {
        val original = file("pokedex/ab/player.nbt", "original progress")
        PlayerDataPaths.prepare(directory, directory.resolve("players"), "pokedex")
        assertEquals("original progress", original.readText())
        assertFalse(directory.resolve("players/pokedex").toFile().exists())
    }

    @Test fun `interim private layout moves all records and backups exactly once`() {
        file("players/pokedex/ab/player.nbt", "progress")
        file("players/pokedex/ab/player.nbt.old", "backup")
        file("players/pokedex/ab/player.nbt.unreadable-preserved", "damaged bytes")
        repeat(2) { PlayerDataPaths.prepare(directory, directory.resolve("players"), "pokedex") }
        assertEquals("progress", directory.resolve("pokedex/ab/player.nbt").toFile().readText())
        assertEquals("backup", directory.resolve("pokedex/ab/player.nbt.old").toFile().readText())
        assertEquals("damaged bytes", directory.resolve("pokedex/ab/player.nbt.unreadable-preserved").toFile().readText())
        assertFalse(directory.resolve("players/pokedex").toFile().exists())
    }

    @Test fun `conflicting populated directories are retained without choosing a winner`() {
        val original = file("pokedex/ab/player.nbt", "original")
        val interim = file("players/pokedex/ab/player.nbt", "interim")
        assertThrows(IOException::class.java) { PlayerDataPaths.prepare(directory, directory.resolve("players"), "pokedex") }
        assertEquals("original", original.readText())
        assertEquals("interim", interim.readText())
    }

    @Test fun `empty interim directory does not shadow existing progress`() {
        val original = file("pokedex/ab/player.nbt", "original")
        directory.resolve("players/pokedex").toFile().mkdirs()
        PlayerDataPaths.prepare(directory, directory.resolve("players"), "pokedex")
        assertEquals("original", original.readText())
    }

    @Test fun `paths outside the world are refused before moving data`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayerDataPaths.prepare(directory, directory.resolve("players"), "../../outside")
        }
    }
}
