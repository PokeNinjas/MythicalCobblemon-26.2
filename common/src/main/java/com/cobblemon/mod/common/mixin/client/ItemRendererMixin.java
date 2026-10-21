/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.item.PokedexItem;
import com.cobblemon.mod.common.item.WearableItem;
import com.cobblemon.mod.common.item.components.TMMoveComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select the native item definition once, before extracting its layers. */
@Mixin(ItemModelResolver.class)
public abstract class ItemRendererMixin {
    @Shadow @Final private ModelManager modelManager;

    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void cobblemon$selectModel(ItemStackRenderState output, ItemStack stack, ItemDisplayContext mode,
                                       Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        // Explicit item-model components (including lecterns and resource-pack customizations) take priority.
        Identifier requested = stack.get(DataComponents.ITEM_MODEL);
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(requested)) return;
        LivingEntity entity = owner == null ? null : owner.asLivingEntity();
        boolean flat = mode == ItemDisplayContext.GUI || mode == ItemDisplayContext.FIXED;
        Identifier selected = null;
        if (stack.getItem() instanceof PokeBallItem ball) {
            selected = flat ? ball.getPokeBall().getModel2d() : ball.getPokeBall().getModel3d();
        } else if (stack.getItem() instanceof PokedexItem pokedex) {
            boolean scanning = entity instanceof Player && entity.getUseItem() == stack && entity.isUsingItem();
            boolean active = entity != null && (entity.getMainHandItem() == stack ||
                (entity.getOffhandItem() == stack && !(entity.getMainHandItem().getItem() instanceof PokedexItem)));
            selected = flat ? pokedex.getType().getItemSpritePath() :
                pokedex.getType().getItemModelPath(scanning ? "scanning" : (active ? null : "off"));
        } else if (stack.getItem() instanceof WearableItem wearable) {
            selected = mode == ItemDisplayContext.HEAD ? wearable.getModel3d() : wearable.getModel2d();
        } else if (stack.getItem() == CobblemonItems.TECHNICAL_MACHINE) {
            TMMoveComponent component = stack.get(CobblemonItemComponents.TM_MOVE);
            if (component != null) selected = Identifier.fromNamespaceAndPath(Cobblemon.MODID,
                "tm/" + component.getMove().getElementalType().showdownId() + "_tm");
        }
        if (selected == null) return;
        if (selected.getPath().startsWith("item/")) {
            selected = Identifier.fromNamespaceAndPath(selected.getNamespace(), selected.getPath().substring(5));
        }
        output.setOversizedInGui(modelManager.getItemProperties(selected).oversizedInGui());
        modelManager.getItemModel(selected).update(output, stack, (ItemModelResolver) (Object) this, mode,
            level instanceof ClientLevel clientLevel ? clientLevel : null, owner, seed);
        ci.cancel();
    }
}
