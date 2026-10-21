/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.mojang.serialization.MapCodec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.resources.model.ResolvableModel
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Matrix4fc

class CobblemonBuiltinItemModel(private val transform: Matrix4fc) : ItemModel {
    override fun update(output: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, mode: ItemDisplayContext,
                        level: ClientLevel?, owner: ItemOwner?, seed: Int) {
        output.appendModelIdentityElement(this)
        requireNotNull(CobblemonBuiltinItemRendererRegistry.rendererOf(stack.item)) {
            "No Cobblemon builtin renderer registered for ${stack.item}"
        }.update(output, stack, resolver, mode, level, owner, seed, transform)
    }
    object Unbaked : ItemModel.Unbaked {
        val CODEC: MapCodec<Unbaked> = MapCodec.unit(this)
        override fun type() = CODEC
        override fun resolveDependencies(resolver: ResolvableModel.Resolver) = Unit
        override fun bake(context: ItemModel.BakingContext, transformation: Matrix4fc): ItemModel = CobblemonBuiltinItemModel(Matrix4f(transformation))
    }
}
