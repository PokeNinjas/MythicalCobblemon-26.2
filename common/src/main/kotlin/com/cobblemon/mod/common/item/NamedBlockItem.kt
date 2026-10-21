/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.item

import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block

/** Placeable items whose translated name belongs to the item, rather than its block. */
open class NamedBlockItem(block: Block, properties: Properties) :
    BlockItem(block, properties.useItemDescriptionPrefix())
