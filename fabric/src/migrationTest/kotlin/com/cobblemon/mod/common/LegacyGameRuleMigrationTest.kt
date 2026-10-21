package com.cobblemon.mod.common

import com.cobblemon.mod.common.util.LegacyGameRuleMigration
import com.google.gson.JsonParser
import com.mojang.serialization.Dynamic
import com.mojang.serialization.JsonOps
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.util.datafix.fixes.GameRuleRegistryFix
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LegacyGameRuleMigrationTest {
    @Test fun `native rule conversion retains Cobblemon strings for the migration hook`() {
        val original = Dynamic(JsonOps.INSTANCE, JsonParser.parseString("""{
            "doPokemonSpawning":"false", "battleInvulnerability":"true",
            "doPokemonLoot":"false", "mobTargetInBattle":"false",
            "doShinyStarters":"true", "healersHealPC":"true", "keepInventory":"true"
        }"""))
        val converter = GameRuleRegistryFix::class.java.getDeclaredMethod("lambda\$makeRule\$2", Dynamic::class.java)
        converter.isAccessible = true
        val vanilla = converter.invoke(null, original) as Dynamic<*>
        val result = LegacyGameRuleMigration.migrate(vanilla)
        for ((name, expected) in mapOf("do_pokemon_spawning" to false, "battle_invulnerability" to true,
            "do_pokemon_loot" to false, "mob_target_in_battle" to false,
            "do_shiny_starters" to true, "healers_heal_pc" to true)) {
            assertEquals(expected, result.get("cobblemon:$name").asBoolean().result().orElseThrow())
        }
        assertTrue(result.get("minecraft:keep_inventory").asBoolean(false))
        assertTrue(result.get("doPokemonSpawning").result().isEmpty)
        assertEquals(result, LegacyGameRuleMigration.migrate(result))
    }

    @Test fun `NBT migration preserves modern values and unrelated data`() {
        val tag = CompoundTag().apply {
            putString("doPokemonSpawning", "false")
            putBoolean("cobblemon:do_pokemon_spawning", true)
            putString("doPokemonLoot", "invalid")
            putString("otherModRule", "custom")
        }
        val result = LegacyGameRuleMigration.migrate(Dynamic(NbtOps.INSTANCE, tag))
        assertTrue(result.get("cobblemon:do_pokemon_spawning").asBoolean(false))
        assertEquals("invalid", result.get("doPokemonLoot").asString().result().orElseThrow())
        assertTrue(result.get("cobblemon:do_pokemon_loot").result().isEmpty)
        assertEquals("custom", result.get("otherModRule").asString().result().orElseThrow())
        assertTrue(tag.contains("doPokemonSpawning"), "Input save data must not be mutated")
    }
}
