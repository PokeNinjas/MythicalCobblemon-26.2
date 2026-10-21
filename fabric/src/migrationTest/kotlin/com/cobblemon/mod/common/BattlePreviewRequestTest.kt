package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.interpreter.instructions.RequestInstruction
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class BattlePreviewRequestTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test fun `preview request selects default order for the requesting actor without a move prompt`() {
        val battle = mock(PokemonBattle::class.java)
        val actor = mock(BattleActor::class.java)
        `when`(actor.showdownId).thenReturn("p2")
        RequestInstruction(actor, BattleMessage("""|request|{"teamPreview":true,"maxTeamSize":3,"side":{"id":"p2","pokemon":[]}}""")).invoke(battle)
        verify(battle).writeShowdownAction(">p2 team")
        verify(battle).log("Request Instruction")
        verifyNoMoreInteractions(battle)
    }

    @Test fun `ordinary wait requests do not select a preview team`() {
        val battle = mock(PokemonBattle::class.java)
        val actor = mock(BattleActor::class.java)
        `when`(battle.players).thenReturn(emptyList())
        RequestInstruction(actor, BattleMessage("""|request|{"wait":true,"teamPreview":false}""")).invoke(battle)
        verify(battle, never()).writeShowdownAction(anyString())
    }
}
