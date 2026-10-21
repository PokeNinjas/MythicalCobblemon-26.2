package com.cobblemon.mod.common

import com.cobblemon.mod.common.util.SavedPokemonItemCodec
import com.cobblemon.mod.common.util.StrictOptionalFieldCodec
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtOps
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SavedPokemonItemTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach { it.apply() }
        }
    }
    private val access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
    private val ops = access.createSerializationContext(JsonOps.INSTANCE)

    @Test fun `legacy custom model component upgrades rather than losing the held item`() {
        val legacy = JsonParser.parseString("""{"id":"minecraft:apple","count":2,
            "components":{"minecraft:custom_model_data":17}}""")
        assertTrue(ItemStack.CODEC.parse(ops, legacy).isError)
        val stack = SavedPokemonItemCodec.CODEC.parse(ops, legacy).getOrThrow()
        assertTrue(stack.`is`(Items.APPLE))
        assertEquals(2, stack.count)
        assertEquals(17F, stack.get(DataComponents.CUSTOM_MODEL_DATA)!!.floats().single())
        assertTrue(legacy.asJsonObject.getAsJsonObject("components").get("minecraft:custom_model_data").isJsonPrimitive)
    }

    @Test fun `current saved stacks are versioned and round trip through JSON and NBT`() {
        val stack = ItemStack(Items.APPLE, 3)
        val json = SavedPokemonItemCodec.CODEC.encodeStart(ops, stack).getOrThrow()
        assertEquals(SharedConstants.getCurrentVersion().dataVersion().version(),
            json.asJsonObject.get(SavedPokemonItemCodec.VERSION_KEY).asInt)
        assertTrue(ItemStack.matches(stack, SavedPokemonItemCodec.CODEC.parse(ops, json).getOrThrow()))
        val nbtOps = access.createSerializationContext(NbtOps.INSTANCE)
        val nbt = SavedPokemonItemCodec.CODEC.encodeStart(nbtOps, stack).getOrThrow()
        assertTrue(ItemStack.matches(stack, SavedPokemonItemCodec.CODEC.parse(nbtOps, nbt).getOrThrow()))
        val oldPrivate = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()
        assertTrue(ItemStack.matches(stack, SavedPokemonItemCodec.CODEC.parse(ops, oldPrivate).getOrThrow()))
    }

    @Test fun `invalid present item never becomes a successful empty optional`() {
        val field = StrictOptionalFieldCodec("HeldItem", SavedPokemonItemCodec.CODEC).codec()
        assertTrue(field.parse(ops, JsonParser.parseString("{}")).getOrThrow().isEmpty)
        val invalid = field.parse(ops, JsonParser.parseString("""{"HeldItem":{"id":"missing:removed_item","count":1}}"""))
        assertTrue(invalid.isError)
        assertTrue(invalid.resultOrPartial {}.isEmpty)
        val future = JsonParser.parseString("""{"id":"minecraft:apple","count":1,"cobblemon:minecraft_data_version":999999}""")
        assertTrue(SavedPokemonItemCodec.CODEC.parse(ops, future).isError)
    }
}
