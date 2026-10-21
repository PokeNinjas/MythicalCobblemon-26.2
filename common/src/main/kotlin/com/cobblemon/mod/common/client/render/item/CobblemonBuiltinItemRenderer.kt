/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Matrix4fc

/** Native item model adapter used by cobblemon:builtin item definitions. */
interface CobblemonBuiltinItemRenderer : ItemModel {
    fun clear() {}
    fun update(output: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, mode: ItemDisplayContext,
               level: ClientLevel?, owner: ItemOwner?, seed: Int, transform: Matrix4fc)
    override fun update(output: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, mode: ItemDisplayContext,
                        level: ClientLevel?, owner: ItemOwner?, seed: Int) =
        update(output, stack, resolver, mode, level, owner, seed, Matrix4f())
}
