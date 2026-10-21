package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.abilities.AbilityTemplate
import com.cobblemon.mod.common.api.abilities.CommonAbility
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.BenchedMoves
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.MoveTarget
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider
import com.cobblemon.mod.common.api.pokemon.evolution.EvolutionProxy
import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature
import com.cobblemon.mod.common.api.pokemon.moves.Learnset
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceGroups
import com.cobblemon.mod.common.api.pokemon.experience.MediumFast
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.config.CobblemonConfig
import com.cobblemon.mod.common.pokemon.*
import com.cobblemon.mod.common.util.DataKeys
import com.mojang.serialization.MapCodec
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class PokemonFeatureLoadOrderTest {
    private val implementationField = Cobblemon::class.java.getDeclaredField("implementation").apply { isAccessible = true }
    private var oldImplementation: Any? = null
    @Suppress("UNCHECKED_CAST")
    private val testMoveRegistry = Moves::class.java.getDeclaredField("allMoves").apply { isAccessible = true }
        .get(null) as MutableMap<String, MoveTemplate>
    private var oldTestMoves: Map<String, MoveTemplate> = emptyMap()
    private fun move(name: String): MoveTemplate = MoveTemplate(name, -1, ElementalTypes.NORMAL,
        DamageCategories.STATUS, 0.0, MoveTarget.all, 100.0, 5, 0, 0.0, emptyArray(), 0F)
        .also { testMoveRegistry[name] = it }
    @BeforeEach fun initializePlatform() {
        oldImplementation = implementationField.get(Cobblemon)
        oldTestMoves = testMoveRegistry.toMap()
        Cobblemon.implementation = mock(CobblemonImplementation::class.java)
    }
    @AfterEach fun restorePlatform() {
        implementationField.set(Cobblemon, oldImplementation)
        testMoveRegistry.clear(); testMoveRegistry.putAll(oldTestMoves)
    }
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach { it.apply() }
        }
    }

    private fun set(pokemon: Pokemon, name: String, value: Any) {
        Pokemon::class.java.getDeclaredField(name).apply { isAccessible = true }.set(pokemon, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun codec(type: Class<*>): MapCodec<Any> =
        type.getDeclaredField("CODEC").apply { isAccessible = true }.get(null) as MapCodec<Any>

    @Test fun `complete saved Pokemon codec round trips features nickname items and taught moves through NBT and JSON`() {
        val oldConfig = runCatching { Cobblemon.config }.getOrNull()
        Cobblemon.config = CobblemonConfig()
        val addedExperienceGroup = ExperienceGroups.none()
        if (addedExperienceGroup) ExperienceGroups.register(MediumFast)
        val featureKey = "test_full_codec_form"
        val provider = ChoiceSpeciesFeatureProvider().apply {
            keys = listOf(featureKey)
            choices = listOf("normal", "heat")
            default = "normal"
        }
        SpeciesFeatures.register(featureKey, provider)
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
        val abilityName = "testfullcodecability"
        val ability = AbilityTemplate(abilityName)
        val previousAbility = abilityRegistry.put(abilityName, ability)
        val taught = move("testfullcodectaught")
        val benched = move("testfullcodecbenched")
        val previousMoves = listOf(taught, benched).associate { it.name to moveRegistry.put(it.name, it) }
        try {
            val species = Species().apply {
                name = "TestFullCodec"
                resourceIdentifier = Identifier.fromNamespaceAndPath("cobblemon", "test_full_codec")
                implemented = true
                Stats.PERMANENT.forEach { baseStats[it] = 75 }
                abilities.add(Priority.LOWEST, CommonAbility(ability))
                features += featureKey
                moves.levelUpMoves[1] = mutableListOf(taught)
                moves.tmMoves += benched
                forms += FormData(name = "Heat").apply { aspects += "heat" }
                initialize()
            }
            speciesRegistry.clear()
            speciesRegistry[species.resourceIdentifier] = species
            val pokemon = Pokemon().apply {
                features.add(StringSpeciesFeature(featureKey, "heat"))
                updateAspects()
                level = 80
                shiny = true
                forcedAspects = setOf("test-cosmetic")
                nickname = Component.literal("Saved Heat").withStyle(net.minecraft.ChatFormatting.ITALIC)
                moveSet.clear()
                moveSet.add(taught.create(2, 3))
                benchedMoves.add(BenchedMove(benched, 2))
                cosmeticItem = ItemStack(Items.DIAMOND)
                persistentData.putString("test_custom_data", "retained")
            }
            set(pokemon, "heldItem", ItemStack(Items.APPLE, 2))
            val registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
            val nbtOps = registryAccess.createSerializationContext(NbtOps.INSTANCE)
            val jsonOps = registryAccess.createSerializationContext(JsonOps.INSTANCE)

            val nbt = Pokemon.CODEC.encodeStart(nbtOps, pokemon).getOrThrow()
            val json = Pokemon.CODEC.encodeStart(jsonOps, pokemon).getOrThrow()
            val copies = listOf(
                Pokemon.CODEC.parse(nbtOps, nbt).getOrThrow(),
                Pokemon.CODEC.parse(jsonOps, json).getOrThrow()
            )
            for (copy in copies) {
                assertEquals(pokemon.uuid, copy.uuid)
                assertEquals("heat", copy.form.formOnlyShowdownId())
                assertEquals("heat", copy.features.filterIsInstance<StringSpeciesFeature>().single().value)
                assertTrue(copy.shiny)
                assertEquals(setOf("test-cosmetic"), copy.forcedAspects)
                assertEquals(pokemon.nickname, copy.nickname)
                assertTrue(ItemStack.matches(pokemon.heldItem(), copy.heldItem()))
                assertTrue(ItemStack.matches(pokemon.cosmeticItem, copy.cosmeticItem))
                assertEquals("retained", copy.persistentData.getString("test_custom_data").orElseThrow())
                assertEquals(listOf(taught.name), copy.moveSet.map { it.template.name })
                assertEquals(2, copy.moveSet[0]!!.currentPp)
                assertEquals(3, copy.moveSet[0]!!.raisedPpStages)
                assertEquals(listOf(benched.name), copy.benchedMoves.map { it.moveTemplate.name })
                assertEquals(2, copy.benchedMoves.first().ppRaisedStages)
            }
        } finally {
            speciesRegistry.clear()
            speciesRegistry.putAll(oldSpecies)
            if (previousAbility == null) abilityRegistry.remove(abilityName) else abilityRegistry[abilityName] = previousAbility
            previousMoves.forEach { (name, previous) ->
                if (previous == null) moveRegistry.remove(name) else moveRegistry[name] = previous
            }
            SpeciesFeatures.unregister(featureKey)
            if (addedExperienceGroup) ExperienceGroups.unregister(MediumFast)
            if (oldConfig != null) Cobblemon.config = oldConfig
        }
    }

    @Test fun `saved shiny form features are restored before aspect updates and taught moves survive`() {
        val oldConfig = runCatching { Cobblemon.config }.getOrNull()
        Cobblemon.config = CobblemonConfig()
        val featureKey = "test_load_order_form"
        val provider = ChoiceSpeciesFeatureProvider().apply {
            keys = listOf(featureKey)
            choices = listOf("heat")
        }
        SpeciesFeatures.register(featureKey, provider)
        val shinyProvider = object : AspectProvider {
            override fun provide(pokemon: Pokemon) = if (pokemon.shiny) setOf("shiny") else emptySet()
            override fun provide(properties: PokemonProperties) = emptySet<String>()
        }
        AspectProvider.register(shinyProvider)
        val species = mock(Species::class.java)
        val id = Identifier.fromNamespaceAndPath("cobblemon", "test_load_order")
        val normal = mock(FormData::class.java)
        val heat = mock(FormData::class.java)
        `when`(normal.moves).thenReturn(Learnset())
        `when`(heat.moves).thenReturn(Learnset())
        `when`(heat.formOnlyShowdownId()).thenReturn("heat")
        `when`(species.resourceIdentifier).thenReturn(id)
        `when`(species.features).thenReturn(mutableSetOf(featureKey))
        `when`(species.standardForm).thenReturn(normal)
        `when`(species.forms).thenReturn(mutableListOf(heat))
        `when`(species.getForm(anySet())).thenAnswer { invocation ->
            if ("heat" in invocation.getArgument<Set<String>>(0)) heat else normal
        }
        @Suppress("UNCHECKED_CAST")
        val registry = PokemonSpecies::class.java.getDeclaredField("speciesByIdentifier")
            .apply { isAccessible = true }.get(null) as MutableMap<Identifier, Species>
        val previousSpecies = registry.put(id, species)
        try {
            val taught = move("testtaught")
            val benched = move("testbenched")
            val moves = MoveSet().apply { add(taught.create(2, 3)) }
            val bench = BenchedMoves().apply { add(BenchedMove(benched, 2)) }
            val feature = CompoundTag().apply {
                putString("cobblemon:feature_id", featureKey)
                putString(featureKey, "heat")
            }
            // Exercise the real persisted P1 codec rather than checking source text.
            val type = Class.forName("com.cobblemon.mod.common.util.codec.internal.PokemonP1")
            val partial = type.declaredConstructors.single { it.parameterCount == 16 }.newInstance(
                UUID.randomUUID(), species, heat, listOf(feature), 80, 0, 70, 40,
                Gender.GENDERLESS, IVs.createRandomIVs(), EVs.createEmpty(), moves, bench,
                1F, true, Abilities.DUMMY.create()
            )
            val saved = codec(type).codec().encodeStart(NbtOps.INSTANCE, partial).getOrThrow() as CompoundTag
            assertTrue(saved.contains("Features"))
            assertTrue(saved.getBoolean(DataKeys.POKEMON_SHINY).orElseThrow())
            val loaded = codec(type).codec().parse(NbtOps.INSTANCE, saved).getOrThrow()
            val pokemon = mock(Pokemon::class.java)
            mapOf(
                "species" to species, "form" to heat, "level" to 80,
                "persistentData" to CompoundTag(),
                "features" to mutableListOf<SpeciesFeature>(), "forcedAspects" to emptySet<String>(),
                "aspects" to emptySet<String>(), "moveSet" to MoveSet(), "benchedMoves" to BenchedMoves(),
                "ivs" to IVs.createRandomIVs(), "evs" to EVs.createEmpty(), "ability" to Abilities.DUMMY.create(),
                "evolutionProxy" to mock(EvolutionProxy::class.java, RETURNS_DEEP_STUBS)
            ).forEach { (name, value) -> set(pokemon, name, value) }
            `when`(pokemon.maxHealth).thenReturn(100)
            `when`(pokemon.isWild()).thenReturn(true)
            doCallRealMethod().`when`(pokemon).species
            doCallRealMethod().`when`(pokemon).form
            doCallRealMethod().`when`(pokemon).level
            doCallRealMethod().`when`(pokemon).features
            doCallRealMethod().`when`(pokemon).aspects
            doCallRealMethod().`when`(pokemon).shiny
            doCallRealMethod().`when`(pokemon).moveSet
            doCallRealMethod().`when`(pokemon).benchedMoves
            doCallRealMethod().`when`(pokemon).ivs
            doCallRealMethod().`when`(pokemon).evs
            doCallRealMethod().`when`(pokemon).ability
            doCallRealMethod().`when`(pokemon).form = any(FormData::class.java) ?: heat
            doCallRealMethod().`when`(pokemon).shiny = anyBoolean()
            doCallRealMethod().`when`(pokemon).updateAspects()
            doCallRealMethod().`when`(pokemon).updateForm()

            type.getMethod("into", Pokemon::class.java).invoke(loaded, pokemon)

            assertSame(heat, pokemon.form)
            assertEquals(setOf("heat", "shiny"), pokemon.aspects)
            assertEquals("heat", (pokemon.features.single() as StringSpeciesFeature).value)
            assertEquals(listOf("testtaught"), pokemon.moveSet.map { it.template.name })
            assertEquals(2, pokemon.moveSet[0]!!.currentPp)
            assertEquals(3, pokemon.moveSet[0]!!.raisedPpStages)
            assertEquals(listOf("testbenched"), pokemon.benchedMoves.map { it.moveTemplate.name })
            assertEquals(2, pokemon.benchedMoves.first().ppRaisedStages)
            verify(pokemon, never()).form = normal
        } finally {
            if (previousSpecies == null) registry.remove(id) else registry[id] = previousSpecies
            SpeciesFeatures.unregister(featureKey)
            AspectProvider.unregister(shinyProvider)
            if (oldConfig != null) Cobblemon.config = oldConfig
        }
    }
}
