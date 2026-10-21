package com.cobblemon.mod.common.entity.npc

import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.battles.BattleStartResult
import net.minecraft.server.level.ServerPlayer

/** Reports existing battle validation messages for tagged dungeon trainers only. */
object DungeonTrainerBattleFeedback {
    fun sendRejection(tags: Set<String>, result: BattleStartResult, initiator: ServerPlayer) {
        // Stable dungeon tags avoid a dependency on the optional activity mod.
        if ("md_kind:elite" !in tags || tags.none { it.startsWith("md_run:") && it.length > 7 }) return
        result.ifErrored { errors -> errors.sendTo(initiator) { it.red() } }
    }
}
