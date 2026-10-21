package com.cobblemon.mod.common

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.UUID

class TrackingRemovalTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test fun `owner tracking end cannot mutate the active section iteration`() {
        val server = mock(MinecraftServer::class.java)
        val level = mock(ServerLevel::class.java)
        val player = mock(ServerPlayer::class.java)
        val pokemon = mock(PokemonEntity::class.java)
        val owner = UUID.randomUUID()
        `when`(player.uuid).thenReturn(owner)
        `when`(pokemon.ownerUUID).thenReturn(owner)
        `when`(pokemon.level()).thenReturn(level)
        `when`(level.server).thenReturn(server)
        `when`(server.wrapRunnable(any(Runnable::class.java) ?: Runnable {})).thenAnswer { TickTask(0, it.getArgument(0)) }
        doCallRealMethod().`when`(pokemon).stopSeenByPlayer(player)
        val section = arrayListOf<Entity>(pokemon, mock(Entity::class.java), mock(Entity::class.java))
        doAnswer { section.remove(pokemon); null }.`when`(pokemon).remove(Entity.RemovalReason.DISCARDED)

        section.forEach { if (it === pokemon) pokemon.stopSeenByPlayer(player) }
        assertEquals(3, section.size)
        pokemon.stopSeenByPlayer(player) // Duplicate tracking notifications must not duplicate cleanup.
        val task = ArgumentCaptor.forClass(TickTask::class.java)
        verify(server, times(1)).schedule(task.capture())
        verify(pokemon, never()).remove(Entity.RemovalReason.DISCARDED)
        task.value.run()
        assertEquals(2, section.size)
        verify(pokemon, times(1)).remove(Entity.RemovalReason.DISCARDED)
    }

    @Test fun `losing a non-owner viewer does not recall the Pokemon`() {
        val pokemon = mock(PokemonEntity::class.java)
        val player = mock(ServerPlayer::class.java)
        `when`(player.uuid).thenReturn(UUID.randomUUID())
        `when`(pokemon.ownerUUID).thenReturn(UUID.randomUUID())
        doCallRealMethod().`when`(pokemon).stopSeenByPlayer(player)
        pokemon.stopSeenByPlayer(player)
        verify(pokemon, never()).level()
        verify(pokemon, never()).remove(any(Entity.RemovalReason::class.java) ?: Entity.RemovalReason.DISCARDED)
    }
}
