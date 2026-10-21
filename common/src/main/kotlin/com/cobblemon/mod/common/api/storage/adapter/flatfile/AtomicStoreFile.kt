package com.cobblemon.mod.common.api.storage.adapter.flatfile

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/** Publish complete files by same-directory rename; never truncate the active save. */
object AtomicStoreFile {
    fun commit(prepared: Path, active: Path) {
        require(prepared.toAbsolutePath().normalize().parent == active.toAbsolutePath().normalize().parent) {
            "Prepared save must share the active save's directory: $prepared"
        }
        require(prepared.toAbsolutePath().normalize() != active.toAbsolutePath().normalize())
        forceCompleteFile(prepared)
        if (Files.exists(active)) {
            copyAndReplace(active, active.resolveSibling("${active.fileName}.old"))
        }
        // If the filesystem cannot atomically replace, fail with both complete files retained.
        Files.move(prepared, active, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    /** Also used for recovery: do not rotate a corrupt active save over its valid backup. */
    fun copyAndReplace(source: Path, target: Path) {
        if (!Files.isRegularFile(source) || Files.size(source) == 0L) {
            throw IOException("Refusing to publish missing or empty save: $source")
        }
        val staged = Files.createTempFile(target.toAbsolutePath().parent, "${target.fileName}.", ".pending")
        Files.copy(source, staged, REPLACE_EXISTING)
        forceCompleteFile(staged)
        // A failure deliberately retains the staged bytes for inspection/recovery.
        Files.move(staged, target, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    private fun forceCompleteFile(path: Path) {
        if (!Files.isRegularFile(path) || Files.size(path) == 0L) {
            throw IOException("Refusing to publish missing or empty save: $path")
        }
        FileChannel.open(path, WRITE).use { it.force(true) }
    }
}
