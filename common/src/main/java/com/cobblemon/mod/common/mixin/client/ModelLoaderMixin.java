/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Preserve discovery of addon egg/TM models without injecting into vanilla's model baker. */
@Mixin(ClientItemInfoLoader.class)
public abstract class ModelLoaderMixin {
    @Inject(method = "scheduleLoad", at = @At("RETURN"), cancellable = true)
    private static void cobblemon$discoverDynamicItems(ResourceManager manager, Executor executor,
        CallbackInfoReturnable<CompletableFuture<ClientItemInfoLoader.LoadedClientInfos>> cir) {
        cir.setReturnValue(cir.getReturnValue().thenApplyAsync(loaded -> {
            var contents = new HashMap<>(loaded.contents());
            for (String folder : List.of("egg", "tm")) {
                manager.listResources("models/item/" + folder, id -> id.getPath().endsWith(".json")).forEach((path, resource) -> {
                    String relative = path.getPath().substring("models/item/".length(), path.getPath().length() - 5);
                    Identifier id = Identifier.fromNamespaceAndPath(path.getNamespace(), relative);
                    Identifier definition = Identifier.fromNamespaceAndPath(path.getNamespace(), "items/" + relative + ".json");
                    // Explicit definitions win even when invalid, so their validation errors remain visible.
                    if (!contents.containsKey(id) && manager.getResource(definition).isEmpty()) {
                        Identifier geometry = Identifier.fromNamespaceAndPath(path.getNamespace(), "item/" + relative);
                        contents.put(id, new ClientItem(new CuboidItemModelWrapper.Unbaked(geometry, Optional.empty(), List.of()), ClientItem.Properties.DEFAULT));
                    }
                });
            }
            return new ClientItemInfoLoader.LoadedClientInfos(contents);
        }, executor));
    }
}
