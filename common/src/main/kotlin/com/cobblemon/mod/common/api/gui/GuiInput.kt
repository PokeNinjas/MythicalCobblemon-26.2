/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.api.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/** For rendering and callbacks without an input event; event handlers use the event's modifiers. */
fun isShiftHeld(): Boolean {
    val window = Minecraft.getInstance().window
    return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
        InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)
}
