/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.api.text.gray
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.block.Block
import java.util.function.Consumer

class HealingMachineItem(block: Block, properties: Properties) : BlockItem(block, properties.overrideDescription(block.descriptionId)) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: TooltipDisplay, tooltip: Consumer<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, display, tooltip, flag)
        tooltip.accept("block.cobblemon.healing_machine.tooltip1".asTranslated().gray())
        tooltip.accept("block.cobblemon.healing_machine.tooltip2".asTranslated().gray())
    }
}
