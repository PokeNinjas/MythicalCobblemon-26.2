package com.cobblemon.mod.common

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class StructurePoolTest {
    @Test fun `shipped jigsaws have valid identifiers and data driven structures resolve local pools`() {
        val resources = Path.of(System.getProperty("cobblemon.test.resourceRoot", "../common/src/main/resources"))
        val data = resources.resolve("data")
        // Include bundled optional datapacks: their structures are shipped in
        // the base pack while their jigsaw pools live in the integration pack.
        val jsonFiles = Files.walk(resources).use { paths ->
            paths.filter { it.toString().endsWith(".json") && "/worldgen/" in it.toString().replace('\\', '/') }.toList()
        }
        val references = mutableSetOf<String>()
        fun collect(json: JsonElement) {
            when {
                json.isJsonObject -> json.asJsonObject.entrySet().forEach { collect(it.value) }
                json.isJsonArray -> json.asJsonArray.forEach(::collect)
                json.isJsonPrimitive && json.asJsonPrimitive.isString -> references.add(json.asString)
            }
        }
        jsonFiles.forEach { collect(JsonParser.parseString(Files.readString(it))) }
        val poolIds = jsonFiles.mapNotNull {
            val path = it.toString().replace('\\', '/')
            val marker = "/data/cobblemon/worldgen/template_pool/"
            if (marker in path) "cobblemon:" + path.substringAfter(marker).removeSuffix(".json") else null
        }.toSet()
        val structures = Files.walk(data.resolve("cobblemon/structure")).use { paths ->
            paths.filter { it.toString().endsWith(".nbt") }.toList()
        }
        assertTrue(structures.isNotEmpty())
        var jigsaws = 0
        for (file in structures) {
            val root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
            val blocks = root.get("blocks") as ListTag
            for (block in blocks) {
                val tag = (block as CompoundTag).get("nbt") as? CompoundTag ?: continue
                if (tag.getString("id").orElse("") != "minecraft:jigsaw") continue
                jigsaws++
                val pool = tag.getString("pool").orElse("")
                assertTrue(pool.isNotBlank() && !pool.endsWith(":"), "$file contains a blank pool")
                val id = Identifier.tryParse(pool)
                assertNotNull(id, "$file has invalid pool identifier $pool")
                val structureId = "cobblemon:" + data.resolve("cobblemon/structure").relativize(file)
                    .toString().replace('\\', '/').removeSuffix(".nbt")
                // Unreferenced upstream draft structures can contain obsolete
                // pool names; do not invent world-generation content for them.
                if (id!!.namespace == "cobblemon" && structureId in references) {
                    assertTrue(pool in poolIds,
                        "$file references missing pool $pool")
                }
            }
        }
        assertTrue(jigsaws > 0, "The audit must actually cover jigsaw blocks")
    }
}
