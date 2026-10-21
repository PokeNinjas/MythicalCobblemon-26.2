/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.item.NamedBlockItem
import net.minecraft.world.level.block.Block

class WearableBlockItem(properties: Properties, override val name: String, block: Block): NamedBlockItem(block, properties.equippable(net.minecraft.world.entity.EquipmentSlot.HEAD)), WearableItem{

}