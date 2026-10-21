package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SnowstormVertexTest {
    @Test fun `rectangular particle geometry survives buffer growth with UV color and light intact`() {
        val buffer = SnowstormParticleVertices()
        val count = 2048
        repeat(count) { i ->
            buffer.addVertex(i.toFloat(), (i % 4 * 2).toFloat(), -3F).setUv(.25F, .75F)
                .setColor(0x7F123456).setLight(0x00B00070)
        }
        val captured = mutableListOf<List<Number>>()
        buffer.emit(object : VertexConsumer {
            var current = mutableListOf<Number>()
            override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
                current = mutableListOf(x, y, z); captured += current
            }
            override fun setUv(u: Float, v: Float): VertexConsumer = apply { current += u; current += v }
            override fun setColor(color: Int): VertexConsumer = apply { current += color }
            override fun setUv2(u: Int, v: Int): VertexConsumer = apply { current += u or (v shl 16) }
            override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = this
            override fun setUv1(u: Int, v: Int): VertexConsumer = this
            override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this
            override fun setLineWidth(width: Float): VertexConsumer = this
        })
        assertEquals(count, captured.size)
        for (i in 0 until count) assertEquals(listOf<Number>(i.toFloat(), (i % 4 * 2).toFloat(), -3F, .25F, .75F, 0x7F123456, 0x00B00070), captured[i])
    }
    @Test fun `clearing discards old vertices and incomplete quads are rejected`() {
        val buffer = SnowstormParticleVertices()
        repeat(4) { buffer.addVertex(1F, 2F, 3F) }
        buffer.clear()
        assertEquals(0, buffer.size)
        buffer.addVertex(4F, 5F, 6F)
        assertEquals(1, buffer.size)
        assertThrows(IllegalStateException::class.java) { buffer.emit(SnowstormParticleVertices()) }
    }
}
