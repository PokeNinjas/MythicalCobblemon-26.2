package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.abilities.*
import com.cobblemon.mod.common.api.moves.*
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.MoveTarget
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceGroups
import com.cobblemon.mod.common.api.pokemon.experience.MediumFast
import com.cobblemon.mod.common.api.pokemon.feature.*
import com.cobblemon.mod.common.api.pokemon.moves.Learnset
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.riding.RidingProperties
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.config.CobblemonConfig
import com.cobblemon.mod.common.pokemon.*
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class FusionSerializationTest {
    private fun move(name: String) = MoveTemplate(name, -1, ElementalTypes.NORMAL,
        DamageCategories.STATUS, 0.0, MoveTarget.all, 100.0, 5, 0, 0.0, emptyArray(), 0F)
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach { it.apply() }
        }
    }

    @Test fun `actual Pokemon codec preserves host gains and untouched partner across unfuse save reload and refuse`() {
        val oldConfig = runCatching { Cobblemon.config }.getOrNull()
        val implementationField = Cobblemon::class.java.getDeclaredField("implementation").apply { isAccessible = true }
        val oldImplementation = implementationField.get(Cobblemon)
        Cobblemon.implementation = mock(CobblemonImplementation::class.java)
        Cobblemon.config = CobblemonConfig()
        val addedExperience = ExperienceGroups.none()
        if (addedExperience) ExperienceGroups.register(MediumFast)
        val featureKey = "test_fusion_save_form"
        SpeciesFeatures.register(featureKey, ChoiceSpeciesFeatureProvider().apply {
            keys = listOf(featureKey); choices = listOf("normal", "ice"); default = "normal"
        })
        @Suppress("UNCHECKED_CAST")
        val speciesRegistry = PokemonSpecies::class.java.getDeclaredField("speciesByIdentifier")
            .apply { isAccessible = true }.get(null) as MutableMap<Identifier, Species>
        @Suppress("UNCHECKED_CAST")
        val abilityRegistry = Abilities::class.java.getDeclaredField("abilityMap")
            .apply { isAccessible = true }.get(null) as MutableMap<String, AbilityTemplate>
        @Suppress("UNCHECKED_CAST")
        val moveRegistry = Moves::class.java.getDeclaredField("allMoves")
            .apply { isAccessible = true }.get(null) as MutableMap<String, MoveTemplate>
        val oldSpecies = speciesRegistry.toMap()
        val ability = AbilityTemplate("testfusionability")
        val oldAbility = abilityRegistry.put(ability.name, ability)
        val signature = move("glaciallance")
        val confusion = move("confusion")
        val taught = (0..2).map { move("testfusiontaught$it") }
        val oldMoves = (listOf(signature, confusion) + taught).associate { it.name to moveRegistry.put(it.name, it) }
        try {
            val settings = mock(RidingBehaviourSettings::class.java)
            `when`(settings.stats).thenReturn(mutableMapOf(RidingStat.SPEED to 1..11))
            doCallRealMethod().`when`(settings).calculate(any(RidingStat::class.java) ?: RidingStat.SPEED, anyFloat())
            val species = Species().apply {
                name = "Calyrex"
                resourceIdentifier = Identifier.fromNamespaceAndPath("cobblemon", "calyrex")
                implemented = true
                Stats.PERMANENT.forEach { baseStats[it] = 75 }
                abilities.add(Priority.LOWEST, CommonAbility(ability))
                features += featureKey
                moves.levelUpMoves[1] = mutableListOf(confusion)
                moves.tmMoves += taught
                forms += FormData(name = "IceRider", _moves = Learnset().apply {
                    formChangeMoves += signature; levelUpMoves[1] = mutableListOf(signature)
                    tmMoves += taught
                }, _riding = RidingProperties(behaviours = mapOf(RidingStyle.LAND to settings))).apply { aspects += "ice" }
                initialize()
            }
            speciesRegistry.clear()
            speciesRegistry[species.resourceIdentifier] = species
            val registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
            val nbtOps = registry.createSerializationContext(NbtOps.INSTANCE)
            val jsonOps = registry.createSerializationContext(JsonOps.INSTANCE)
            fun change(pokemon: Pokemon, value: String) {
                pokemon.features.filterIsInstance<StringSpeciesFeature>().single { it.name == featureKey }.value = value
                pokemon.updateAspects()
            }
            val partner = Pokemon().apply {
                level = 60; ivs[Stats.ATTACK] = 12; evs[Stats.ATTACK] = 20
                persistentData.putString("partner_custom", "preserved")
            }
            val partnerBefore = partner.saveToNBT(registry, CompoundTag())
            var host = Pokemon().apply {
                features.add(StringSpeciesFeature(featureKey, "normal"))
                level = 80
                moveSet.clear(); moveSet.add(confusion.create(0, 2))
                taught.forEach { moveSet.add(it.create(0, 2)) }
                persistentData.put("fusion_pokemon", partnerBefore.copy())
                persistentData.putString("unrelated_custom", "preserved")
            }
            val hostId = host.uuid
            change(host, "ice")
            assertTrue(signature in host.moveSet.getMoveTemplates())
            host.level = 81; host.ivs[Stats.ATTACK] = 30; host.evs[Stats.ATTACK] = 100
            host.setRideBoost(RidingStat.SPEED, 7F)
            host.moveSet.first { it.template == signature }.apply { raisedPpStages = 3; currentPp = 0 }
            host = Pokemon.CODEC.parse(nbtOps, Pokemon.CODEC.encodeStart(nbtOps, host).getOrThrow()).getOrThrow()
            change(host, "normal")
            assertEquals(0F, host.getMaxRideBoost(RidingStat.SPEED))
            assertFalse(signature in host.moveSet.getMoveTemplates())
            assertEquals("confusion", host.moveSet[0]!!.name)
            assertEquals(0, host.moveSet[0]!!.currentPp)
            assertEquals(2, host.moveSet[0]!!.raisedPpStages)
            assertTrue(host.benchedMoves.none { it.moveTemplate == signature })

            val copies = listOf(
                Pokemon.CODEC.parse(nbtOps, Pokemon.CODEC.encodeStart(nbtOps, host).getOrThrow()).getOrThrow(),
                Pokemon.CODEC.parse(jsonOps, Pokemon.CODEC.encodeStart(jsonOps, host).getOrThrow()).getOrThrow()
            )
            for ((index, copy) in copies.withIndex()) {
                assertEquals(hostId, copy.uuid)
                assertEquals(81, copy.level)
                assertEquals(30, copy.ivs[Stats.ATTACK])
                assertEquals(100, copy.evs[Stats.ATTACK])
                assertEquals(7F, copy.getRideBoost(RidingStat.SPEED))
                assertEquals("preserved", copy.persistentData.getString("unrelated_custom").orElseThrow())
                if (index == 0) assertEquals(partnerBefore, copy.persistentData.getCompoundOrEmpty("fusion_pokemon"))
                // JSON round-trips preserve numbers but may narrow NBT integer tag types.
                assertEquals(CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, partnerBefore).getOrThrow(),
                    CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, copy.persistentData.getCompoundOrEmpty("fusion_pokemon")).getOrThrow())
                assertFalse(signature in copy.moveSet.getMoveTemplates())
                change(copy, "ice")
                val returned = copy.moveSet.first { it.template == signature }
                assertEquals(3, returned.raisedPpStages)
                assertEquals(0, returned.currentPp)
                assertEquals(8F, copy.getRideStat(RidingStyle.LAND, RidingStat.SPEED))
                taught.forEach { move ->
                    val current = copy.moveSet.first { it.template == move }
                    assertEquals(0, current.currentPp); assertEquals(2, current.raisedPpStages)
                }
                val returnedPartner = Pokemon.loadFromNBT(registry, copy.persistentData.getCompoundOrEmpty("fusion_pokemon"))
                assertEquals(partner.uuid, returnedPartner.uuid)
                assertEquals(60, returnedPartner.level)
                assertEquals(12, returnedPartner.ivs[Stats.ATTACK])
                assertEquals(20, returnedPartner.evs[Stats.ATTACK])
            }
        } finally {
            speciesRegistry.clear(); speciesRegistry.putAll(oldSpecies)
            if (oldAbility == null) abilityRegistry.remove(ability.name) else abilityRegistry[ability.name] = oldAbility
            oldMoves.forEach { (name, previous) -> if (previous == null) moveRegistry.remove(name) else moveRegistry[name] = previous }
            SpeciesFeatures.unregister(featureKey)
            if (addedExperience) ExperienceGroups.unregister(MediumFast)
            if (oldConfig != null) Cobblemon.config = oldConfig
            implementationField.set(Cobblemon, oldImplementation)
        }
    }
}
