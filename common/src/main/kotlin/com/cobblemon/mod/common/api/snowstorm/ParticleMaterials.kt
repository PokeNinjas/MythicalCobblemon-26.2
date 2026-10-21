/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.snowstorm

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.BlendFactor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier

/** Fixed native pipeline states reproduce Snowstorm's four original blend/cull/depth combinations. */
object ParticleMaterials {
    private fun material(name: String, cull: Boolean, blend: BlendFunction): RenderType {
        val pipeline = com.cobblemon.mod.common.client.render.IrisMaterialBridge.register(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cobblemon", "pipeline/snowstorm_$name"))
            .withCull(cull).withColorTargetState(ColorTargetState(blend)).build()),
            if (name == "opaque") "PARTICLES" else "PARTICLES_TRANSLUCENT")
        return RenderType.create("cobblemon_snowstorm_$name", RenderSetup.builder(pipeline)
            .withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES).useLightmap().sortOnUpload().createRenderSetup())
    }
    val ALPHA = material("alpha", true, BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA))
    val ADD = material("add", false, BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE))
    val BLEND = material("blend", false, BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA))
    val OPAQUE = material("opaque", true, BlendFunction(BlendFactor.SRC_COLOR, BlendFactor.ZERO))
}
