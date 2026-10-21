/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.toast

import com.cobblemon.mod.common.net.messages.client.toast.ToastPacket
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import java.util.*
import kotlin.math.min
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence

@Environment(EnvType.CLIENT)
class CobblemonToast(
    val id: UUID,
    var icons: List<ItemStack>,
    var title: Component,
    var description: Component,
    var frameTexture: Identifier,
    var progress: Float,
    var progressColor: Int,
    var durationMs: Long? = null
) : Toast {

    constructor(packet: ToastPacket) : this(
        packet.uuid,
        packet.icons,
        packet.title,
        packet.description,
        packet.frameTexture,
        packet.progress,
        packet.progressColor,
        packet.durationMs
    )

    private var startTime: Long = -1
    private var lastProgress = 0F
    private var lastTime = 0L
    internal var nextVisibility: Toast.Visibility = Toast.Visibility.SHOW

    fun getLines(font: Font, maxWidth: Int): List<FormattedCharSequence> {
        return font.split(this.description, maxWidth)
    }

    override fun getWantedVisibility(): Toast.Visibility = nextVisibility
    override fun getToken(): Any = id

    override fun update(manager: ToastManager, fullyVisibleForMs: Long) {
        if (startTime == -1L) startTime = fullyVisibleForMs
        val elapsed = fullyVisibleForMs - startTime
        durationMs?.let { duration ->
            if (elapsed >= duration) nextVisibility = Toast.Visibility.HIDE
        }
        if (hasProgressBar()) {
            lastProgress = Mth.clampedLerp(lastProgress, progress, (fullyVisibleForMs - lastTime).toFloat() / 100F)
        }
        lastTime = fullyVisibleForMs
    }

    override fun onFinishedRendering() {
        ToastTracker.remove(this)
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, font: Font, fullyVisibleForMs: Long) {
        val elapsedTime = (fullyVisibleForMs - startTime.coerceAtLeast(0L)).coerceAtLeast(0L)
        val localDuration = durationMs
        val maxWidth = 125
        val frameHeight = height() + (getLines(font, maxWidth).size - 1).coerceAtLeast(0) * 10
        context.blitSprite(RenderPipelines.GUI_TEXTURED, frameTexture, 0, 0, width(), frameHeight)
        context.text(font, title, 30, 7, (title.style.color?.value ?: -1) or 0xFF000000.toInt(), false)
        context.textWithWordWrap(font, description, 30, 18, maxWidth, (description.style.color?.value ?: -1) or 0xFF000000.toInt())

        if (icons.isNotEmpty()) {
            val iconIndex = if (localDuration != null && localDuration > 0) {
                val repeatsPerIcon = 2
                val totalIcons = icons.size * repeatsPerIcon
                val timePerIcon = (localDuration / totalIcons).coerceAtLeast(1L)
                (elapsedTime / timePerIcon).coerceIn(0L, (totalIcons - 1).toLong()).toInt() / repeatsPerIcon
            } else {
                ((elapsedTime / 500L) % icons.size).toInt()
            }
            context.fakeItem(icons[iconIndex], 8, 8)
        }
        if (hasProgressBar()) {
            context.fill(3, 28, 157, 29, -1)
            context.fill(3, 28, (3F + 154F * lastProgress).toInt(), 29, progressColor)
        }
    }

    internal fun updateFrom(packet: ToastPacket) {
        val newIcons = this.icons + packet.icons
        this.icons = newIcons
        this.title = packet.title
        this.description = packet.description
        this.frameTexture = packet.frameTexture
        this.progress = packet.progress
        this.progressColor = packet.progressColor
        this.lastProgress = min(this.progress, this.lastProgress)
        this.nextVisibility = when (packet.behaviour) {
            ToastPacket.Behaviour.SHOW_OR_UPDATE -> Toast.Visibility.SHOW
            ToastPacket.Behaviour.HIDE -> Toast.Visibility.HIDE
        }
    }

    private fun hasProgressBar(): Boolean = this.progress in 0F..1F

}
