package com.cobblemon.mod.common.api.storage

/** Reject damaged or ambiguous records before replacing any live storage contents. */
object StorageLoadValidation {
    fun validateSlotKeys(keys: Set<String>, prefix: String, size: Int) {
        require(size >= 0) { "Invalid storage size: $size" }
        keys.filter { it.startsWith(prefix) && it.removePrefix(prefix).firstOrNull()?.let { c -> c.isDigit() || c == '-' } == true }.forEach { key ->
            val index = key.removePrefix(prefix).toIntOrNull()
            require(index != null && index in 0 until size && key == prefix + index) { "Invalid storage slot: $key (size $size)" }
        }
    }

    fun <T, K> requireUnique(values: Iterable<T>, identity: (T) -> K) {
        val seen = hashSetOf<K>()
        values.forEach { require(seen.add(identity(it))) { "Duplicate Pokemon identity in saved storage: ${identity(it)}" } }
    }
}
