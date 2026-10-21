package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.SnapshotBedrockModel
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.model.geom.ModelPart
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Uses the actual native 26.2 baker and bundled NPC assets, without a graphics context. */
class NPCTextureGeometryTest {
    private fun bake(name: String): ModelPart {
        val path = "/assets/cobblemon/bedrock/npcs/models/$name.geo.json"
        val json = requireNotNull(javaClass.getResourceAsStream(path)) { path }.bufferedReader().use { it.readText() }
        return requireNotNull(TexturedModel.from(json)).create().bakeRoot()
    }

    @Test fun `actual Steve and Alex face coordinates span skin pixels rather than one texel`() {
        for (name in listOf("steve", "alex")) {
            val root = bake(name)
            val head = root.getChild("trainer").getChild("body").getChild("torso").getChild("head")
            assertEquals(2, head.cubes.size, name)
            for (cube in head.cubes) for (face in cube.polygons) {
                val vertices = face.vertices()
                val uSpan = vertices.maxOf { it.u() } - vertices.minOf { it.u() }
                val vSpan = vertices.maxOf { it.v() } - vertices.minOf { it.v() }
                assertEquals(8F / 64F, uSpan, 0.00001F, "$name head U span")
                assertEquals(8F / 64F, vSpan, 0.00001F, "$name head V span")
            }
            for (cube in head.cubes) {
                val output = UvConsumer()
                cube.compile(PoseStack().last(), output, 15728880, 0, -1)
                assertEquals(cube.polygons.flatMap { it.vertices().map { v -> v.u() to v.v() } }, output.uvs,
                    "$name native vertex emission must preserve every stored texture coordinate")
            }
            val cubes = root.allParts.toList().flatMap { it.cubes }
            assertTrue(cubes.size >= 12, "$name must bake full body geometry")
            var faces = 0
            for (cube in cubes) for (face in cube.polygons) {
                val vertices = face.vertices()
                assertTrue(vertices.all { it.u().isFinite() && it.v().isFinite() })
                assertTrue(vertices.maxOf { it.u() } > vertices.minOf { it.u() }, "$name collapsed U")
                assertTrue(vertices.maxOf { it.v() } > vertices.minOf { it.v() }, "$name collapsed V")
                faces++
            }
            println("NPC_UV $name cubes=${cubes.size} faces=$faces headPixelSpan=8x8")
        }
    }

    private class UvConsumer : VertexConsumer {
        val uvs = mutableListOf<Pair<Float, Float>>()
        override fun addVertex(x: Float, y: Float, z: Float) = this
        override fun setColor(r: Int, g: Int, b: Int, a: Int) = this
        override fun setColor(argb: Int) = this
        override fun setUv(u: Float, v: Float) = apply { uvs += u to v }
        override fun setUv1(u: Int, v: Int) = this
        override fun setUv2(u: Int, v: Int) = this
        override fun setNormal(x: Float, y: Float, z: Float) = this
        override fun setLineWidth(width: Float) = this
    }

    @Test fun `deferred NPC snapshot preserves every baked cube and texture coordinate`() {
        for (name in listOf("steve", "alex")) {
            val root = bake(name)
            val snapshotModel = SnapshotBedrockModel(root)
            val source = root.allParts.toList()
            val drawing = snapshotModel.root().allParts.toList()
            assertEquals(source.size, drawing.size)
            snapshotModel.setupAnim(snapshotModel.capture())
            source.zip(drawing).forEach { (original, copy) ->
                assertNotSame(original, copy)
                assertSame(original.cubes, copy.cubes, "$name immutable cubes must be shared")
            }
        }
    }
}
