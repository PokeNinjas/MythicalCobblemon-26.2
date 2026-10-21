package com.cobblemon.mod.common.util;

import com.mojang.serialization.Dynamic;
import java.util.Map;

/** Converts the original string-valued rules alongside vanilla's registry migration. */
public final class LegacyGameRuleMigration {
    private static final Map<String, String> RULES = Map.of(
        "battleInvulnerability", "cobblemon:battle_invulnerability",
        "doPokemonSpawning", "cobblemon:do_pokemon_spawning",
        "doPokemonLoot", "cobblemon:do_pokemon_loot",
        "mobTargetInBattle", "cobblemon:mob_target_in_battle",
        "doShinyStarters", "cobblemon:do_shiny_starters",
        "healersHealPC", "cobblemon:healers_heal_pc"
    );

    private LegacyGameRuleMigration() {}

    public static Dynamic<?> migrate(Dynamic<?> rules) {
        for (var entry : RULES.entrySet()) {
            var legacy = rules.get(entry.getKey()).asString().result();
            if (legacy.isEmpty()) continue;
            String value = legacy.get();
            // Do not turn malformed data into a false value or overwrite an
            // explicitly saved modern rule in a partially converted fixture.
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) continue;
            if (rules.get(entry.getValue()).result().isEmpty()) {
                rules = rules.set(entry.getValue(), rules.createBoolean(Boolean.parseBoolean(value)));
            }
            rules = rules.remove(entry.getKey());
        }
        return rules;
    }
}
