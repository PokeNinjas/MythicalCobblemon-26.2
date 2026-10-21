package com.cobblemon.mod.common.api.storage.adapter.flatfile

import java.io.File
import java.io.IOException
import java.util.UUID

/** A damaged existing save is never treated as a missing store. */
object StoreFileRecovery {
    fun <T : Any> load(file: File, decode: (File) -> T): T? {
        val backup = File(file.absolutePath + ".old")
        if (!file.exists() && !backup.exists()) return null
        val failures = mutableListOf<Exception>()
        if (file.exists()) {
            try {
                require(file.length() > 0L) { "Empty store file: $file" }
                return decode(file)
            } catch (e: Exception) { failures.add(e) }
        }
        if (backup.exists()) {
            try {
                require(backup.length() > 0L) { "Empty backup store file: $backup" }
                val loaded = decode(backup)
                // Preserve the unreadable bytes before restoring a fully decoded backup.
                if (file.exists()) file.copyTo(File(file.absolutePath + ".unreadable-" + UUID.randomUUID()))
                AtomicStoreFile.copyAndReplace(backup.toPath(), file.toPath())
                com.mojang.logging.LogUtils.getLogger().warn("Recovered store {} from {}; any unreadable active bytes were preserved separately", file, backup)
                return loaded
            } catch (e: Exception) { failures.add(e) }
        }
        throw IOException("Unable to load store $file; original and backup files were retained. Refusing to create an empty replacement.").also {
            failures.forEach(it::addSuppressed)
        }
    }
}
