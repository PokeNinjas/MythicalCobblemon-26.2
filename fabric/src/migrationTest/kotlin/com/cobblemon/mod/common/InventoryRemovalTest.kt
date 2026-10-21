package com.cobblemon.mod.common

import com.cobblemon.mod.common.util.removeAmountIf
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.EntityEquipment
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class InventoryRemovalTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach { it.apply() }
        }
    }
    private fun inventory() = Inventory(mock(Player::class.java), EntityEquipment())

    @Test fun `removal spans real inventory and offhand slots without restarting count`() {
        val inventory = inventory()
        inventory.setItem(0, ItemStack(Items.APPLE, 3))
        inventory.setItem(4, ItemStack(Items.APPLE, 4))
        inventory.setItem(Inventory.SLOT_OFFHAND, ItemStack(Items.APPLE, 8))
        inventory.setItem(1, ItemStack(Items.DIAMOND, 9))
        inventory.removeAmountIf(10) { it.`is`(Items.APPLE) }
        assertTrue(inventory.getItem(0).isEmpty)
        assertTrue(inventory.getItem(4).isEmpty)
        assertEquals(5, inventory.getItem(Inventory.SLOT_OFFHAND).count)
        assertEquals(9, inventory.getItem(1).count)
    }

    @Test fun `removal within first stack leaves later matching slots untouched`() {
        val inventory = inventory()
        inventory.setItem(0, ItemStack(Items.APPLE, 12))
        inventory.setItem(Inventory.SLOT_OFFHAND, ItemStack(Items.APPLE, 8))
        inventory.removeAmountIf(5) { it.`is`(Items.APPLE) }
        assertEquals(7, inventory.getItem(0).count)
        assertEquals(8, inventory.getItem(Inventory.SLOT_OFFHAND).count)
    }

    @Test fun `zero negative and insufficient requests never remove unrelated items`() {
        val inventory = inventory()
        inventory.setItem(2, ItemStack(Items.APPLE, 4))
        inventory.setItem(6, ItemStack(Items.DIAMOND, 3))
        inventory.removeAmountIf(0) { true }
        inventory.removeAmountIf(-2) { true }
        assertEquals(4, inventory.getItem(2).count)
        inventory.removeAmountIf(20) { it.`is`(Items.APPLE) }
        assertTrue(inventory.getItem(2).isEmpty)
        assertEquals(3, inventory.getItem(6).count)
    }
}
