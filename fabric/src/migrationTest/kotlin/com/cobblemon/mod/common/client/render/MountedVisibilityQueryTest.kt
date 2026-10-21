package com.cobblemon.mod.common.client.render

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molang.function.PokemonEntityMoLangFunctions
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.LivingEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class MountedVisibilityQueryTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }
    @Test fun `remote rider animation does not depend on resolving a controlling owner`() {
        val pokemon = mock(PokemonEntity::class.java)
        `when`(pokemon.passengers).thenReturn(listOf(mock(LivingEntity::class.java)))
        `when`(pokemon.controllingPassenger).thenReturn(null)
        val query = PokemonEntityMoLangFunctions.attach(pokemon).getValue("is_ridden")
        assertEquals(1.0, (query(MoParams.EMPTY) as DoubleValue).asDouble())
        `when`(pokemon.passengers).thenReturn(emptyList())
        assertEquals(0.0, (query(MoParams.EMPTY) as DoubleValue).asDouble())
    }
}
