package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.JsonModelAdapter
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.blockentity.BlockEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.repository.ProductionInbuiltPosers
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.SharedConstants
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path

class ModelCatalogTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private val assets = Path.of(System.getProperty("cobblemon.test.resourceRoot", "../common/src/main/resources")).resolve("assets/cobblemon")

    @Test fun `every shipped poser directory is registered and all resolver poser references exist`() {
        val registered = VaryingModelRepository.poserDirectories.map { it.first }.toSet()
        val files = Files.walk(assets.resolve("bedrock")).use { paths ->
            paths.filter { it.toString().endsWith(".json") }.toList()
        }
        val poserFiles = files.filter { "/posers/" in it.toString().replace('\\', '/') }
        assertTrue(poserFiles.isNotEmpty())
        for (file in poserFiles) {
            val relative = assets.relativize(file).toString().replace('\\', '/')
            assertTrue(registered.any { relative.startsWith("$it/") }, "Unregistered poser resource: $relative")
        }
        val saved = VaryingModelRepository.posers.toMap()
        try {
            VaryingModelRepository.posers.clear()
            ProductionInbuiltPosers.register()
            val names = VaryingModelRepository.posers.keys.toMutableSet()
            poserFiles.forEach { names.add(Identifier.fromNamespaceAndPath("cobblemon", it.fileName.toString().removeSuffix(".json"))) }
            fun checkReferences(json: JsonElement, path: Path) {
                if (json.isJsonArray) json.asJsonArray.forEach { checkReferences(it, path) }
                if (json.isJsonObject) json.asJsonObject.entrySet().forEach { (key, value) ->
                    if (key == "poser" && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        assertTrue(Identifier.parse(value.asString) in names, "$path references missing poser ${value.asString}")
                    } else checkReferences(value, path)
                }
            }
            for (directory in VaryingModelRepository.variationDirectories) {
                files.filter { it.startsWith(assets.resolve(directory)) }.forEach {
                    checkReferences(JsonParser.parseString(Files.readString(it)), it)
                }
            }
        } finally {
            VaryingModelRepository.posers.clear()
            VaryingModelRepository.posers.putAll(saved)
        }
    }

    @Test fun `gilded chest adapter calls the typed constructor rather than allocating an uninitialized model`() {
        val type = VaryingModelRepository.poserDirectories.single { it.first == "bedrock/block_entities/posers" }.second
        assertEquals(BlockEntityModel::class.java, type)
        val bone = mock(Bone::class.java)
        val surrogate = PosableModel(bone)
        val previousPart = JsonModelAdapter.modelPart
        val previousModel = JsonModelAdapter.model
        try {
            JsonModelAdapter.modelPart = bone
            mockConstruction(BlockEntityModel::class.java, withSettings().defaultAnswer(CALLS_REAL_METHODS)) { model, _ ->
                doReturn(surrogate.functions).`when`(model).functions
                doReturn(surrogate.runtime).`when`(model).runtime
            }.use { construction ->
                val model = VaryingModelRepository.gson.fromJson(
                    Files.readString(assets.resolve("bedrock/block_entities/posers/gilded_chest.json")), type) as BlockEntityModel
                assertEquals(1, construction.constructed().size, "Gson must invoke the registered model constructor")
                assertSame(model, JsonModelAdapter.model)
                assertEquals(0.7F, model.maxScale)
                assertEquals(-0.2F, model.yTranslation)
                assertEquals(setOf("closed", "open"), model.poses.keys)
                assertTrue(model.poses.getValue("closed").transitions.containsKey("open"))
            }
        } finally {
            JsonModelAdapter.modelPart = previousPart
            JsonModelAdapter.model = previousModel
        }
    }
}
