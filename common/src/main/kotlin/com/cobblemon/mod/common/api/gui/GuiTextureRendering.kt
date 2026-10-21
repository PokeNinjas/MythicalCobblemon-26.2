/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.api.gui

import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.joml.Matrix3x2f

private object GuiTexturePipelines {
    val opaque: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cobblemon", "pipeline/gui_textured_opaque"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build()
    )
}

/** Extracts a textured GUI quad with its own transform, tint, scissor and material. */
@JvmOverloads
fun blitk(
    context: GuiGraphicsExtractor,
    texture: Identifier,
    x: Number,
    y: Number,
    height: Number = 0,
    width: Number = 0,
    uOffset: Number = 0,
    vOffset: Number = 0,
    textureWidth: Number = width,
    textureHeight: Number = height,
    red: Number = 1,
    green: Number = 1,
    blue: Number = 1,
    alpha: Number = 1F,
    blend: Boolean = true,
    scale: Float = 1F
) {
    val w = width.toFloat()
    val h = height.toFloat()
    if (w == 0F || h == 0F || scale == 0F || alpha.toFloat() <= 0F) return
    val tw = textureWidth.toFloat()
    val th = textureHeight.toFloat()
    require(tw != 0F && th != 0F) { "GUI texture dimensions must be nonzero: $texture" }

    // A transformed unit quad retains subpixel coordinates and arbitrary scaling.
    // The copied matrix belongs to this submission and cannot change with later widgets.
    val pose = Matrix3x2f(context.pose()).scale(scale).translate(x.toFloat(), y.toFloat()).scale(w, h)
    val image = Minecraft.getInstance().textureManager.getTexture(texture)
    fun channel(value: Number) = (value.toFloat().coerceIn(0F, 1F) * 255F).toInt()
    context.guiRenderState.addGuiElement(
        BlitRenderState(
            if (blend) RenderPipelines.GUI_TEXTURED else GuiTexturePipelines.opaque,
            TextureSetup.singleTexture(image.textureView, image.sampler),
            pose, 0, 0, 1, 1,
            uOffset.toFloat() / tw, (uOffset.toFloat() + w) / tw,
            vOffset.toFloat() / th, (vOffset.toFloat() + h) / th,
            ARGB.color(channel(alpha), channel(red), channel(green), channel(blue)),
            context.scissorStack.peek()
        )
    )
}
