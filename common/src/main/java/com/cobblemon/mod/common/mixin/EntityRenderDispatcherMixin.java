/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.PosableEntity;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep locator boxes alongside native F3+B hitbox gizmos. */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "showHitboxes", at = @At("TAIL"))
    private void cobblemon$showLocators(Entity entity, float partialTicks, boolean serverEntity, CallbackInfo ci) {
        if (!serverEntity && entity instanceof PosableEntity posable && posable.getDelegate() instanceof PosableState state) {
            state.getLocatorStates().forEach((name, locator) -> {
                var position = locator.getOrigin();
                if (Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)) {
                    Gizmos.cuboid(AABB.ofSize(position, .25, .25, .25), GizmoStyle.stroke(0xFF00FF00));
                }
            });
        }
    }
}
