package com.cobblemon.mod.common.client.render

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2fStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import javax.imageio.ImageIO

class SignEditorBackgroundTest {
    @Test fun `unrelated woods use their existing renderer`() {
        val graphics = mock(GuiGraphicsExtractor::class.java)
        assertFalse(SignEditorBackground.draw(graphics, "oak", false))
        assertFalse(SignEditorBackground.draw(graphics, "anothermod:saccharine", true))
        verifyNoInteractions(graphics)
    }

    @Test fun `both custom woods draw standing and wall previews`() {
        for (wood in listOf("apricorn", "saccharine")) {
            for (wall in listOf(false, true)) {
                val graphics = mock(GuiGraphicsExtractor::class.java)
                `when`(graphics.pose()).thenReturn(Matrix3x2fStack(4))
                assertTrue(SignEditorBackground.draw(graphics, "cobblemon:$wood", wall))
                val draws = mockingDetails(graphics).invocations.filter { it.method.name == "blit" }
                assertEquals(if (wall) 1 else 2, draws.size)
                for (draw in draws) {
                    assertEquals("minecraft:textures/entity/signs/$wood.png", draw.arguments[1].toString())
                }
            }
        }
    }

    @Test fun `preview textures exist with native dimensions and hanging art is unchanged`() {
        for (wood in listOf("apricorn", "saccharine")) {
            val board = javaClass.getResourceAsStream("/assets/minecraft/textures/entity/signs/$wood.png")!!.use(ImageIO::read)
            assertEquals(64, board.width)
            assertEquals(32, board.height)
            val old = javaClass.getResourceAsStream("/assets/minecraft/textures/gui/hanging_signs/$wood.png")!!.use { it.readBytes() }
            val current = javaClass.getResourceAsStream("/assets/cobblemon/textures/gui/hanging_signs/$wood.png")!!.use { it.readBytes() }
            assertArrayEquals(old, current)
            val hanging = current.inputStream().use(ImageIO::read)
            assertEquals(16, hanging.width)
            assertEquals(16, hanging.height)
        }
    }
}
