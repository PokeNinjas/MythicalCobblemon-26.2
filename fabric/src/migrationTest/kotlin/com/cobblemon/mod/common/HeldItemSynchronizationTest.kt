package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.net.messages.client.pokemon.update.HeldItemUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class HeldItemSynchronizationTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach { it.apply() }
        }
    }

    // Keep the real transaction and packet code; isolate unrelated species and storage initialization.
    private fun pokemon(client: Boolean): Pokemon = mock(Pokemon::class.java).also { pokemon ->
        for ((name, value) in mapOf("isClient" to client, "heldItem" to ItemStack(Items.DIAMOND))) {
            Pokemon::class.java.getDeclaredField(name).apply { isAccessible = true }.set(pokemon, value)
        }
        doCallRealMethod().`when`(pokemon).heldItem()
        doCallRealMethod().`when`(pokemon).swapHeldItem(any(ItemStack::class.java) ?: ItemStack.EMPTY, anyBoolean(), anyBoolean())
    }

    @Test fun `client item update applies an owned copy without server transaction events`() {
        val pokemon = pokemon(true)
        val input = ItemStack(Items.APPLE, 3)
        val pre = CobblemonEvents.HELD_ITEM_PRE.subscribe { fail<Unit>("Client sync emitted server pre-event") }
        val post = CobblemonEvents.HELD_ITEM_POST.subscribe { fail<Unit>("Client sync emitted server post-event") }
        try {
            HeldItemUpdatePacket({ pokemon }, input).set(pokemon, input)
            assertTrue(pokemon.heldItem().`is`(Items.APPLE))
            assertEquals(1, pokemon.heldItem().count)
            assertEquals(3, input.count)
            input.shrink(3)
            assertEquals(1, pokemon.heldItem().count)
        } finally {
            pre.unsubscribe()
            post.unsubscribe()
        }
    }

    @Test fun `server cancellation still preserves both original stacks and skips post event`() {
        val pokemon = pokemon(false)
        val input = ItemStack(Items.APPLE, 3)
        var preCount = 0
        val pre = CobblemonEvents.HELD_ITEM_PRE.subscribe { preCount++; it.cancel() }
        val post = CobblemonEvents.HELD_ITEM_POST.subscribe { fail<Unit>("Canceled transaction emitted post-event") }
        try {
            assertSame(input, pokemon.swapHeldItem(input, true, true))
            assertEquals(1, preCount)
            assertTrue(pokemon.heldItem().`is`(Items.DIAMOND))
            assertEquals(3, input.count)
        } finally {
            pre.unsubscribe()
            post.unsubscribe()
        }
    }
}
