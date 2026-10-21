/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render;

import net.minecraft.resources.Identifier;

/** Immutable texture choice captured before native deferred model submission. */
public record NamedVillagerTexture(Identifier original, Identifier replacement) {
    public interface State {
        NamedVillagerTexture cobblemon$getNamedVillagerTexture();
        void cobblemon$setNamedVillagerTexture(NamedVillagerTexture texture);
    }
}
