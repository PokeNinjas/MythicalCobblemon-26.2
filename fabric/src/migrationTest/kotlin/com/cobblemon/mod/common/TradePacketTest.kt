package com.cobblemon.mod.common

import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket
import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket.TradeablePokemon
import com.cobblemon.mod.common.pokemon.Gender
import io.netty.buffer.Unpooled
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class TradePacketTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach { it.apply() }
        }
    }

    private fun roundTrip(party: List<TradeablePokemon?>): TradeStartedPacket {
        val original = TradeStartedPacket(UUID.fromString("71638723-3c03-3a71-a599-c5a55741de46"), Component.literal("Test trader"), party)
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
        try {
            original.encode(buffer)
            val decoded = TradeStartedPacket.decode(buffer)
            assertEquals(0, buffer.readableBytes(), "Native packet framing rejects any unread payload bytes")
            assertEquals(original.traderId, decoded.traderId)
            assertEquals(original.traderName, decoded.traderName)
            assertEquals(party.size, decoded.traderParty.size)
            return decoded
        } finally { buffer.release() }
    }

    @Test fun `trade initialization preserves empty party positions and full offered Pokemon metadata`() {
        val offered = TradeablePokemon(UUID.randomUUID(), Identifier.parse("cobblemon:machoke"), setOf("male", "shiny"),
            30, Gender.MALE, ItemStack(Items.APPLE, 2), true)
        val other = TradeablePokemon(UUID.randomUUID(), Identifier.parse("cobblemon:wooloo"), emptySet(),
            5, Gender.FEMALE, ItemStack.EMPTY, false)
        val decoded = roundTrip(listOf(null, offered, null, other, null, null))
        for (slot in listOf(0, 2, 4, 5)) assertNull(decoded.traderParty[slot])
        for ((slot, expected) in listOf(1 to offered, 3 to other)) {
            val actual = decoded.traderParty[slot]!!
            assertEquals(expected.pokemonId, actual.pokemonId)
            assertEquals(expected.species, actual.species)
            assertEquals(expected.aspects, actual.aspects)
            assertEquals(expected.level, actual.level)
            assertEquals(expected.gender, actual.gender)
            assertEquals(expected.tradeable, actual.tradeable)
            assertTrue(ItemStack.matches(expected.heldItem, actual.heldItem))
        }
    }

    @Test fun `trade initialization consumes empty and all-null party payloads completely`() {
        roundTrip(emptyList())
        assertTrue(roundTrip(List(6) { null }).traderParty.all { it == null })
    }
}
