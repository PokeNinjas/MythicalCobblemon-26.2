/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.api.gui;

import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/** Regions captured with a GUI frame, in transformed GUI coordinates. */
public interface RegionalBlurState {
    void cobblemon$requestBlur(ScreenRectangle rectangle);
    List<ScreenRectangle> cobblemon$blurRegions();
}
