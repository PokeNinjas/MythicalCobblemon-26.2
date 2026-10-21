/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/** GroundPathNavigation in 26.2 omits Nullable while returning PathNavigation's nullable result. */
public abstract class NullableGroundPathNavigation extends GroundPathNavigation {
    protected NullableGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    public final @Nullable Path createPath(BlockPos target, int reachRange) {
        return createCobblemonPath(target, reachRange);
    }

    @Override
    public final @Nullable Path createPath(Entity target, int reachRange) {
        return createCobblemonPath(target.blockPosition(), reachRange);
    }

    protected abstract @Nullable Path createCobblemonPath(BlockPos target, int reachRange);
}
