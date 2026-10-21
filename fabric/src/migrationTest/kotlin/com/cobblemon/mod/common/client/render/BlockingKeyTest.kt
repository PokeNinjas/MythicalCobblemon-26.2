package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.keybind.CobblemonBlockingKeyBinding
import com.cobblemon.mod.common.client.keybind.KeybindCategories
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BlockingKeyTest {
    private class Binding : CobblemonBlockingKeyBinding("private.validation.blocking", InputConstants.Type.KEYSYM,
        InputConstants.KEY_F12, KeybindCategories.COBBLEMON_CATEGORY) {
        var presses = 0
        var releases = 0
        override fun onPress() { presses++ }
        override fun onRelease() { releases++ }
    }
    @Test fun `press and release between ticks is delivered exactly once`() {
        val binding = Binding()
        KeyMapping.click(binding.defaultKey)
        binding.onTick()
        assertEquals(1, binding.presses)
        assertEquals(1, binding.releases)
        assertFalse(binding.wasDown)
        binding.onTick()
        assertEquals(1, binding.presses)
        assertEquals(1, binding.releases)
    }
    @Test fun `held press waits for release and consumes its queued click`() {
        val binding = Binding()
        binding.isDown = true
        KeyMapping.click(binding.defaultKey)
        binding.onTick()
        assertEquals(1, binding.presses)
        assertEquals(0, binding.releases)
        binding.isDown = false
        binding.onTick()
        binding.onTick()
        assertEquals(1, binding.presses)
        assertEquals(1, binding.releases)
    }
}
