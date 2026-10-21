package com.cobblemon.mod.common.pokemon

import com.cobblemon.mod.common.api.moves.Move
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.moves.Moves
import net.minecraft.nbt.CompoundTag

/** Keeps earned PP upgrades while a form-exclusive move is unavailable. */
internal object FormChangeMoveMemory {
    private const val KEY = "mythical:form_move_memory"
    private const val REPLACEMENT = "mythical:form_move_replacement"

    fun remember(data: CompoundTag, move: Move) {
        val memory = data.getCompoundOrEmpty(KEY)
        memory.put(move.name, CompoundTag().apply {
            putInt("pp", move.currentPp)
            putInt("ppUps", move.raisedPpStages)
        })
        data.put(KEY, memory)
    }

    fun rememberBenched(data: CompoundTag, template: MoveTemplate, ppUps: Int) {
        // A benched move has no current PP. Preserve the last active PP if known.
        val previous = restore(data, template)
        remember(data, template.create(previous?.currentPp ?: template.pp, ppUps))
    }

    fun restore(data: CompoundTag, template: MoveTemplate): Move? {
        val saved = data.getCompoundOrEmpty(KEY).getCompoundOrEmpty(template.name)
        val pp = saved.getInt("pp").orElse(-1)
        val stages = saved.getInt("ppUps").orElse(-1)
        if (pp < 0 || stages !in 0..3) return null
        return template.create(pp, stages).also { it.currentPp = pp.coerceAtMost(it.maxPp) }
    }

    fun rememberReplacement(data: CompoundTag, slot: Int, confusion: Move, signature: String) {
        data.put(REPLACEMENT, CompoundTag().apply {
            putInt("slot", slot); putString("signature", signature)
            putInt("pp", confusion.currentPp); putInt("ppUps", confusion.raisedPpStages)
        })
    }

    fun finishReplacement(data: CompoundTag, moves: MoveSet, removed: Map<Int, String>, nextSignatures: Set<String>) {
        val saved = data.getCompoundOrEmpty(REPLACEMENT)
        if (saved.isEmpty) return
        val signature = saved.getString("signature").orElse("")
        if (signature in nextSignatures) return
        // Consume this cycle if the player replaced the signature themselves.
        data.remove(REPLACEMENT)
        // Move reordering and saved MoveSet compaction must not lose the displaced move.
        val slot = removed.entries.firstOrNull { it.value == signature }?.key ?: return
        if (slot !in 0 until MoveSet.MOVE_COUNT) return
        val current = moves[slot]
        if (current != null && current.name in nextSignatures) {
            saved.putString("signature", current.name)
            data.put(REPLACEMENT, saved)
            return
        }
        if (current != null || moves.any { it.name == "confusion" }) return
        val pp = saved.getInt("pp").orElse(-1)
        val stages = saved.getInt("ppUps").orElse(-1)
        if (pp < 0 || stages !in 0..3) return
        val confusion = Moves.getByNameOrDummy("confusion").create(pp, stages)
        confusion.currentPp = pp.coerceAtMost(confusion.maxPp)
        moves.setMove(slot, confusion)
    }
}
