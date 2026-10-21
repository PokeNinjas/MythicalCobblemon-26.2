/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

/** Preserves vanilla spawner acceleration and last-tick interpolation without unbounded catch-up loops. */
class SpawnerSpinState(var spawnDelay: Int = 20) {
    var spin = 0.0
    var oSpin = 0.0
    var lastTick = Long.MIN_VALUE

    fun advance(gameTime: Long) {
        if (lastTick == Long.MIN_VALUE || gameTime < lastTick) lastTick = gameTime - 1
        val elapsed = gameTime - lastTick
        if (elapsed > spawnDelay + 1L) {
            val skip = elapsed - spawnDelay - 1L
            repeat(spawnDelay) { step() }
            lastTick += skip
            spin = (spin + (skip % 72L) * 5.0) % 360.0
        }
        while (lastTick < gameTime) step()
    }
    private fun step() {
        lastTick++
        oSpin = spin
        if (spawnDelay > 0) spawnDelay--
        spin = (spin + 1000.0 / (spawnDelay + 200.0)) % 360.0
    }
}
