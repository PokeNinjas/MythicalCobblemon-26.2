package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.net.messages.server.block.TMMachineTeachMovePacket
import com.cobblemon.mod.common.net.serverhandling.block.TMMachineTeachMoveHandler
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.mockito.Mockito.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import sun.misc.Unsafe
import java.util.UUID

class TMMachineSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach { it.apply() }
        }

        private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
    }

    private data class Fixture(
        val machine: TMMachineBlockEntity,
        val menu: TMMachineMenu,
        val player: ServerPlayer,
        val level: Level,
        val pos: BlockPos
    )

    // Keep the real menu, inventory, inherited container validity and native reach
    // calculation. Only world lookup and player attributes need a server fixture.
    // The machine constructor requires registered Cobblemon blocks, so initialize
    // just the fields used by this path instead of mocking the final Kotlin class.
    private fun fixture(): Fixture {
        val pos = BlockPos(10, 64, -8)
        val level = mock(Level::class.java)
        val machine = unsafe.allocateInstance(TMMachineBlockEntity::class.java) as TMMachineBlockEntity
        BlockEntity::class.java.getDeclaredField("worldPosition").apply { isAccessible = true }.set(machine, pos)
        machine.setLevel(level)
        machine.tmMachineInventory = TMMachineBlockEntity.TMMachineBlockInventory(machine)
        `when`(level.getBlockEntity(pos)).thenReturn(machine)

        val menu = TMMachineMenu(MenuType.GENERIC_9x3, 7).apply { inventory = machine.tmMachineInventory }
        val player = mock(ServerPlayer::class.java)
        player.containerMenu = menu
        `when`(player.blockInteractionRange()).thenReturn(4.5)
        doCallRealMethod().`when`(player).isWithinBlockInteractionRange(pos, 4.0)
        return Fixture(machine, menu, player, level, pos).also { setEyePosition(it, Vec3(10.5, 65.5, -7.5)) }
    }

    private fun setEyePosition(f: Fixture, position: Vec3) {
        // getX/getZ/getEyePosition are final; let them read the real native field.
        Entity::class.java.getDeclaredField("position").apply { isAccessible = true }.set(f.player, position)
        `when`(f.player.eyeY).thenReturn(position.y)
        `when`(f.player.eyePosition).thenReturn(position)
    }

    @Test fun `menu and inventory accept nearby player and reject out of range player`() {
        val f = fixture()
        assertTrue(f.machine.tmMachineInventory.stillValid(f.player))
        assertTrue(f.menu.stillValid(f.player))

        setEyePosition(f, Vec3(74.5, 65.5, -7.5))
        assertFalse(f.machine.tmMachineInventory.stillValid(f.player))
        assertFalse(f.menu.stillValid(f.player))
    }

    @Test fun `menu preserves native interaction range attribute`() {
        val f = fixture()
        setEyePosition(f, Vec3(23.0, 64.5, -7.5))
        assertFalse(f.menu.stillValid(f.player))
        `when`(f.player.blockInteractionRange()).thenReturn(12.0)
        assertTrue(f.menu.stillValid(f.player))
    }

    @Test fun `menu rejects removed replaced and unloaded machine`() {
        val f = fixture()
        `when`(f.level.getBlockEntity(f.pos)).thenReturn(null)
        assertFalse(f.menu.stillValid(f.player))
        `when`(f.level.getBlockEntity(f.pos)).thenReturn(mock(BlockEntity::class.java))
        assertFalse(f.menu.stillValid(f.player))
        `when`(f.level.getBlockEntity(f.pos)).thenReturn(f.machine)
        BlockEntity::class.java.getDeclaredField("level").apply { isAccessible = true }.set(f.machine, null)
        assertFalse(f.menu.stillValid(f.player))
    }

    @Test fun `stale teaching packet is rejected before storage access or carried stack mutation`() {
        val server = mock(MinecraftServer::class.java)
        for (removed in listOf(false, true)) {
            val f = fixture()
            if (removed) `when`(f.level.getBlockEntity(f.pos)).thenReturn(null)
            else setEyePosition(f, Vec3(74.5, 65.5, -7.5))
            f.menu.carried = ItemStack(Items.APPLE, 2)
            val packet = TMMachineTeachMovePacket(UUID.randomUUID(), f.menu.carried.copy(), MoveTemplate.dummy("tackle"))

            // No Pokemon storage provider is installed: reaching storage is itself
            // a regression, as well as allowing this packet to change menu state.
            assertDoesNotThrow(Executable { TMMachineTeachMoveHandler.handle(packet, server, f.player) })
            assertEquals(2, f.menu.carried.count)
            assertEquals(0, f.menu.stateId)
        }
    }

    @Test fun `accepted teaching advances menu state before consuming and broadcasting`() {
        // Full accepted teaching needs Cobblemon's storage and TM registries. Audit
        // the compiled production method rather than reproduce it in a test helper.
        val type = TMMachineTeachMoveHandler::class.java
        val node = ClassNode()
        type.getResourceAsStream("${type.simpleName}.class")!!.use {
            ClassReader(it).accept(node, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        val method = node.methods.single {
            it.name == "handle" && it.desc.startsWith("(Lcom/cobblemon/mod/common/net/messages/server/block/TMMachineTeachMovePacket;")
        }
        val calls = method.instructions.toArray().filterIsInstance<MethodInsnNode>()
        val advance = calls.indexOfFirst { it.name == "incrementStateId" && it.desc == "()I" }
        val consume = calls.indexOfFirst { it.owner == "net/minecraft/world/item/ItemStack" && it.name == "shrink" }
        val broadcast = calls.indexOfFirst { it.name == "broadcastChanges" }
        assertEquals(1, calls.count { it.name == "incrementStateId" })
        assertTrue(advance >= 0 && advance < consume, "TM consumption must advance the menu state first")
        assertTrue(consume < broadcast, "The updated carried stack must be broadcast after consumption")
    }
}
