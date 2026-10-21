/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.BindGroupLayouts

/** Preserves upstream emissive material culling, optional blending and depth writing using native pipelines. */
object PokemonMaterials {
    private fun pipeline(translucent: Boolean): RenderPipeline {
        val builder=RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cobblemon",if(translucent) "emissive_translucent" else "emissive_cutout"))
            .withShaderDefine("ALPHA_CUTOUT",.1f)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withCull(true)
        if(translucent) builder.withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        return com.cobblemon.mod.common.client.render.IrisMaterialBridge.register(
            RenderPipelines.register(builder.build()), "EMISSIVE_ENTITIES")
    }
    private val cutout=pipeline(false)
    private val translucent=pipeline(true)
    private val types=mutableMapOf<Pair<Identifier,Boolean>,RenderType>()
    init {
        // Iris builds its program overrides before the first world frame, not when particles appear.
        com.cobblemon.mod.common.api.snowstorm.ParticleMaterials.ALPHA
    }
    fun initialize() = Unit
    fun clearTextures() { types.clear(); translucentLayers.clear() }
    fun emissive(texture: Identifier, blend: Boolean): RenderType = types.getOrPut(texture to blend) {
        val builder=RenderSetup.builder(if(blend) translucent else cutout)
            .withTexture("Sampler0",texture).useOverlay()
        if(blend) builder.sortOnUpload()
        RenderType.create("cobblemon_emissive",builder.createRenderSetup())
    }
    private val translucentLayers = mutableMapOf<net.minecraft.resources.Identifier, net.minecraft.client.renderer.rendertype.RenderType>()

    fun extractLayers(identifier: net.minecraft.resources.Identifier,
                      state: com.cobblemon.mod.common.client.render.models.blockbench.PosableState,
                      baseColor: Int = -1): List<PokemonLayerSubmission> {
        return com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository.getLayers(identifier, state).mapNotNull { layer ->
            val texture = layer.texture?.invoke(state) ?: return@mapNotNull null
            val type = when {
                layer.emissive -> emissive(texture, layer.translucent)
                layer.translucent_cull -> translucentLayers.getOrPut(texture) {
                    net.minecraft.client.renderer.rendertype.RenderType.create("cobblemon_translucent_layer",
                        net.minecraft.client.renderer.rendertype.RenderSetup.builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                            .withTexture("Sampler0", texture).useLightmap().useOverlay().sortOnUpload().createRenderSetup())
                }
                layer.translucent -> net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture)
                else -> net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutCull(texture)
            }
            val c = layer.tint
            val color = net.minecraft.util.ARGB.colorFromFloat(c.w, c.x, c.y, c.z)
            PokemonLayerSubmission(type, net.minecraft.util.ARGB.multiply(baseColor, color), layer.emissive,
                ((layer.scrolling?.speedU ?: 0F) * state.animationSeconds) % 1F,
                ((layer.scrolling?.speedV ?: 0F) * state.animationSeconds) % 1F)
        }
    }

}
