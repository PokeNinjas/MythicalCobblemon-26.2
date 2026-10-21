package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.BattleStartError
import com.cobblemon.mod.common.battles.ErroredBattleStart
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.entity.npc.DungeonTrainerBattleFeedback
import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerPlayer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class DungeonTrainerBattleFeedbackTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private val elite = setOf("md_kind:elite", "md_run:8cf7abc0-846c-43d1-b3e9-615e6de192c9")

    @Test fun `rejected dungeon trainer battle sends existing localized reason to initiator`() {
        val player = mock(ServerPlayer::class.java)
        val result = ErroredBattleStart(mutableSetOf(BattleStartError.targetIsBusy(Component.literal("Trainer"))))
        DungeonTrainerBattleFeedback.sendRejection(elite, result, player)

        val message = ArgumentCaptor.forClass(Component::class.java)
        verify(player).sendSystemMessage(message.capture())
        val contents = message.value.contents as TranslatableContents
        assertEquals("cobblemon.battle.errors.busy", contents.key)
        assertEquals(0xFF5555, message.value.style.color!!.value)
        assertEquals(1, result.errors.count())
        verifyNoMoreInteractions(player)
    }

    @Test fun `ordinary trainers bosses and incomplete tags remain silent`() {
        val player = mock(ServerPlayer::class.java)
        val result = ErroredBattleStart(mutableSetOf(BattleStartError.canceledByEvent(null)))
        for (tags in listOf(emptySet(), setOf("md_kind:elite"), setOf("md_kind:elite", "md_run:"),
            setOf("md_kind:boss", "md_run:8cf7abc0-846c-43d1-b3e9-615e6de192c9"))) {
            DungeonTrainerBattleFeedback.sendRejection(tags, result, player)
        }
        verifyNoInteractions(player)
    }

    @Test fun `successful battle is neither touched nor announced`() {
        val player = mock(ServerPlayer::class.java)
        val battle = mock(PokemonBattle::class.java)
        val result = SuccessfulBattleStart(battle)
        DungeonTrainerBattleFeedback.sendRejection(elite, result, player)
        assertSame(battle, result.battle)
        verifyNoInteractions(player, battle)
    }
}
