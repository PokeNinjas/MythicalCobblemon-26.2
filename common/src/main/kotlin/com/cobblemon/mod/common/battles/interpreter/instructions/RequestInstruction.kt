/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.ShowdownActionRequest
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket
import com.google.gson.JsonParser

/**
 * Format: |request|REQUEST
 *
 * The actor needs to know its options for when it next has to make a decision.
 * @author Deltric
 * @since January 22nd, 2022
 */
class RequestInstruction(val battleActor: BattleActor, val message: BattleMessage): InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        battle.log("Request Instruction")

        val payload = JsonParser.parseString(message.rawMessage.substringAfter("|request|")).asJsonObject
        if (payload.get("teamPreview")?.asBoolean == true) {
            // Until a preview selection UI exists, retain party order. Showdown applies
            // the format's team-size restrictions when resolving this default choice.
            battle.writeShowdownAction(">${battleActor.showdownId} team")
            return
        }

        // Parse Json message and update state info for actor
        val request = BattleRegistry.gson.fromJson(payload, ShowdownActionRequest::class.java)
        request.sanitize(battle, battleActor)
        battle.dispatchGo {
            // This request won't be acted on until the start of next turn or post-`update` if there's a force switch
            battleActor.sendUpdate(BattleQueueRequestPacket(request))
            battleActor.request = request
            battleActor.responses.clear()
        }
    }

}
