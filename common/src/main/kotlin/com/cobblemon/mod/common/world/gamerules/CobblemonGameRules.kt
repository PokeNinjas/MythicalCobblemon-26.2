/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.gamerules

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleCategory

object CobblemonGameRules {

    @JvmField
    val BATTLE_INVULNERABILITY: GameRule<Boolean> = Cobblemon.implementation.registerBooleanGameRule("battleInvulnerability", GameRuleCategory.PLAYER, false)
    @JvmField
    val DO_POKEMON_SPAWNING: GameRule<Boolean> = Cobblemon.implementation.registerBooleanGameRule("doPokemonSpawning", GameRuleCategory.SPAWNING, true)
    @JvmField
    val DO_POKEMON_LOOT: GameRule<Boolean> = Cobblemon.implementation.registerBooleanGameRule("doPokemonLoot", GameRuleCategory.DROPS, true)
    @JvmField
    val MOB_TARGET_IN_BATTLE: GameRule<Boolean> = Cobblemon.implementation.registerBooleanGameRule("mobTargetInBattle", GameRuleCategory.MOBS, true)
    @JvmField
    val SHINY_STARTERS: GameRule<Boolean> = Cobblemon.implementation.registerBooleanGameRule("doShinyStarters", GameRuleCategory.MISC, false)
    @JvmField
    val HEALERS_HEAL_PC: GameRule<Boolean> = Cobblemon.implementation.registerBooleanGameRule("healersHealPC", GameRuleCategory.MISC, false)

}