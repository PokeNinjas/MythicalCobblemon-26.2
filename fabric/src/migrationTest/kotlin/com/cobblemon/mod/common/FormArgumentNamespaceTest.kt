package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.command.argument.FormArgumentType
import com.cobblemon.mod.common.command.argument.SpeciesArgumentType
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import net.minecraft.SharedConstants
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class FormArgumentNamespaceTest {
    @Test fun `form commands and suggestions resolve explicit addon and default namespaces`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        @Suppress("UNCHECKED_CAST")
        val registry = PokemonSpecies::class.java.getDeclaredField("speciesByIdentifier").apply { isAccessible = true }
            .get(PokemonSpecies) as MutableMap<Identifier, Species>
        val keys = listOf(Identifier.parse("zamega:namespace_test"), Identifier.parse("cobblemon:namespace_test"))
        val previous = keys.associateWith { registry[it] }
        val selected = keys.map { key ->
            val form = mock(FormData::class.java)
            `when`(form.formOnlyShowdownId()).thenReturn("mega")
            val species = mock(Species::class.java)
            `when`(species.resourceIdentifier).thenReturn(key)
            `when`(species.forms).thenReturn(mutableListOf(form))
            `when`(species.standardForm).thenReturn(form)
            registry[key] = species
            form
        }
        try {
            for ((token, expected) in listOf("zamega:namespace_test" to selected[0], "namespace_test" to selected[1])) {
                val input = "test $token mega"
                val reader = StringReader(input).apply { cursor = input.lastIndexOf(' ') + 1 }
                assertSame(expected, FormArgumentType().parse(reader))
                val dispatcher = CommandDispatcher<Any>()
                dispatcher.register(literal<Any>("test").then(argument<Any, Species>("species", SpeciesArgumentType())
                    .then(argument<Any, FormData>("form", FormArgumentType()))))
                val suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("test $token me", Any())).get()
                assertEquals(listOf("mega"), suggestions.list.map { it.text })
            }
        } finally {
            previous.forEach { (id, value) -> if (value == null) registry.remove(id) else registry[id] = value }
        }
    }
}
