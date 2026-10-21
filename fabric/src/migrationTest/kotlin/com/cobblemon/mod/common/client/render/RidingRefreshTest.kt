package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.api.riding.Seat
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class RidingRefreshTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private fun vehicle(client: Boolean, seats: List<Seat>, passengers: List<Entity>, assigned: Map<Seat, Entity>): PokemonEntity {
        val vehicle = mock(PokemonEntity::class.java)
        val level = mock(Level::class.java)
        `when`(level.isClientSide).thenReturn(client)
        `when`(vehicle.level()).thenReturn(level)
        `when`(vehicle.seats).thenReturn(seats)
        `when`(vehicle.passengers).thenReturn(passengers)
        val pokemon = mock(Pokemon::class.java, RETURNS_DEEP_STUBS)
        `when`(pokemon.form.riding.behaviours).thenReturn(null)
        `when`(pokemon.entity).thenReturn(vehicle)
        PokemonEntity::class.java.getDeclaredField("pokemon").apply { isAccessible = true }.set(vehicle, pokemon)
        PokemonEntity::class.java.getDeclaredField("occupiedSeats").apply { isAccessible = true }.set(vehicle, assigned.toMutableMap())
        doCallRealMethod().`when`(vehicle).occupiedSeats
        doCallRealMethod().`when`(vehicle).refreshRiding()
        return vehicle
    }

    @Test fun `late observer metadata refresh preserves the basket seat and never ejects its rider`() {
        val seat = Seat("seat_1")
        val rider = mock(Entity::class.java)
        val vehicle = vehicle(true, listOf(seat), listOf(rider), mapOf(seat to rider))
        vehicle.refreshRiding()
        assertEquals(mapOf(seat to rider), vehicle.occupiedSeats)
        verify(vehicle, never()).ejectPassengers()
    }

    @Test fun `observer recovers a missing seat from tracked passengers without an owner lookup`() {
        val seat = Seat("seat_1")
        val rider = mock(Entity::class.java)
        val vehicle = vehicle(true, listOf(seat), listOf(rider), emptyMap())
        vehicle.refreshRiding()
        assertEquals(mapOf(seat to rider), vehicle.occupiedSeats)
        verify(vehicle, never()).getControllingPassenger()
    }

    @Test fun `refresh retains valid multi seat assignments and removes dismounted riders`() {
        val front = Seat("seat_1")
        val oldBack = Seat("seat_2", handsBusy = false)
        val newBack = Seat("seat_2", handsBusy = true)
        val leaving = mock(Entity::class.java)
        val staying = mock(Entity::class.java)
        val joining = mock(Entity::class.java)
        val vehicle = vehicle(true, listOf(front, newBack), listOf(joining, staying), mapOf(front to leaving, oldBack to staying))
        vehicle.refreshRiding()
        assertEquals(mapOf(front to joining, newBack to staying), vehicle.occupiedSeats)
        `when`(vehicle.passengers).thenReturn(emptyList())
        vehicle.refreshRiding()
        assertTrue(vehicle.occupiedSeats.isEmpty())
    }

    @Test fun `server refresh still ejects riders and clears assignments`() {
        val seat = Seat("seat_1")
        val rider = mock(Entity::class.java)
        val vehicle = vehicle(false, listOf(seat), listOf(rider), mapOf(seat to rider))
        vehicle.refreshRiding()
        verify(vehicle).ejectPassengers()
        assertTrue(vehicle.occupiedSeats.isEmpty())
    }
}
