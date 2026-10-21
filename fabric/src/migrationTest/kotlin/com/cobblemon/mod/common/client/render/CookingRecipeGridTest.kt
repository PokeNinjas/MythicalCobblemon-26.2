/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.api.cooking.CookingRecipeGrid
import java.util.Optional
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CookingRecipeGridTest {
    @Test fun `all three ingredient graphs agree with exhaustive assignment`() {
        val permutations = listOf(listOf(0,1,2),listOf(0,2,1),listOf(1,0,2),listOf(1,2,0),listOf(2,0,1),listOf(2,1,0))
        for (mask in 0 until (1 shl 9)) {
            val accepts = { ingredient: Int, item: Int -> mask and (1 shl (ingredient * 3 + item)) != 0 }
            val expected = permutations.any { choices -> choices.indices.all { accepts(it, choices[it]) } }
            assertEquals(expected, CookingRecipeGrid.matchAssignments(3, 3, accepts), "graph $mask")
        }
        assertFalse(CookingRecipeGrid.matchAssignments(2, 3) { _, _ -> true })
        assertFalse(CookingRecipeGrid.matchAssignments(0, 0) { _, _ -> true })
        assertTrue(CookingRecipeGrid.matchAssignments(9, 9) { ingredient, item -> item <= 8 - ingredient })
    }

    @Test fun `every shaped size places into the same centered three by three ingredient grid`() {
        for (width in 1..3) for (height in 1..3) {
            val pattern = List(width * height) { if (it == 1) Optional.empty<Int>() else Optional.of(it) }
            val padded = CookingRecipeGrid.pad(width, height, pattern)
            assertEquals(9, padded.size)
            val left = if (width == 1) 1 else 0; val top = if (height == 1) 1 else 0
            for (y in 0..2) for (x in 0..2) {
                val expected = if (x in left until left + width && y in top until top + height)
                    pattern[(y-top)*width+x-left] else Optional.empty<Int>()
                assertEquals(expected, padded[y*3+x])
            }
        }
        assertThrows(IllegalArgumentException::class.java) { CookingRecipeGrid.pad(4, 1, listOf(Optional.of(1))) }
    }
}
