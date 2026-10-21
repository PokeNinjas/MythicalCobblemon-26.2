/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.player.MountedLivingRenderState;
import com.cobblemon.mod.common.client.render.player.MountedRiderPose;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class MountedLivingRenderStateMixin implements MountedLivingRenderState, com.cobblemon.mod.common.client.render.NamedVillagerTexture.State {
    @Unique private com.cobblemon.mod.common.client.render.NamedVillagerTexture cobblemon$namedVillagerTexture;
    @Override public com.cobblemon.mod.common.client.render.NamedVillagerTexture cobblemon$getNamedVillagerTexture() { return cobblemon$namedVillagerTexture; }
    @Override public void cobblemon$setNamedVillagerTexture(com.cobblemon.mod.common.client.render.NamedVillagerTexture texture) { cobblemon$namedVillagerTexture = texture; }
    @Unique private Matrix4f cobblemon$seatTransform;
    @Unique private boolean cobblemon$riderHidden;
    @Override public boolean getRiderHidden() { return cobblemon$riderHidden; }
    @Override public void setRiderHidden(boolean hidden) { cobblemon$riderHidden = hidden; }
    @Unique private MountedRiderPose cobblemon$riderPose;
    @Override public Matrix4f getSeatTransform() { return cobblemon$seatTransform; }
    @Override public void setSeatTransform(Matrix4f transform) { cobblemon$seatTransform = transform; }
    @Override public MountedRiderPose getRiderPose() { return cobblemon$riderPose; }
    @Override public void setRiderPose(MountedRiderPose pose) { cobblemon$riderPose = pose; }
}
