/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.platform.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState

/** Native submission event. Material pipelines determine the eventual draw order. */
data class RenderEvent(
    val stage: Stage,
    val levelRenderer: LevelRenderer,
    val poseStack: PoseStack,
    val collector: SubmitNodeCollector,
    val camera: CameraRenderState,
    val gameTime: Long
) {
    enum class Stage { SUBMIT }
}
