package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FirstPersonMountVisibilityTest {
    @Test fun `local first person mount cannot submit a body into water depth`() {
        assertTrue(PokemonRenderer.hideLocalMountBody(firstPerson = true, localPassenger = true))
    }
    @Test fun `third person and other observers still see the mount`() {
        assertFalse(PokemonRenderer.hideLocalMountBody(firstPerson = false, localPassenger = true))
        assertFalse(PokemonRenderer.hideLocalMountBody(firstPerson = true, localPassenger = false))
        assertFalse(PokemonRenderer.hideLocalMountBody(firstPerson = false, localPassenger = false))
    }
}
