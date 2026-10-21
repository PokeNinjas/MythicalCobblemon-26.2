package com.cobblemon.mod.common.api.storage.player.adapter

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

object PlayerDataPaths {
    /** Keep pre-26.2 Cobblemon paths despite vanilla moving playerdata to players/data. */
    fun prepare(worldRoot: Path, interimRoot: Path, subfolder: String): Path {
        val root = worldRoot.toAbsolutePath().normalize()
        val canonical = root.resolve(subfolder).normalize()
        val interim = interimRoot.toAbsolutePath().normalize().resolve(subfolder).normalize()
        require(canonical.startsWith(root) && canonical != root && interim.startsWith(root))
        if (canonical == interim || !Files.exists(interim)) return root
        if (!Files.isDirectory(interim)) throw IOException("Expected player-data directory: $interim")
        if (Files.list(interim).use { it.findAny().isEmpty }) return root
        // Early private 26.2 builds used the vanilla player's parent folder. Move the
        // complete store, including backups, once. Never choose between conflicting saves.
        if (Files.exists(canonical)) {
            throw IOException("Conflicting player-data directories: $canonical and $interim. Both retained; refusing to replace progress.")
        }
        Files.move(interim, canonical, ATOMIC_MOVE)
        return root
    }
}
