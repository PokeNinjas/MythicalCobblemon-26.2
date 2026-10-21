/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.block.SpawnerSpinState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpawnerSpinTest {
    @Test fun `catch-up matches tick-by-tick acceleration and previous-frame rotation`() {
        for (gap in listOf(1L, 19L, 20L, 21L, 22L, 73L, 500L, 10000L)) {
            val fast = SpawnerSpinState()
            val reference = SpawnerSpinState()
            fast.advance(100)
            reference.advance(100)
            fast.advance(100 + gap)
            for (time in 101..100 + gap) reference.advance(time)
            assertEquals(reference.spin, fast.spin, 1E-9, "gap $gap")
            assertEquals(reference.oSpin, fast.oSpin, 1E-9, "previous gap $gap")
            assertEquals(reference.spawnDelay, fast.spawnDelay)
            assertEquals(reference.lastTick, fast.lastTick)
        }
    }
    @Test fun `trillion tick gap stays bounded and retains the final interpolation pair`() {
        val state = SpawnerSpinState(0)
        state.advance(0)
        state.advance(1_000_000_000_000L)
        assertEquals(((1_000_000_000_001L % 72L) * 5).toDouble(), state.spin)
        assertEquals(((1_000_000_000_000L % 72L) * 5).toDouble(), state.oSpin)
        assertEquals(1_000_000_000_000L, state.lastTick)
    }
    @Test fun `repeated frame and reversed world clock do not freeze or double advance`() {
        val state = SpawnerSpinState(0)
        state.advance(100)
        val spin = state.spin
        state.advance(100)
        assertEquals(spin, state.spin)
        state.advance(10)
        assertEquals(10L, state.lastTick)
        assertEquals(spin + 5.0, state.spin)
        state.advance(11)
        assertEquals(spin + 10.0, state.spin)
    }
}
