/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

val BlockPos.center: Vec3 get() = Vec3.atCenterOf(this)
val BlockPos.bottomCenter: Vec3 get() = Vec3.atBottomCenterOf(this)
