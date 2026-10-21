package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.moves.*
import com.cobblemon.mod.common.api.pokemon.evolution.EvolutionProxy
import com.cobblemon.mod.common.api.pokemon.moves.Learnset
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.pokemon.*
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class FusionProgressTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private fun form(signature: MoveTemplate? = null): FormData = mock(FormData::class.java).also {
        `when`(it.moves).thenReturn(Learnset().apply { signature?.let { formChangeMoves += it } })
    }

    private fun pokemon(old: FormData, speciesName: String = "calyrex", data: CompoundTag = CompoundTag()): Pokemon {
        val pokemon = mock(Pokemon::class.java)
        val species = mock(Species::class.java)
        `when`(species.resourceIdentifier).thenReturn(Identifier.fromNamespaceAndPath("cobblemon", speciesName))
        `when`(pokemon.species).thenReturn(species)
        `when`(pokemon.persistentData).thenReturn(data)
        for ((name, value) in mapOf(
            "species" to species, "persistentData" to data,
            "form" to old, "level" to 100, "moveSet" to MoveSet(), "benchedMoves" to BenchedMoves(),
            "rideBoosts" to mutableMapOf<RidingStat, Float>(),
            "evolutionProxy" to mock(EvolutionProxy::class.java, RETURNS_DEEP_STUBS)
        )) Pokemon::class.java.getDeclaredField(name).apply { isAccessible = true }.set(pokemon, value)
        `when`(pokemon.maxHealth).thenReturn(100)
        doCallRealMethod().`when`(pokemon).form
        doCallRealMethod().`when`(pokemon).level
        doCallRealMethod().`when`(pokemon).moveSet
        doCallRealMethod().`when`(pokemon).benchedMoves
        doCallRealMethod().`when`(pokemon).form = any(FormData::class.java) ?: old
        doCallRealMethod().`when`(pokemon).restoreRideBoosts(anyMap())
        doCallRealMethod().`when`(pokemon).setRideBoosts(anyMap())
        doCallRealMethod().`when`(pokemon).getRideBoosts()
        return pokemon
    }

    @Test fun `both Calyrex rider signatures replace Confusion in a full moveset without losing other moves`() {
        for (name in listOf("glaciallance", "astralbarrage")) {
            val pokemon = pokemon(form())
            val unrelated = (0..2).map { MoveTemplate.dummy("taught$it").create(1, 2) }
            pokemon.moveSet.add(unrelated[0])
            pokemon.moveSet.add(MoveTemplate.dummy("confusion").create())
            pokemon.moveSet.add(unrelated[1])
            pokemon.moveSet.add(unrelated[2])
            pokemon.form = form(MoveTemplate.dummy(name))
            assertEquals(name, pokemon.moveSet[1]!!.name)
            assertSame(unrelated[0], pokemon.moveSet[0])
            assertSame(unrelated[1], pokemon.moveSet[2])
            assertSame(unrelated[2], pokemon.moveSet[3])
            assertTrue(pokemon.benchedMoves.none { it.moveTemplate.name == name })
        }
    }

    @Test fun `Calyrex signature already benched can replace Confusion with its PP Ups`() {
        val signature = MoveTemplate.dummy("glaciallance")
        val pokemon = pokemon(form())
        pokemon.moveSet.add(MoveTemplate.dummy("confusion").create())
        repeat(3) { pokemon.moveSet.add(MoveTemplate.dummy("taught$it").create()) }
        pokemon.benchedMoves.add(BenchedMove(signature, 3))
        pokemon.form = form(signature)
        assertEquals(signature, pokemon.moveSet[0]!!.template)
        assertEquals(3, pokemon.moveSet[0]!!.raisedPpStages)
        assertTrue(pokemon.benchedMoves.none { it.moveTemplate == signature })
    }

    @Test fun `full moveset without Confusion keeps every move and benches signature`() {
        val pokemon = pokemon(form())
        val moves = (0..3).map { MoveTemplate.dummy("taught$it").create(1, 2) }
        moves.forEach { pokemon.moveSet.add(it) }
        val signature = MoveTemplate.dummy("astralbarrage")
        pokemon.form = form(signature)
        moves.forEachIndexed { index, move -> assertSame(move, pokemon.moveSet[index]) }
        assertEquals(signature, pokemon.benchedMoves.single().moveTemplate)
    }

    @Test fun `exclusive signature PP survives unfusion persisted memory and refusion without refilling`() {
        val signature = MoveTemplate.dummy("glaciallance")
        val base = form()
        val fused = form(signature)
        val pokemon = pokemon(fused)
        pokemon.moveSet.add(signature.create(0, 3))
        pokemon.moveSet.add(MoveTemplate.dummy("tackle").create())
        pokemon.form = base
        assertFalse(signature in pokemon.moveSet.getMoveTemplates())
        // Same persistence envelope used by PokemonP2, including save/reload while unfused.
        val json = CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, pokemon.persistentData).getOrThrow()
        val saved = CompoundTag.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()
        val loaded = pokemon(base, data = saved)
        loaded.moveSet.add(MoveTemplate.dummy("tackle").create())
        repeat(3) {
            loaded.form = fused
            val move = loaded.moveSet.first { it.template == signature }
            assertEquals(0, move.currentPp)
            assertEquals(3, move.raisedPpStages)
            loaded.form = base
        }
    }

    @Test fun `Ice and Shadow signature memories remain independent`() {
        val ice = MoveTemplate.dummy("glaciallance")
        val shadow = MoveTemplate.dummy("astralbarrage")
        val iceForm = form(ice)
        val shadowForm = form(shadow)
        val pokemon = pokemon(iceForm)
        pokemon.moveSet.add(ice.create(0, 3))
        pokemon.form = shadowForm
        pokemon.moveSet.first { it.template == shadow }.apply { raisedPpStages = 1; currentPp = 0 }
        pokemon.form = iceForm
        assertEquals(3, pokemon.moveSet.single().raisedPpStages)
        pokemon.form = shadowForm
        assertEquals(1, pokemon.moveSet.single().raisedPpStages)
        assertEquals(0, pokemon.moveSet.single().currentPp)
    }

    @Test fun `benched signature PP Ups survive a form cycle`() {
        val signature = MoveTemplate.dummy("glaciallance")
        val fused = form(signature)
        val pokemon = pokemon(fused)
        repeat(4) { pokemon.moveSet.add(MoveTemplate.dummy("taught$it").create()) }
        pokemon.benchedMoves.add(BenchedMove(signature, 3))
        pokemon.form = form()
        assertTrue(pokemon.benchedMoves.none { it.moveTemplate == signature })
        pokemon.form = fused
        assertEquals(3, pokemon.benchedMoves.single().ppRaisedStages)
    }

    @Test fun `saved ride training survives an unridable form but training setter retains current form limit`() {
        val pokemon = pokemon(form())
        `when`(pokemon.getMaxRideBoost(RidingStat.SPEED)).thenReturn(0F)
        pokemon.restoreRideBoosts(mapOf(RidingStat.SPEED to 7F))
        assertEquals(7F, pokemon.getRideBoosts()[RidingStat.SPEED])
        pokemon.form = form()
        assertEquals(7F, pokemon.getRideBoosts()[RidingStat.SPEED])
        pokemon.setRideBoosts(mapOf(RidingStat.SPEED to 7F))
        assertEquals(0F, pokemon.getRideBoosts()[RidingStat.SPEED])
    }

    @Test fun `invalid stored ride boosts are not applied`() {
        val pokemon = pokemon(form())
        pokemon.restoreRideBoosts(mapOf(RidingStat.SPEED to Float.NaN, RidingStat.JUMP to -1F,
            RidingStat.SKILL to Float.POSITIVE_INFINITY, RidingStat.STAMINA to 3F))
        assertEquals(mapOf(RidingStat.STAMINA to 3F), pokemon.getRideBoosts())
    }

    @Test fun `unfusion restores only the Confusion slot replaced in this cycle and keeps its PP`() {
        val base = form()
        val fused = form(MoveTemplate.dummy("glaciallance"))
        val pokemon = pokemon(base)
        pokemon.moveSet.add(MoveTemplate.dummy("tackle").create())
        pokemon.moveSet.add(MoveTemplate.dummy("confusion").create(0, 3))
        repeat(2) { pokemon.moveSet.add(MoveTemplate.dummy("taught$it").create()) }
        pokemon.form = fused
        pokemon.moveSet.swapMove(1, 2)
        pokemon.form = base
        assertEquals("confusion", pokemon.moveSet[2]!!.name)
        assertEquals(0, pokemon.moveSet[2]!!.currentPp)
        assertEquals(3, pokemon.moveSet[2]!!.raisedPpStages)
        assertEquals(4, pokemon.moveSet.count())
    }

    @Test fun `unfusion never overwrites a newly taught move or resurrects old Confusion on a later cycle`() {
        val signature = MoveTemplate.dummy("glaciallance")
        val base = form()
        val fused = form(signature)
        val pokemon = pokemon(base)
        pokemon.moveSet.add(MoveTemplate.dummy("confusion").create())
        pokemon.form = fused
        val taught = MoveTemplate.dummy("newtaught").create(0, 3)
        pokemon.moveSet.setMove(0, taught)
        pokemon.form = base
        assertSame(taught, pokemon.moveSet[0])
        assertFalse(pokemon.moveSet.any { it.name == "confusion" })
        pokemon.form = fused
        pokemon.form = base
        assertSame(taught, pokemon.moveSet[0])
        assertFalse(pokemon.moveSet.any { it.name == "confusion" })
    }

    @Test fun `unfusion sole signature fallback preserves benched move PP Ups`() {
        val signature = MoveTemplate.dummy("glaciallance")
        val pokemon = pokemon(form(signature))
        pokemon.moveSet.add(signature.create())
        val taught = MoveTemplate.dummy("taughtfallback")
        pokemon.benchedMoves.add(BenchedMove(taught, 3))
        pokemon.form = form()
        val promoted = pokemon.moveSet.single()
        assertEquals(taught, promoted.template)
        assertEquals(3, promoted.raisedPpStages)
        assertEquals(promoted.maxPp, promoted.currentPp)
    }
}
