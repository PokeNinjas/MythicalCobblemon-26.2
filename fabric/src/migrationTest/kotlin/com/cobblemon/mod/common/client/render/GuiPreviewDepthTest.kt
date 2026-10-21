package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.gui.previewDepthExtent
import com.mojang.blaze3d.systems.DeviceInfo
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.Projection
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class GuiPreviewDepthTest {
    @Test fun `translated Charizard profile stays inside native depth clipping at enlarged GUI scales`() {
        // Charizard's original profile has Z=-10; the shared profile adapter adds
        // -4 and scales by 20. Include geometry on both sides of that origin.
        val depths = listOf(-350F, -280F, -210F)
        // Only the backend clip convention needs a device; keep native matrix construction real.
        val device = mock(GpuDevice::class.java)
        val info = mock(DeviceInfo::class.java)
        `when`(device.deviceInfo).thenReturn(info)
        mockStatic(RenderSystem::class.java).use { renderSystem ->
            renderSystem.`when`<GpuDevice> { RenderSystem.getDevice() }.thenReturn(device)
            for (zeroToOne in listOf(false, true)) {
                `when`(info.isZZeroToOne).thenReturn(zeroToOne)
                for (raster in 1..12) {
                    val extent = previewDepthExtent(350F, raster.toFloat())
                    val projection = Projection().apply { setupOrtho(-extent, extent, 128F * raster, 128F * raster, true) }
                        .getMatrix(Matrix4f())
                    val original = Projection().apply { setupOrtho(-1000F, 1000F, 128F * raster, 128F * raster, true) }
                        .getMatrix(Matrix4f())
                    for (z in depths) {
                        val point = Vector3f(32F * raster, 48F * raster, -z * raster)
                        val fixed = projection.transformPosition(point, Vector3f())
                        val old = original.transformPosition(point, Vector3f())
                        assertEquals(old.x, fixed.x)
                        assertEquals(old.y, fixed.y)
                        assertTrue(fixed.z in (if (zeroToOne) 0F else -1F)..1F,
                            "Clipped model at raster scale $raster, depth $z: ${fixed.z}")
                        if (raster >= 5) assertTrue(old.z > 1F, "Fixture must reproduce native clipping")
                    }
                }
            }
        }
    }
}
