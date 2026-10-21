/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.api.gui.RegionalGuiBlur
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.SamplerCache
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.RenderPipelines
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class RegionalGuiBlurTest {
    @Test fun `clipped overlapping blur regions leave a disjoint exact complement`() {
        val regions = listOf(ScreenRectangle(-1, 1, 4, 3), ScreenRectangle(2, 2, 5, 3),
            ScreenRectangle(99, 99, 2, 2), ScreenRectangle(1, 1, 0, 2))
        val outside = RegionalGuiBlur.outsideRegions(12, 9, 2, regions)
        for (y in 0 until 9) for (x in 0 until 12) {
            val inside = regions.any { x >= it.left() * 2 && x < it.right() * 2 && y >= it.top() * 2 && y < it.bottom() * 2 }
            val restored = outside.count { x >= it.left() && x < it.right() && y >= it.top() && y < it.bottom() }
            assertEquals(if (inside) 0 else 1, restored, "pixel $x,$y")
        }
        assertTrue(RegionalGuiBlur.outsideRegions(12, 9, 2, listOf(ScreenRectangle(0, 0, 6, 5))).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { RegionalGuiBlur.outsideRegions(12, 9, 0, regions) }
    }

    private class Pixels(width: Int, height: Int, usage: Int = USAGE_COPY_SRC or USAGE_COPY_DST) : GpuTexture(usage,
        "pixels", GpuFormat.RGBA8_UNORM, width, height, 1, 1) {
        // Nonuniform colour and alpha expose accidental tinting/blending as well as
        // partial-copy errors. Storage coordinates match OpenGL's bottom-left origin.
        val pixels = IntArray(width * height) { ((it * 73 and 255) shl 24) or (it * 7919 and 0xFFFFFF) }
        var closed = false
        override fun close() { closed = true }
        override fun isClosed() = closed
    }

    private class PixelView(val source: Pixels) : GpuTextureView(source, 0, 1) {
        var closed = false
        override fun close() { closed = true }
        override fun isClosed() = closed
    }

    @Test fun `deferred blur preserves RGBA outside pixels despite native offset-copy endpoints and releases views`() {
        val textures = mutableListOf<Pixels>()
        val views = mutableListOf<PixelView>()
        val copyCalls = mutableListOf<List<Int>>()
        val passes = mutableListOf<RenderPass>()
        val sampler = mock(GpuSampler::class.java)
        val samplerCache = mock(SamplerCache::class.java)
        `when`(samplerCache.getClampToEdge(FilterMode.NEAREST)).thenReturn(sampler)

        fun restorePass(destination: PixelView): RenderPass {
            var source: PixelView? = null
            var scissor: ScreenRectangle? = null
            return mock(RenderPass::class.java) { invocation ->
                when (invocation.method.name) {
                    "setPipeline" -> {
                        assertSame(RenderPipelines.TRACY_BLIT, invocation.getArgument<Any>(0))
                        null
                    }
                    "bindTexture" -> {
                        assertEquals("InSampler", invocation.getArgument<String>(0))
                        source = invocation.getArgument(1)
                        assertSame(sampler, invocation.getArgument<Any>(2))
                        null
                    }
                    "enableScissor" -> {
                        scissor = ScreenRectangle(invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), invocation.getArgument(3))
                        null
                    }
                    "draw" -> {
                        assertEquals(listOf(3, 1, 0, 0), invocation.arguments.toList())
                        val input = requireNotNull(source).source
                        val output = destination.source
                        val bounds = requireNotNull(scissor)
                        for (y in bounds.top() until bounds.bottom()) for (x in bounds.left() until bounds.right()) {
                            output.pixels[y * output.getWidth(0) + x] = input.pixels[y * input.getWidth(0) + x]
                        }
                        null
                    }
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            }.also { passes += it }
        }

        val encoder = mock(CommandEncoder::class.java) { invocation ->
            when (invocation.method.name) {
                "copyTextureToTexture" -> {
                    val src = invocation.getArgument<Pixels>(0)
                    val dst = invocation.getArgument<Pixels>(1)
                    val dx = invocation.getArgument<Int>(3); val dy = invocation.getArgument<Int>(4)
                    val sx = invocation.getArgument<Int>(5); val sy = invocation.getArgument<Int>(6)
                    val x1 = invocation.getArgument<Int>(7); val y1 = invocation.getArgument<Int>(8)
                    copyCalls += listOf(dx, dy, sx, sy, x1, y1)
                    assertEquals(sx, dx); assertEquals(sy, dy)
                    // 26.2 GlCommandEncoder forwards nominal width/height as x1/y1
                    // endpoints. Both old helper rectangles and full snapshots use
                    // equal source/destination origins, so reversed endpoints still
                    // copy the identical bounding rectangle, not width*height pixels.
                    for (y in minOf(dy, y1) until maxOf(dy, y1)) {
                        for (x in minOf(dx, x1) until maxOf(dx, x1)) {
                            dst.pixels[y * dst.getWidth(0) + x] = src.pixels[y * src.getWidth(0) + x]
                        }
                    }
                    null
                }
                "createRenderPass" -> restorePass(invocation.getArgument(1))
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val device = mock(GpuDevice::class.java) { invocation ->
            when (invocation.method.name) {
                "createCommandEncoder" -> encoder
                "createTexture" -> {
                    val usage = invocation.getArgument<Int>(1)
                    assertTrue(usage and GpuTexture.USAGE_TEXTURE_BINDING != 0)
                    Pixels(invocation.getArgument(3), invocation.getArgument(4), usage).also { textures += it }
                }
                "createTextureView" -> PixelView(invocation.getArgument(0)).also { views += it }
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        mockStatic(RenderSystem::class.java).use { renderSystem ->
            renderSystem.`when`<SamplerCache> { RenderSystem.getSamplerCache() }.thenReturn(samplerCache)
            val helper = RegionalGuiBlur()
            val target = Pixels(12, 10)
            val original = target.pixels.copyOf()
            val regions = listOf(ScreenRectangle(2, 1, 2, 2))
            repeat(3) {
                original.copyInto(target.pixels)
                helper.render(device, target, 2, regions) { target.pixels.fill(-1) }
                helper.endFrame()
                for (y in 0 until 10) for (x in 0 until 12) {
                    val index = (9 - y) * 12 + x
                    assertEquals(if (x in 4 until 8 && y in 2 until 6) -1 else original[index], target.pixels[index], "pixel $x,$y")
                }
            }
            assertEquals(1, textures.size)
            assertEquals(1, views.count { it.source === textures[0] })
            assertFalse(views.single { it.source === textures[0] }.closed)
            assertEquals(3, views.count { it.source === target && it.closed })
            assertFalse(target.closed)
            assertEquals(List(3) { listOf(0, 0, 0, 0, 12, 10) }, copyCalls)

            val resized = Pixels(16, 10)
            helper.render(device, resized, 2, regions) { resized.pixels.fill(-1) }
            assertTrue(textures[0].closed)
            assertTrue(views.single { it.source === textures[0] }.closed)
            assertEquals(2, textures.size)
            assertEquals(listOf(0, 0, 0, 0, 16, 10), copyCalls.last())
            repeat(121) { helper.endFrame() }
            assertTrue(textures[1].closed)
            assertTrue(views.all { it.closed })
            assertFalse(resized.closed)
            passes.forEach { pass ->
                verify(pass).close()
                renderSystem.verify { RenderSystem.bindDefaultUniforms(pass) }
            }
            helper.close()
        }
    }
}
