package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.ShowdownActionRequest
import com.cobblemon.mod.common.battles.ShowdownPokemon
import com.cobblemon.mod.common.battles.ShowdownSide
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class BattleSwitchEligibilityTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private fun candidate(active: Boolean, reviving: Boolean = false, health: Int = 40): BattlePokemon {
        val uuid = UUID.randomUUID()
        val pokemon = mock(Pokemon::class.java)
        `when`(pokemon.uuid).thenReturn(uuid)
        `when`(pokemon.currentHealth).thenReturn(health)
        val actor = mock(BattleActor::class.java)
        val battle = mock(PokemonBattle::class.java)
        `when`(actor.battle).thenReturn(battle)
        `when`(battle.activePokemon).thenReturn(emptyList()) // queued switch has not animated yet
        val request = ShowdownActionRequest().apply {
            side = ShowdownSide().apply {
                this.pokemon = listOf(ShowdownPokemon().apply {
                    details = "Pikachu, $uuid"
                    this.active = active
                    this.reviving = reviving
                })
            }
        }
        `when`(actor.request).thenReturn(request)
        return BattlePokemon(pokemon).also { it.actor = actor }
    }

    @Test fun `engine active target cannot be switched in before local active slot catches up`() {
        assertFalse(candidate(active = true).canBeSentOut())
        assertTrue(candidate(active = false).canBeSentOut())
    }

    @Test fun `revival and reserved choices retain existing eligibility rules`() {
        assertTrue(candidate(active = false, reviving = true, health = 0).canBeSentOut())
        assertFalse(candidate(active = true, reviving = true, health = 0).canBeSentOut())
        assertFalse(candidate(active = false, health = 0).canBeSentOut())
        assertFalse(candidate(active = false).apply { willBeSwitchedIn = true }.canBeSentOut())
    }
}
