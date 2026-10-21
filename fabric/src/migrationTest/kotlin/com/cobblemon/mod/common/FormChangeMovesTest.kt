package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.BenchedMoves
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.evolution.EvolutionProxy
import com.cobblemon.mod.common.api.pokemon.moves.Learnset
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class FormChangeMovesTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private fun form(learnset: Learnset = Learnset()): FormData = mock(FormData::class.java).also {
        `when`(it.moves).thenReturn(learnset)
    }

    // Run the actual form setter and move mutation code; isolate unrelated ability,
    // network, HP and evolution infrastructure that requires a running game.
    private fun pokemon(oldForm: FormData, level: Int = 100): Pokemon {
        val pokemon = mock(Pokemon::class.java)
        val species = mock(Species::class.java)
        `when`(species.resourceIdentifier).thenReturn(Identifier.fromNamespaceAndPath("cobblemon", "test"))
        `when`(pokemon.species).thenReturn(species)
        `when`(pokemon.persistentData).thenReturn(CompoundTag())
        for ((name, value) in mapOf(
            "species" to species, "persistentData" to pokemon.persistentData,
            "form" to oldForm, "level" to level, "moveSet" to MoveSet(),
            "benchedMoves" to BenchedMoves(),
            "evolutionProxy" to mock(EvolutionProxy::class.java, RETURNS_DEEP_STUBS)
        )) {
            Pokemon::class.java.getDeclaredField(name).apply { isAccessible = true }.set(pokemon, value)
        }
        `when`(pokemon.maxHealth).thenReturn(100)
        doCallRealMethod().`when`(pokemon).form
        doCallRealMethod().`when`(pokemon).level
        doCallRealMethod().`when`(pokemon).moveSet
        doCallRealMethod().`when`(pokemon).benchedMoves
        doCallRealMethod().`when`(pokemon).form = any(FormData::class.java) ?: oldForm
        return pokemon
    }

    @Test fun `leaving a form removes its exclusive signature moves and preserves unrelated taught moves`() {
        val signature = MoveTemplate.dummy("overheat")
        val activeCustom = MoveTemplate.dummy("customactive")
        val benchCustom = MoveTemplate.dummy("custombench")
        val heat = form(Learnset().apply { formChangeMoves += signature })
        val pokemon = pokemon(heat)
        pokemon.moveSet.add(signature.create())
        pokemon.moveSet.add(activeCustom.create(2, 3))
        pokemon.benchedMoves.add(BenchedMove(signature, 2))
        pokemon.benchedMoves.add(BenchedMove(benchCustom, 3))

        pokemon.form = form()

        assertEquals(listOf(activeCustom), pokemon.moveSet.getMoveTemplates())
        assertEquals(2, pokemon.moveSet[1]!!.currentPp)
        assertEquals(3, pokemon.moveSet[1]!!.raisedPpStages)
        assertEquals(listOf(benchCustom), pokemon.benchedMoves.map { it.moveTemplate })
        assertEquals(3, pokemon.benchedMoves.first().ppRaisedStages)
    }

    @Test fun `signature still learnable by the next form keeps existing PP and PP Ups`() {
        val move = MoveTemplate.dummy("sharedsignature")
        val pokemon = pokemon(form(Learnset().apply { formChangeMoves += move }))
        val active = move.create(2, 3)
        pokemon.moveSet.add(active)
        pokemon.form = form(Learnset().apply { tmMoves += move })
        assertSame(active, pokemon.moveSet[0])
        assertEquals(2, active.currentPp)
        assertEquals(3, active.raisedPpStages)
    }

    @Test fun `form signature is level gated using its earliest level entry`() {
        val move = MoveTemplate.dummy("glaciate")
        val signatureForm = form(Learnset().apply {
            formChangeMoves += move
            levelUpMoves[100] = mutableListOf(move)
            levelUpMoves[80] = mutableListOf(move)
        })
        val low = pokemon(form(), 79)
        low.moveSet.add(MoveTemplate.dummy("tackle").create())
        low.form = signatureForm
        assertFalse(move in low.moveSet.getMoveTemplates())
        assertFalse(low.benchedMoves.any { it.moveTemplate == move })
        val ready = pokemon(form(), 80)
        ready.form = signatureForm
        assertEquals(listOf(move), ready.moveSet.getMoveTemplates())
    }

    @Test fun `form cycling does not duplicate already benched signature moves`() {
        val move = MoveTemplate.dummy("glaciallance")
        val from = form(Learnset().apply { tmMoves += move })
        val to = form(Learnset().apply { formChangeMoves += move })
        val pokemon = pokemon(from)
        repeat(4) { pokemon.moveSet.add(MoveTemplate.dummy("move$it").create()) }
        pokemon.benchedMoves.add(BenchedMove(move, 3))
        repeat(3) {
            pokemon.form = to
            pokemon.form = from
        }
        assertEquals(1, pokemon.benchedMoves.count { it.moveTemplate == move })
        assertEquals(3, pokemon.benchedMoves.first().ppRaisedStages)
    }

    @Test fun `same form assignment leaves moves untouched`() {
        val move = MoveTemplate.dummy("overheat")
        val form = form(Learnset().apply { formChangeMoves += move })
        val pokemon = pokemon(form)
        val active = move.create(1, 2)
        pokemon.moveSet.add(active)
        pokemon.form = form
        assertSame(active, pokemon.moveSet[0])
        assertEquals(1, active.currentPp)
    }

    @Test fun `bench-only signature removal emits one synchronization update`() {
        val signature = MoveTemplate.dummy("glaciallance")
        val pokemon = pokemon(form(Learnset().apply { formChangeMoves += signature }))
        pokemon.moveSet.add(MoveTemplate.dummy("tackle").create())
        pokemon.benchedMoves.add(BenchedMove(signature, 2))
        var updates = 0
        pokemon.benchedMoves.changeFunction = { updated ->
            updates++
            assertFalse(updated.any { it.moveTemplate == signature })
        }
        pokemon.form = form()
        assertEquals(1, updates)
    }
}
