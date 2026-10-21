package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials
import com.cobblemon.mod.common.client.render.models.blockbench.SnapshotBedrockModel
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.IndexType
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.preprocessor.GlslPreprocessor
import com.mojang.blaze3d.shaders.GpuDebugOptions
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.GpuDeviceBackend
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.StagedVertexBuffer
import net.minecraft.client.renderer.feature.FeatureFrameContext
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.util.Optional
import java.util.OptionalDouble
import java.util.EnumSet

/** Isolated hidden GLFW context; never opens or connects a Minecraft client. */
class LayerAlphaGpuTest {
    private val closeables = mutableListOf<AutoCloseable>()
    private fun <T : AutoCloseable> keep(value: T): T = value.also(closeables::add)

    @Test fun `actual extra-layer pipelines preserve transparent texels and translucent blending`() {
        fun resolver(species: String, number: String): ModelVariationSet {
            val path = "/assets/cobblemon/bedrock/pokemon/resolvers/${number}_${species}/0_${species}_base.json"
            return javaClass.getResourceAsStream(path)!!.bufferedReader().use {
                VaryingRenderableResolver.GSON.fromJson(it, ModelVariationSet::class.java)
            }
        }
        val cofagrigus = resolver("cofagrigus", "0563").variations.first().layers!!.first()
        assertTrue(cofagrigus.emissive)
        assertFalse(cofagrigus.translucent)
        assertEquals("cobblemon:textures/pokemon/0563_cofagrigus/cofagrigus_emissive.png",
            (cofagrigus.texture as StaticModelTextureSupplier).texture.toString())
        val solosis = resolver("solosis", "0577").variations.first().layers!!.first()
        assertTrue(solosis.translucent)
        assertTrue(solosis.translucent_cull)
        assertEquals("cobblemon:textures/pokemon/0577_solosis/solosis_liquid.png",
            (solosis.texture as StaticModelTextureSupplier).texture.toString())

        RenderSystem.initRenderThread()
        assertTrue(glfwInit(), "GLFW initialization failed")
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val window = glfwCreateWindow(32, 32, "Isolated alpha regression", 0, 0)
        assertNotEquals(0L, window)
        var initialized = false
        try {
            val constructor = Class.forName("com.mojang.blaze3d.opengl.GlDevice").getDeclaredConstructor(
                java.lang.Long.TYPE, ShaderSource::class.java, GpuDebugOptions::class.java)
            constructor.isAccessible = true
            val backend = constructor.newInstance(window, shaderSource(), GpuDebugOptions(1, false, false, false)) as GpuDeviceBackend
            val device = GpuDevice(backend) {}
            RenderSystem.initRenderer(device)
            initialized = true
            val id = Identifier.fromNamespaceAndPath("cobblemon", "alpha_regression")
            val pipelines = listOf(
                "native_cutout" to RenderPipelines.ENTITY_CUTOUT_CULL,
                "emissive_cutout" to PokemonMaterials.emissive(id, false).pipeline(),
                "emissive_translucent" to PokemonMaterials.emissive(id, true).pipeline(),
                "native_translucent_cull" to RenderPipelines.ENTITY_TRANSLUCENT_CULL
            )
            println("GPU=" + glGetString(GL_RENDERER))
            val checks = mutableListOf<org.junit.jupiter.api.function.Executable>()
            for ((name, pipeline) in pipelines) {
                for (alpha in listOf(0, 120, 255)) {
                    val result = render(device, pipeline, alpha)
                    println("LAYER_ALPHA $name sourceAlpha=$alpha rgba=${result.joinToString(",")}")
                    checks += org.junit.jupiter.api.function.Executable { if (alpha == 0) {
                        assertArrayEquals(intArrayOf(0, 0, 255, 255), result, "$name transparent texel overwrote base")
                    } else if (name == "emissive_cutout" || name == "native_cutout") {
                        assertArrayEquals(intArrayOf(0, 255, 0, alpha), result, "$name cutout texel incorrect")
                    } else {
                        val expected = intArrayOf(0, alpha, 255 - alpha, 255)
                        expected.indices.forEach { channel ->
                            assertTrue(kotlin.math.abs(expected[channel] - result[channel]) <= 1,
                                "$name sourceAlpha=$alpha channel=$channel expected=${expected[channel]} actual=${result[channel]}")
                        }
                    } }
                }
            }
            for ((name, pipeline) in pipelines) for (alpha in listOf(0, 120, 255)) {
                val result = renderQueued(device, pipeline, alpha)
                println("QUEUED_ALPHA $name sourceAlpha=$alpha rgba=${result.joinToString(",")}")
                checks += org.junit.jupiter.api.function.Executable {
                    val expected = when {
                        alpha == 0 -> intArrayOf(255, 0, 0, 255)
                        name.endsWith("cutout") -> intArrayOf(0, 255, 0, alpha)
                        else -> intArrayOf(255 - alpha, alpha, 0, 255)
                    }
                    expected.indices.forEach { channel -> assertTrue(kotlin.math.abs(expected[channel] - result[channel]) <= 1,
                        "Queued $name sourceAlpha=$alpha channel=$channel expected=${expected[channel]} actual=${result[channel]}") }
                }
            }
            for (species in listOf("cofagrigus", "solosis")) for (layers in listOf(false, true)) for (cull in listOf(false,true))
                renderQueued(device, pipelines.last().second, 120, species, layers, cull)
            assertAll(checks)
        } finally {
            closeables.asReversed().forEach { runCatching { it.close() } }
            if (initialized) RenderSystem.shutdownRenderer()
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

    private fun renderQueued(device: GpuDevice, pipeline: RenderPipeline, alpha: Int, species: String? = null, includeLayers: Boolean = true, forceLegacy: Boolean = false): IntArray {
        val number = if (species == "cofagrigus") "0563" else "0577"
        val dimension = if (species == null) 8 else 256
        val actualRoot = species?.let { name ->
            val json = javaClass.getResourceAsStream("/assets/cobblemon/bedrock/pokemon/models/${number}_${name}/${name}.geo.json")!!.bufferedReader().use { it.readText() }
            com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel.from(json)!!.create().bakeRoot()
        }
        val itemTransform = Matrix4f().scale(.5F, -.5F, -3.5F).translate(1F, -.2F, -.5F)
            .scale(if (species == "cofagrigus") .35F else .7F, if (species == "cofagrigus") .35F else .7F, .15F)
            .rotateY(Math.toRadians(35.0).toFloat())
        val projectionMatrix = if (actualRoot == null) Matrix4f() else {
            val extents = mutableListOf<org.joml.Vector3f>()
            val poses = PoseStack(); poses.mulPose(itemTransform)
            actualRoot.visit(poses) { pose, _, _, cube ->
                for (x in listOf(cube.minX,cube.maxX)) for (y in listOf(cube.minY,cube.maxY)) for (z in listOf(cube.minZ,cube.maxZ))
                    extents += pose.pose().transformPosition(org.joml.Vector3f(x/16F,y/16F,z/16F))
            }
            val cx = (extents.minOf { it.x } + extents.maxOf { it.x }) / 2F
            val cy = (extents.minOf { it.y } + extents.maxOf { it.y }) / 2F
            val cz = (extents.minOf { it.z } + extents.maxOf { it.z }) / 2F
            val span = maxOf(extents.maxOf { it.x }-extents.minOf { it.x }, extents.maxOf { it.y }-extents.minOf { it.y }) * .6F
            Matrix4f().scale(1F/span,1F/span,.1F).translate(-cx,-cy,5F-cz)
        }
        fun assetTexture(suffix: String): GpuTexture {
            val path="/assets/cobblemon/textures/pokemon/${number}_${species}/${species}${suffix}.png"
            return NativeImage.read(javaClass.getResourceAsStream(path)!!).use { image ->
                keep(device.createTexture(path, GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
                    GpuFormat.RGBA8_UNORM,image.width,image.height,1,1)).also { device.createCommandEncoder().writeToTexture(it,image) }
            }
        }
        fun texture(label: String, color: Int, size: Int = 1): GpuTexture {
            val texture = keep(device.createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM, size, size, 1, 1))
            NativeImage(size, size, false).use { image ->
                for (y in 0 until size) for (x in 0 until size) image.setPixel(x, y, color)
                device.createCommandEncoder().writeToTexture(texture, image)
            }
            return texture
        }
        val baseView = keep(device.createTextureView(if (species == null) texture("queued red base", 0xFFFF0000.toInt()) else assetTexture("")))
        val overlayView = keep(device.createTextureView(if (species == null) texture("queued green overlay", (alpha shl 24) or 0x00FF00) else assetTexture(if(species=="cofagrigus") "_emissive" else "_liquid")))
        val whiteView = keep(device.createTextureView(texture("queued white light", -1, 16)))
        val target = keep(device.createTexture("queued target", GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_COPY_DST,
            GpuFormat.RGBA8_UNORM, dimension, dimension, 1, 1))
        val depth = keep(device.createTexture("queued depth", GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_COPY_DST, GpuFormat.D32_FLOAT, dimension, dimension, 1, 1))
        val targetView = keep(device.createTextureView(target))
        val depthView = keep(device.createTextureView(depth))
        fun uniform(size: Int, writer: (Std140Builder) -> Unit): GpuBuffer = MemoryStack.stackPush().use { stack ->
            val builder = Std140Builder.onStack(stack, size)
            writer(builder)
            keep(device.createBuffer({ "queued uniform" }, GpuBuffer.USAGE_UNIFORM, builder.get()))
        }
        val projection = uniform(64) { it.putMat4f(projectionMatrix) }
        val fog = uniform(48) { it.putVec4(0F, 0F, 0F, 0F).putFloat(1000F).putFloat(2000F)
            .putFloat(1000F).putFloat(2000F).putFloat(2000F).putFloat(2000F).align(16) }
        val lighting = uniform(32) { it.putVec3(0F, 0F, 1F).putVec3(0F, 0F, 1F).align(16) }
        RenderSystem.setProjectionMatrix(projection.slice(), ProjectionType.ORTHOGRAPHIC)
        RenderSystem.setShaderFog(fog.slice())
        RenderSystem.setShaderLights(lighting.slice())
        RenderSystem.outputColorTextureOverride = targetView
        RenderSystem.outputDepthTextureOverride = depthView
        device.createCommandEncoder().clearColorAndDepthTextures(target, Vector4f(0F, 0F, 1F, 1F), depth, 0.0)
        val baseId = Identifier.fromNamespaceAndPath("cobblemon", "queued_base")
        val overlayId = Identifier.fromNamespaceAndPath("cobblemon", "queued_overlay")
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        val textureManager = mock(TextureManager::class.java)
        for ((id, view) in listOf(baseId to baseView, overlayId to overlayView)) {
            val abstractTexture = mock(AbstractTexture::class.java)
            `when`(abstractTexture.textureView).thenReturn(view)
            `when`(abstractTexture.sampler).thenReturn(sampler)
            `when`(textureManager.getTexture(id)).thenReturn(abstractTexture)
        }
        val game = mock(Minecraft::class.java)
        val renderer = mock(GameRenderer::class.java)
        val overlay = mock(OverlayTexture::class.java)
        `when`(overlay.textureView).thenReturn(whiteView)
        `when`(renderer.overlayTexture()).thenReturn(overlay)
        `when`(renderer.lightmap()).thenReturn(whiteView)
        `when`(game.textureManager).thenReturn(textureManager)
        val mainTarget = mock(com.mojang.blaze3d.pipeline.RenderTarget::class.java)
        com.mojang.blaze3d.pipeline.RenderTarget::class.java.getDeclaredField("useDepth").apply { isAccessible = true }.setBoolean(mainTarget, true)
        `when`(renderer.mainRenderTarget()).thenReturn(mainTarget)
        Minecraft::class.java.getDeclaredField("gameRenderer").apply { isAccessible = true }.set(game, renderer)
        val staged = keep(StagedVertexBuffer({ "queued alpha staged" }, 4096))
        val context = FeatureFrameContext(mock(net.minecraft.client.renderer.state.OptionsRenderState::class.java),
            mock(net.minecraft.client.gui.Font::class.java), mock(net.minecraft.client.renderer.block.BlockStateModelSet::class.java),
            mock(net.minecraft.client.color.block.BlockColors::class.java), textureManager,
            mock(net.minecraft.client.resources.model.sprite.AtlasManager::class.java), whiteView, staged)
        val cube = ModelPart.Cube(0, 0, -16F, -16F, 8F, 32F, 32F, 0F, 0F, 0F, 0F, false, 64F, 64F, EnumSet.of(Direction.SOUTH))
        val model = SnapshotBedrockModel(actualRoot ?: ModelPart(listOf(cube), emptyMap()))
        val snapshot = model.capture()
        val base = RenderTypes.entityCutoutCull(baseId)
        val layer = RenderType.create("queued extra layer", RenderSetup.builder(pipeline).withTexture("Sampler0", overlayId)
            .useLightmap().useOverlay().createRenderSetup())
        val submissions: List<ModelFeatureRenderer.Submit<*>> = if(species == null) listOf(base, layer).map { type ->
            ModelFeatureRenderer.Submit(type, PoseStack().last(), model, snapshot, 15728880, OverlayTexture.NO_OVERLAY, -1, null, null)
        } else {
            val storage = net.minecraft.client.renderer.SubmitNodeStorage()
            val actualLayer = if(species=="cofagrigus") PokemonMaterials.emissive(overlayId,false) else layer
            val frame = com.cobblemon.mod.common.client.render.item.PokemonItemFrame(snapshot, baseId, itemTransform, -1,
                if(includeLayers) listOf(com.cobblemon.mod.common.client.render.pokemon.PokemonLayerSubmission(actualLayer,-1,species=="cofagrigus")) else emptyList(), true)
            com.cobblemon.mod.common.client.render.item.PokemonItemRenderer().submit(frame,PoseStack(),storage,15728880,OverlayTexture.NO_OVERLAY,false,0)
            val captured=mutableListOf<ModelFeatureRenderer.Submit<*>>()
            storage.drainPhases { phase -> phase.sortInto { node, _ ->
                assertTrue(node is ModelFeatureRenderer.Submit<*>)
                val submit=node as ModelFeatureRenderer.Submit<*>
                captured += if(forceLegacy && captured.isEmpty()) ModelFeatureRenderer.Submit(RenderTypes.entityCutout(baseId),submit.pose(),model,snapshot,submit.lightCoords(),submit.overlayCoords(),submit.tintedColor(),null,null) else submit
            } }
            println("ACTUAL_ITEM $species layers=$includeLayers submissions=${captured.size}")
            captured
        }
        try {
            mockStatic(Minecraft::class.java).use { staticGame ->
                staticGame.`when`<Minecraft> { Minecraft.getInstance() }.thenReturn(game)
                val feature = ModelFeatureRenderer()
                submissions.forEach { feature.prepareGroup(context, listOf(it), false) }
                staged.upload()
                submissions.forEachIndexed { index, submit -> feature.executeGroup(context, index, listOf(submit), false) }
                feature.finishExecute(context)
            }
            glFinish()
            val pixels = MemoryUtil.memAlloc(dimension * dimension * 4)
            try {
                glBindTexture(GL_TEXTURE_2D, (target as GlTexture).glId())
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
                assertEquals(GL_NO_ERROR, glGetError())
                if(species != null) {
                    val image=java.awt.image.BufferedImage(dimension,dimension,java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    for(y in 0 until dimension) for(x in 0 until dimension) {
                        val i=(y*dimension+x)*4
                        fun channel(c:Int)=pixels.get(i+c).toInt() and 255
                        image.setRGB(x,dimension-1-y,(channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
                    }
                    var visibleDetail = 0
                    for (y in 0 until dimension) for (x in 0 until dimension) {
                        val rgb=image.getRGB(x,y)
                        val r=rgb ushr 16 and 255; val g=rgb ushr 8 and 255; val b=rgb and 255
                        if (species=="cofagrigus" && r>100 && g>60 && b<r*.55 && r>g) visibleDetail++
                        if (species=="solosis" && r>180 && g>180 && b<190) visibleDetail++
                    }
                    println("ACTUAL_ITEM_DETAIL $species layers=$includeLayers legacy=$forceLegacy detailPixels=$visibleDetail")
                    if (forceLegacy) assertTrue(visibleDetail<100,"Legacy no-cull positive control must reproduce missing details")
                    else assertTrue(visibleDetail>300,"Actual Pokemon item submit must preserve visible model details")
                    val file=java.io.File("../../verification/layer-alpha-20260916/$species-${if(includeLayers) "layered" else "base"}-${if(forceLegacy) "legacy" else "fixed"}.png")
                    file.parentFile.mkdirs(); javax.imageio.ImageIO.write(image,"png",file)
                }
                return IntArray(4) { pixels.get((dimension/2 * dimension + dimension/2) * 4 + it).toInt() and 255 }
            } finally { MemoryUtil.memFree(pixels) }
        } finally {
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
        }
    }

    private fun render(device: GpuDevice, pipeline: RenderPipeline, alpha: Int): IntArray {
        val bindUsage = GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST
        val source = keep(device.createTexture("alpha source", bindUsage, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1))
        val white = keep(device.createTexture("white light and overlay", bindUsage, GpuFormat.RGBA8_UNORM, 16, 16, 1, 1))
        val target = keep(device.createTexture("alpha target", GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_COPY_SRC,
            GpuFormat.RGBA8_UNORM, 8, 8, 1, 1))
        val depth = keep(device.createTexture("alpha depth", GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.D32_FLOAT, 8, 8, 1, 1))
        val encoder = device.createCommandEncoder()
        NativeImage(1, 1, false).use { image -> image.setPixel(0, 0, (alpha shl 24) or 0x00FF00); encoder.writeToTexture(source, image) }
        NativeImage(16, 16, false).use { image ->
            for (y in 0..15) for (x in 0..15) image.setPixel(x, y, -1)
            encoder.writeToTexture(white, image)
        }
        val sourceView = keep(device.createTextureView(source))
        val whiteView = keep(device.createTextureView(white))
        val targetView = keep(device.createTextureView(target))
        val depthView = keep(device.createTextureView(depth))
        fun uniform(size: Int, writer: (Std140Builder) -> Unit): GpuBuffer = MemoryStack.stackPush().use { stack ->
            val builder = Std140Builder.onStack(stack, size)
            writer(builder)
            keep(device.createBuffer({ "alpha uniform" }, GpuBuffer.USAGE_UNIFORM, builder.get()))
        }
        val projection = uniform(64) { it.putMat4f(Matrix4f()) }
        val fog = uniform(48) { it.putVec4(0F, 0F, 0F, 0F).putFloat(1000F).putFloat(2000F)
            .putFloat(1000F).putFloat(2000F).putFloat(2000F).putFloat(2000F).align(16) }
        val lighting = uniform(32) { it.putVec3(0F, 0F, 1F).putVec3(0F, 0F, 1F).align(16) }
        val transform = RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(), Vector4f(1F))
        val allocator = keep(ByteBufferBuilder(1024))
        val builder = BufferBuilder(allocator, PrimitiveTopology.QUADS, pipeline.getVertexFormatBinding(0)!!)
        for ((x, y) in listOf(-1F to -1F, 1F to -1F, 1F to 1F, -1F to 1F)) {
            builder.addVertex(x, y, .5F).setColor(-1).setUv(.5F, .5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0F, 0F, 1F)
        }
        val mesh = keep(builder.buildOrThrow())
        val vertex = keep(device.createBuffer({ "alpha vertices" }, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer()))
        val indices = MemoryStack.stackPush().use { stack ->
            val bytes = stack.malloc(12)
            for (index in listOf(0, 1, 2, 2, 3, 0)) bytes.putShort(index.toShort())
            bytes.flip()
            keep(device.createBuffer({ "alpha indices" }, GpuBuffer.USAGE_INDEX, bytes))
        }
        encoder.createRenderPass({ "layer alpha" }, targetView, Optional.of(Vector4f(0F, 0F, 1F, 1F)),
            depthView, OptionalDouble.of(0.0)).use { pass ->
            pass.setPipeline(pipeline)
            pass.setUniform("DynamicTransforms", transform)
            pass.setUniform("Projection", projection)
            pass.setUniform("Fog", fog)
            pass.setUniform("Lighting", lighting)
            val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            pass.bindTexture("Sampler0", sourceView, sampler)
            pass.bindTexture("Sampler1", whiteView, sampler)
            pass.bindTexture("Sampler2", whiteView, sampler)
            pass.setVertexBuffer(0, vertex.slice())
            pass.setIndexBuffer(indices, IndexType.SHORT)
            pass.drawIndexed(6, 1, 0, 0, 0)
        }
        encoder.submit()
        glFinish()
        val pixels = MemoryUtil.memAlloc(8 * 8 * 4)
        try {
            glBindTexture(GL_TEXTURE_2D, (target as GlTexture).glId())
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
            assertEquals(GL_NO_ERROR, glGetError())
            return IntArray(4) { pixels.get((4 * 8 + 4) * 4 + it).toInt() and 255 }
        } finally { MemoryUtil.memFree(pixels) }
    }

    private fun shaderSource() = ShaderSource { id, type ->
        val extension = if (type == ShaderType.VERTEX) ".vsh" else ".fsh"
        val text = resource("assets/${id.namespace}/shaders/${id.path}$extension")
        val imported = mutableSetOf<String>()
        val preprocessor = object : GlslPreprocessor() {
            override fun applyImport(quoted: Boolean, name: String): String? {
                val location = if (':' in name) Identifier.parse(name) else Identifier.fromNamespaceAndPath("minecraft", name)
                val path = "assets/${location.namespace}/shaders/include/${location.path}"
                return if (imported.add(path)) resource(path) else null
            }
        }
        preprocessor.process(text).joinToString("\n")
    }

    private fun resource(path: String): String = javaClass.classLoader.getResourceAsStream(path)
        ?.bufferedReader()?.use { it.readText() } ?: error("Missing shader resource: $path")
}

