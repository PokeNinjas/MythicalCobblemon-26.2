/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui

import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay.Companion.PORTRAIT_DIAMETER
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.util.toHex
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.LightCoordsUtil as LightTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix4f
import org.joml.Vector3f

/** Preserve the legacy helper's RGB-as-opaque contract; native extraction expects ARGB. */
internal fun legacyTextColour(colour: Int): Int =
    if (colour ushr 24 == 0) colour or 0xFF000000.toInt() else colour

@JvmOverloads
fun drawCenteredText(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: Component,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true
) {
    val comp = (text as MutableComponent).let { if (font != null) it.font(font) else it }
    val textRenderer = Minecraft.getInstance().font
    context.text(textRenderer, comp, x.toInt() - textRenderer.width(comp) / 2, y.toInt(), legacyTextColour(colour), shadow)
}

@JvmOverloads
fun drawText(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: MutableComponent,
    x: Number,
    y: Number,
    centered: Boolean = false,
    colour: Int,
    shadow: Boolean = true,
    pMouseX: Number? = null,
    pMouseY: Number? = null
): Boolean {
    val comp = if (font == null) text else text.setStyle(text.style.withFont(net.minecraft.network.chat.FontDescription.Resource(font)))
    val textRenderer = Minecraft.getInstance().font
    var x = x
    val width = textRenderer.width(comp)
    if (centered) {
        x = x.toDouble() - width / 2
    }
    context.text(textRenderer, comp, x.toInt(), y.toInt(), legacyTextColour(colour), shadow)
    var isHovered = false
    if (pMouseY != null && pMouseX != null) {
        if (pMouseX.toInt() >= x.toInt() && pMouseX.toInt() <= x.toInt() + width &&
            pMouseY.toInt() >= y.toInt() && pMouseY.toInt() <= y.toInt() + textRenderer.lineHeight
        ) {
            isHovered = true
        }
    }
    return isHovered
}

@JvmOverloads
fun drawTextJustifiedRight(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: MutableComponent,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true
) {
    val comp = text.let { if (font != null) it.font(font) else it }
    val font = Minecraft.getInstance().font
    context.text(font, comp, x.toInt() - font.width(comp), y.toInt(), legacyTextColour(colour), shadow)
}

@JvmOverloads
fun drawText(
    context: GuiGraphicsExtractor,
    text: FormattedCharSequence,
    x: Number,
    y: Number,
    centered: Boolean = false,
    colour: Int,
    shadow: Boolean = true
) {
    val textRenderer = Minecraft.getInstance().font
    var tweakedX = x
    if (centered) {
        val width = textRenderer.width(text)
        tweakedX = tweakedX.toDouble() - width / 2
    }
    context.text(textRenderer, text, tweakedX.toInt(), y.toInt(), legacyTextColour(colour), shadow)
}

@JvmOverloads
fun drawString(
    context: GuiGraphicsExtractor,
    text: String,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true,
    font: Identifier? = null
) {
    val comp = Component.literal(text).also {
        font?.run {
            it.style = it.style.withFont(net.minecraft.network.chat.FontDescription.Resource(this))
        }
    }
    val textRenderer = Minecraft.getInstance().font
    context.text(textRenderer, comp, x.toInt(), y.toInt(), legacyTextColour(colour), shadow)
}

@JvmOverloads
fun drawPosablePortrait(
    identifier: Identifier,
    context: GuiGraphicsExtractor,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F, g: Float = 1F, b: Float = 1F, a: Float = 1F
) {
    val sprite = VaryingModelRepository.getSprite(identifier, state, SpriteType.PORTRAIT)
    if (sprite != null) {
        blitk(context, sprite, -scale, PORTRAIT_DIAMETER + 2F - scale * PORTRAIT_DIAMETER / 18F,
            width = scale * 2, height = scale * 2, red = r, green = g, blue = b, alpha = a)
        return
    }
    val originalPose = state.currentPose
    try {
        com.cobblemon.mod.common.client.gui.NativeGuiModels.draw(context, identifier, state, partialTicks,
            PoseType.PORTRAIT, contextScale, doQuirks, toHex(r, g, b, a), LightTexture.pack(11, 7),
            headYaw, headPitch, limbSwing, limbSwingAmount, ageInTicks) { model, modelPose ->
            modelPose.translate(0F, PORTRAIT_DIAMETER + 2F, 0F)
            modelPose.scale(scale, scale, scale)
            modelPose.translate(0.0, -PORTRAIT_DIAMETER / 18.0, 0.0)
            modelPose.translate(model.portraitTranslation.x * if (reversed) -1F else 1F,
                model.portraitTranslation.y + 1.5 * model.portraitScale, model.portraitTranslation.z - 4)
            modelPose.scale(model.portraitScale, model.portraitScale, 1F / model.portraitScale)
            modelPose.mulPose(Axis.YP.rotationDegrees(-32F * if (reversed) -1F else 1F))
            modelPose.mulPose(Axis.XP.rotationDegrees(5F))
        }
    } finally {
        originalPose?.let(state::setPose)
    }
}

fun drawProfile(resourceIdentifier: Identifier, context: GuiGraphicsExtractor, state: PosableState, partialTicks: Float, scale: Float = 20F) {
    val sprite = VaryingModelRepository.getSprite(resourceIdentifier, state, SpriteType.PROFILE)
    if (sprite != null) {
        blitk(context, sprite, -scale, 0, width = scale * 2, height = scale * 2)
        return
    }
    com.cobblemon.mod.common.client.gui.NativeGuiModels.draw(context, resourceIdentifier, state, partialTicks,
        PoseType.PORTRAIT, 1F, true, -1, LightTexture.pack(11, 7)) { model, modelPose ->
        modelPose.scale(scale, scale, scale)
        modelPose.translate(model.profileTranslation.x, model.profileTranslation.y + 1.5 * model.profileScale, model.profileTranslation.z - 4)
        modelPose.scale(model.profileScale, model.profileScale, 1F / model.profileScale)
        modelPose.mulPose(Axis.YP.rotationDegrees(-32F))
        modelPose.mulPose(Axis.XP.rotationDegrees(5F))
    }
}

fun renderSprite(context: GuiGraphicsExtractor, sprite: Identifier) = blitk(context, sprite, -1, 0, width = 2, height = 2)
