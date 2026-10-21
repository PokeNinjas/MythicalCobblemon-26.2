/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.entity;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.duck.RidePassenger;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow @Nullable public abstract Entity getVehicle();

    @Inject(method = "stopRiding", at = @At("HEAD"))
    private void cobblemon$resetServerMountOrientation(CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.server.level.ServerPlayer
            && getVehicle() instanceof OrientationControllable vehicle) {
            vehicle.getOrientationController().setActive(false);
        }
    }

    @WrapOperation(method = "updateFluidInteraction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityFluidInteraction;isInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean cobblemon$riderFluid(net.minecraft.world.entity.EntityFluidInteraction fluids,
                                         net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluid,
                                         Operation<Boolean> original) {
        if (fluid.equals(net.minecraft.tags.FluidTags.WATER) && getVehicle() instanceof PokemonEntity) return false;
        return original.call(fluids, fluid);
    }

    @WrapOperation(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;onGround()Z"
            )
    )
    public boolean cobblemon$forceOnGroundForStepUp(Entity entity, Operation<Boolean> original) {
        if (entity instanceof PokemonEntity vehicle && entity.hasControllingPassenger()) {
            return true;
        }
        return original.call(entity);
    }

    @Inject(
            method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cobblemon$modifyEyePosition_partial(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        cobblemon$getCustomEyePos(cir);
    }

    @Inject(
            method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cobblemon$modifyEyePosition_noPartial(CallbackInfoReturnable<Vec3> cir) {
        cobblemon$getCustomEyePos(cir);
    }

    @Unique
    private void cobblemon$getCustomEyePos(CallbackInfoReturnable<Vec3> cir) {
        var entity = (Entity)(Object)this;
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (!(player instanceof RidePassenger ridePassenger)) return;
        if (!(player.getVehicle() instanceof OrientationControllable vehicle)) return;
        if (!(vehicle instanceof PokemonEntity)) return;
        var vehicleController = vehicle.getOrientationController();
        if (vehicleController == null) return;

        Vec3 customEyePos = ridePassenger.cobblemon$getRideEyePos();
        cir.setReturnValue(customEyePos);
    }


    @WrapOperation(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void cobblemon$weightedCollision(Entity receiver, double x, double y, double z, Operation<Void> original,
                                             @Local(argsOnly = true) Entity other) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player || self instanceof PokemonEntity)
            || !(other instanceof Player || other instanceof PokemonEntity)) {
            original.call(receiver, x, y, z);
            return;
        }
        float selfWeight = self instanceof PokemonEntity pokemon ? pokemon.getPokemon().getSpecies().getWeight() : 900F;
        float otherWeight = other instanceof PokemonEntity pokemon ? pokemon.getPokemon().getSpecies().getWeight() : 900F;
        float total = selfWeight + otherWeight;
        double share = total > 0 ? (receiver == self ? otherWeight : selfWeight) / total * 2.0 : 1.0;
        original.call(receiver, x * share, y, z * share);
    }
}
