/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.item.CobblemonItemProperty
import com.cobblemon.mod.common.client.render.color.CobblemonItemTintSource
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.item.ItemModels
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties
import net.minecraft.client.color.item.ItemTintSources
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ItemDefinitionTest {
    companion object {
        @JvmStatic @BeforeAll fun registerCodecs() {
            ItemModels.bootstrap()
            RangeSelectItemModelProperties.bootstrap()
            ItemTintSources.bootstrap()
            RangeSelectItemModelProperties.ID_MAPPER.put(Identifier.parse("cobblemon:predicate"), CobblemonItemProperty.CODEC)
            ItemTintSources.ID_MAPPER.put(Identifier.parse("cobblemon:item"), CobblemonItemTintSource.CODEC)
        }
    }

    @Test fun `all migrated cuboid item definitions parse through the native client item codec`() {
        val root = Path.of(System.getProperty("cobblemon.test.resourceRoot", "../common/src/main/resources")).resolve("assets/cobblemon/items")
        var parsed = 0
        Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".json") && it.fileName.toString() != "pokemon_model.json" }.forEach { path ->
                val json = JsonParser.parseString(Files.readString(path))
                val result = ClientItem.CODEC.parse(JsonOps.INSTANCE, json)
                assertTrue(result.isSuccess, "$path: ${result.error()}")
                parsed++
            }
        }
        assertEquals(1158, parsed)
    }

    @Test fun `custom predicate preserves namespace and identifier through codec roundtrip`() {
        val source = CobblemonItemProperty(Identifier.parse("cobblemon:ponigiri_overlay"))
        val encoded = CobblemonItemProperty.CODEC.codec().encodeStart(JsonOps.INSTANCE, source).getOrThrow()
        assertEquals(source, CobblemonItemProperty.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow())
    }

    @Test fun `native tint source rejects invalid negative layer indices`() {
        assertTrue(CobblemonItemTintSource.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""{"index":-1}""")).isError)
    }
    @Test fun `all originally translucent disc block variants retain forced native translucent materials`() {
        val root = Path.of(System.getProperty("cobblemon.test.resourceRoot", "../common/src/main/resources")).resolve("assets/cobblemon/models/block")
        for (name in listOf("upgrade", "dubious_disc")) for (amount in 1..4) {
            val json = JsonParser.parseString(Files.readString(root.resolve("${name}_${amount}.json"))).asJsonObject
            for ((_, value) in json.getAsJsonObject("textures").entrySet()) {
                val material = net.minecraft.client.resources.model.sprite.Material.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()
                assertTrue(material.forceTranslucent())
                assertTrue(material.sprite().path.startsWith("block/decorative/"))
            }
        }
    }

}
