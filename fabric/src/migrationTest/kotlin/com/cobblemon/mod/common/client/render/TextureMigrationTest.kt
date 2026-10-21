package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.ScrollingVertexConsumer
import com.mojang.blaze3d.vertex.VertexConsumer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextureMigrationTest {
    @Test fun `non-looping animation stops at final frame without blending back to start`() {
        assertEquals(AnimationTextureFrame(3, 3, 0F), TextureInterpolation.frame(.875F, 4F, 4, false))
        assertEquals(AnimationTextureFrame(3, 3, 0F), TextureInterpolation.frame(100F, 4F, 4, false))
        assertEquals(AnimationTextureFrame(3, 0, .5F), TextureInterpolation.frame(.875F, 4F, 4, true))
    }
    @Test fun `long-running texture timing does not overflow and invalid timing is rejected`() {
        val frame = TextureInterpolation.frame(1E9F, 60F, 4, true)
        assertTrue(frame.current in 0..3)
        assertTrue(frame.blend in 0F..1F)
        assertThrows(IllegalArgumentException::class.java) { TextureInterpolation.frame(0F, 0F, 4, true) }
        assertThrows(IllegalArgumentException::class.java) { TextureInterpolation.frame(Float.NaN, 4F, 4, true) }
    }
    @Test fun `transparent interpolation preserves visible RGB without black fringes`() {
        assertEquals(0x7FFF8040, TextureInterpolation.pixel(0x00FFFFFF, 0xFFFF8040.toInt(), .5F))
        assertEquals(0x7FFF8040, TextureInterpolation.pixel(0xFFFF8040.toInt(), 0x00000000, .5F))
        assertEquals(0xFF7F007F.toInt(), TextureInterpolation.pixel(0xFFFF0000.toInt(), 0xFF0000FF.toInt(), .5F))
    }
    @Test fun `scrolling applies each snapshot UV offset to chained and packed native vertices`() {
        val target = RecordingVertices()
        val first = ScrollingVertexConsumer(target, .25F, -.5F)
        first.addVertex(0F, 0F, 0F).setColor(-1).setUv(.1F, .8F)
        assertEquals(.35F, target.u, 1E-6F)
        assertEquals(.3F, target.v, 1E-6F)
        ScrollingVertexConsumer(target, -.2F, .3F).addVertex(0F, 0F, 0F, -1, .1F, .8F, 0, 0, 0F, 1F, 0F)
        assertEquals(-.1F, target.u, 1E-6F)
        assertEquals(1.1F, target.v, 1E-6F)
        first.setUv(.1F, .8F)
        assertEquals(.35F, target.u, 1E-6F)
    }
    private class RecordingVertices : VertexConsumer {
        var u = 0F; var v = 0F
        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = this
        override fun setColor(color: Int): VertexConsumer = this
        override fun setUv(u: Float, v: Float): VertexConsumer = apply { this.u = u; this.v = v }
        override fun setUv1(u: Int, v: Int): VertexConsumer = this
        override fun setUv2(u: Int, v: Int): VertexConsumer = this
        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun setLineWidth(width: Float): VertexConsumer = this
    }
}
