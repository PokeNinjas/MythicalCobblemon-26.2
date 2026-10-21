/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.ClientPlayerIcon;
import com.cobblemon.mod.common.client.render.layer.PokemonOnShoulderRenderer;
import com.cobblemon.mod.common.client.render.layer.ShoulderRenderState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin<T extends Avatar & ClientAvatarEntity>
        extends LivingEntityRenderer<T, AvatarRenderState, PlayerModel> {
    protected PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float radius) {
        super(context, model, radius);
    }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void cobblemon$addShoulderLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        addLayer(new PokemonOnShoulderRenderer(this));
    }
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void cobblemon$extractPlayer(T entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity.isPassenger() && entity.getVehicle() instanceof PokemonEntity) {
            // Both the native crouch offset and model crouch pose now consume this snapshot flag.
            state.isCrouching = false;
        }
        ((com.cobblemon.mod.common.client.render.player.MountedLivingRenderState)state).setRiderPose(
            entity instanceof net.minecraft.client.player.AbstractClientPlayer player
                ? com.cobblemon.mod.common.client.render.player.MountedPlayerRenderer.extract(player, state) : null);
        var shoulders = (ShoulderRenderState)state;
        shoulders.setShoulderPokemon(entity instanceof Player player
            ? PokemonOnShoulderRenderer.extract(player, state, partialTicks) : List.of());
        shoulders.setPlayerIcon(entity instanceof Player player ? ClientPlayerIcon.extract(player, partialTicks) : null);
    }

    @Override
    public void submit(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poses,
                       net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        super.submit(state, poses, collector, camera);
        ClientPlayerIcon.submit(((ShoulderRenderState)state).getPlayerIcon(), poses, collector);
    }
}
