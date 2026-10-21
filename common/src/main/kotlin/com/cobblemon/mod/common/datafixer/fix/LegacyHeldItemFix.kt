package com.cobblemon.mod.common.datafixer.fix

import com.mojang.datafixers.DSL
import com.mojang.datafixers.DataFix
import com.mojang.datafixers.TypeRewriteRule
import com.mojang.datafixers.schemas.Schema
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References

/** Keep recursive vanilla item types inside the vanilla schema family. */
class LegacyHeldItemFix(schema: Schema) : DataFix(schema, false) {
    override fun makeRule(): TypeRewriteRule = fixTypeEverywhereTyped(
        "Cobblemon legacy item componentization", inputSchema.getType(References.ITEM_STACK)
    ) { typed ->
        typed.update(DSL.remainderFinder()) { item ->
            // Version-zero Cobblemon records used pre-component item stacks.
            // Already componentized stacks must retain their current data.
            if (item.get("Count").result().isPresent) {
                DataFixers.getDataFixer().update(References.ITEM_STACK, item, 3818, 3955)
            } else item
        }
    }
}
