/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.entity.MountedLivingEntityRenderer;
import com.cobblemon.mod.common.client.render.player.MountedLivingRenderState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void cobblemon$extractSeat(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
        var namedState = (com.cobblemon.mod.common.client.render.NamedVillagerTexture.State) state;
        namedState.cobblemon$setNamedVillagerTexture(null);
        net.minecraft.world.entity.npc.villager.VillagerData data = null;
        boolean zombie = entity instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager;
        if (entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) data = villager.getVillagerData();
        else if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager villager) data = villager.getVillagerData();
        if (data != null) {
            var choice = com.cobblemon.mod.common.CobblemonVillagerProfessions.INSTANCE.getNameTagOverride(data.profession().value());
            var key = data.profession().unwrapKey().orElse(null);
            if (choice != null && key != null && key.identifier().getNamespace().equals("cobblemon")) {
                String name = net.minecraft.ChatFormatting.stripFormatting(entity.getName().getString());
                for (String suffix : choice.getSecond()) {
                    if (name != null && name.endsWith(suffix)) {
                        String directory = "textures/entity/" + (zombie ? "zombie_villager" : "villager") + "/profession/";
                        var original = key.identifier().withPath(directory + key.identifier().getPath() + ".png");
                        var override = key.identifier().withPath(directory + choice.getFirst() + ".png");
                        namedState.cobblemon$setNamedVillagerTexture(new com.cobblemon.mod.common.client.render.NamedVillagerTexture(original, override));
                        break;
                    }
                }
            }
        }
        var mounted = (MountedLivingRenderState)state;
        mounted.setRiderPose(null);
        mounted.setRiderHidden(false);
        mounted.setSeatTransform(entity.getVehicle() instanceof PokemonEntity pokemon
            ? MountedLivingEntityRenderer.extract(entity, pokemon, partial, state.bodyRot, mounted) : null);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void cobblemon$hideCollapsedSeatRider(LivingEntityRenderState state, PoseStack poses,
                                               SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (((MountedLivingRenderState)state).getRiderHidden()) ci.cancel();
    }

    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void cobblemon$applySeat(LivingEntityRenderState state, PoseStack poses, float bodyRot, float scale, CallbackInfo ci) {
        var transform = ((MountedLivingRenderState)state).getSeatTransform();
        if (transform != null) poses.mulPose(transform);
    }

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private void cobblemon$applyRiderRoot(LivingEntityRenderState state, PoseStack poses, SubmitNodeCollector collector,
                                         CameraRenderState camera, CallbackInfo ci) {
        var pose = ((MountedLivingRenderState)state).getRiderPose();
        if (pose != null) poses.mulPose(pose.getRootTransform());
    }
}
