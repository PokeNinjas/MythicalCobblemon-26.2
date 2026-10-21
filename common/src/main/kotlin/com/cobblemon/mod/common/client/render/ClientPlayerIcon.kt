/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.requests.ClientPlayerActionRequest
import com.cobblemon.mod.common.client.ClientMultiBattleTeamMember
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.entity.player.Player
import org.joml.Matrix4f
import java.util.*

/**
 * Visual notification rendered above a player's nametag for [ClientMultiBattleTeamMember]s and unanswered [ClientPlayerActionRequest]s.
 *
 * @param expiryTime The amount of time (in seconds) this icon is valid.
 *
 * @author Segfault Guy
 * @since September 1st, 2024
 */
abstract class ClientPlayerIcon(expiryTime: Int? = null) {

    /** The texture to use for the icon. */
    open val texture: Identifier = cobblemonResource("textures/particle/request/icon_exclamation.png")
    /** The offset of the icon from the player's head. */
    open val Y_OFFSET = 1.5F
    open val SCALE = 0.75F
    private val FADE_RATIO = 0.25F

    private val tickCount get() = Minecraft.getInstance().player?.tickCount ?: 0    // icon is only ticked when tracked, so derive lifetime from player tick - startTick
    private val startTick = Minecraft.getInstance().player?.tickCount ?: 0
    private val tickRateManager = Minecraft.getInstance().level!!.tickRateManager()
    /** The tick at which the icon begins fading. */
    private val fadeTickStamp = expiryTime?.let { it * tickRateManager.tickrate() * (1 - FADE_RATIO) + startTick}?.toInt() ?: Int.MAX_VALUE // TODO is server expiration based on epoc going to cause a problem?

    /** Duration (in ticks) of the fade transition. */
    open val fadeDuration = 30
    /** How long (in ticks) the icon has been fading. */
    private var fadeCount = 0
    private val fading get() = tickCount >= fadeTickStamp
    private var fadeOut = true
        set(value) {
            fadeCount = 0
            field = value
        }

    private val MAX_ALPHA = 1F
    private val MIN_ALPHA = 0.15F   // MC fragment shaders won't draw textures with < 0.1 alpha and the sudden cutoff from 0.1 to 0 and vice versa is jarring
    private var alpha = 1F
    private val startAlpha get() = if (fadeOut) MAX_ALPHA else MIN_ALPHA
    private val finalAlpha get() = if (fadeOut) MIN_ALPHA else MAX_ALPHA

    fun onTick() {
        if (!fading) return else fadeCount++
        if (fadeCount == fadeDuration) fadeOut = !fadeOut   // toggle transition

        // alpha changed outside of render deltaticks so fade does not freeze off screen
        alpha = Mth.lerp(fadeCount.toFloat()/fadeDuration, startAlpha, finalAlpha)
    }

    private fun extractIcon(player: Player, partialTicks: Float): PlayerIconFrame? {
        if (CobblemonClient.battle != null || player.isInvisible) return null
        val attachment = player.attachments.getNullable(EntityAttachment.NAME_TAG, 0, player.getViewYRot(partialTicks)) ?: return null
        val poses = PoseStack()
        poses.translate(attachment.x, attachment.y + Y_OFFSET, attachment.z)
        poses.mulPose(Axis.YP.rotationDegrees(180F - Minecraft.getInstance().gameRenderer.mainCamera().yRot()))
        poses.scale(SCALE, -SCALE, SCALE)
        return PlayerIconFrame(texture, Matrix4f(poses.last().pose()), net.minecraft.util.ARGB.colorFromFloat(alpha, 1F, 1F, 1F))
    }

    companion object {
        private val trackedIcons = mutableMapOf<UUID, ClientPlayerIcon>()

        fun onTick() = trackedIcons.values.forEach { it.onTick() }

        /** Updates the icon that is rendered above the player. */
        fun update(player: UUID) {
            val teamIcon = CobblemonClient.teamData.multiBattleTeamMembers.find { it.uuid == player }
            val requests = CobblemonClient.requests.getRequestsFrom(player).filterIsInstance<ClientPlayerIcon>()

            if (requests.isNotEmpty()) {
                val icon = requests.minBy { it.fadeTickStamp }  // icon that expires the soonest has priority
                trackedIcons[player] = icon
            }
            else if (teamIcon != null) {
                trackedIcons[player] = teamIcon
            }
            else {
                trackedIcons.remove(player)
            }
        }
        // TODO make some api so this is expandable? i cant be assed right now

        fun clear() = trackedIcons.clear()

        @JvmStatic fun extract(player: Player, partialTicks: Float) = trackedIcons[player.uuid]?.extractIcon(player, partialTicks)

        @JvmStatic fun submit(frame: PlayerIconFrame?, poses: PoseStack, collector: net.minecraft.client.renderer.SubmitNodeCollector) {
            if (frame == null) return
            poses.pushPose()
            poses.mulPose(frame.transform)
            collector.submitCustomGeometry(poses, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(frame.texture)) { pose, buffer ->
                fun vertex(x: Float, y: Float, u: Float, v: Float) {
                    buffer.addVertex(pose, x, y, 0F).setColor(frame.color).setUv(u, v)
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(15728880).setNormal(pose, 0F, 0F, 1F)
                }
                vertex(-.5F, 0F, 0F, 0F)
                vertex(-.5F, 1F, 0F, 1F)
                vertex(.5F, 1F, 1F, 1F)
                vertex(.5F, 0F, 1F, 0F)
            }
            poses.popPose()
        }
    }
}
/** Extracted icon presentation; deferred submission never reads a live player or request. */
data class PlayerIconFrame(val texture: Identifier, val transform: Matrix4f, val color: Int)
