/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import java.util.function.BiFunction
import java.util.function.Function

/** Shared native material factories; pipeline state is owned by Minecraft. */
object CobblemonRenderLayers {
    val BERRY_LAYER: RenderType = RenderTypes.entityCutout(cobblemonResource("textures/atlas/berries.png"))
    val ENTITY_TRANSLUCENT = BiFunction<Identifier, Boolean, RenderType>(RenderTypes::entityTranslucent)
    val ENTITY_CUTOUT = Function<Identifier, RenderType>(RenderTypes::entityCutoutCull)
}
