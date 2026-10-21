/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.api.gui;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;

/** Keeps pixels outside tooltip regions unchanged around Minecraft's deferred blur pass. */
public final class RegionalGuiBlur implements AutoCloseable {
    private GpuTexture backup;
    private GpuTextureView backupView;
    private int idleFrames;

    public void render(GpuDevice device, GpuTexture target, int guiScale,
                       List<ScreenRectangle> regions, Runnable blur) {
        if (regions.isEmpty()) {
            blur.run();
            return;
        }
        int width = target.getWidth(0), height = target.getHeight(0);
        List<ScreenRectangle> outside = outsideRegions(width, height, guiScale, regions);
        if (backup == null || backup.isClosed() || backup.getWidth(0) != width ||
                backup.getHeight(0) != height || backup.getFormat() != target.getFormat()) {
            close();
            backup = device.createTexture("Cobblemon regional GUI blur backup",
                GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                target.getFormat(), width, height, 1, 1);
            backupView = device.createTextureView(backup);
        }
        idleFrames = 0;
        CommandEncoder encoder = device.createCommandEncoder();
        encoder.copyTextureToTexture(target, backup, 0, 0, 0, 0, 0, width, height);
        blur.run();
        // The 26.2 OpenGL offset-copy path passes extents as blit endpoints.
        // Keep the origin-aligned snapshot, then restore through a scissored,
        // unblended full-size blit so pixels outside the tooltip stay unchanged.
        try (GpuTextureView targetView = device.createTextureView(target);
             RenderPass pass = encoder.createRenderPass(() -> "Cobblemon tooltip blur restore", targetView, Optional.empty())) {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", backupView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            for (ScreenRectangle rectangle : outside) {
                pass.enableScissor(rectangle.left(), height - rectangle.bottom(), rectangle.width(), rectangle.height());
                pass.draw(3, 1, 0, 0);
            }
        }
    }

    /** Non-overlapping complement of the clipped union; coordinates here are top-left pixels. */
    public static List<ScreenRectangle> outsideRegions(int width, int height, int guiScale,
                                                       List<ScreenRectangle> regions) {
        if (width <= 0 || height <= 0 || guiScale <= 0) throw new IllegalArgumentException("Invalid GUI dimensions");
        List<ScreenRectangle> remaining = new ArrayList<>();
        remaining.add(new ScreenRectangle(0, 0, width, height));
        for (ScreenRectangle region : regions) {
            int left = (int) Math.clamp((long) region.left() * guiScale, 0L, width);
            int right = (int) Math.clamp((long) region.right() * guiScale, 0L, width);
            int top = (int) Math.clamp((long) region.top() * guiScale, 0L, height);
            int bottom = (int) Math.clamp((long) region.bottom() * guiScale, 0L, height);
            if (left >= right || top >= bottom) continue;
            List<ScreenRectangle> next = new ArrayList<>();
            for (ScreenRectangle rectangle : remaining) {
                int x0 = Math.max(left, rectangle.left()), y0 = Math.max(top, rectangle.top());
                int x1 = Math.min(right, rectangle.right()), y1 = Math.min(bottom, rectangle.bottom());
                if (x0 >= x1 || y0 >= y1) { next.add(rectangle); continue; }
                add(next, rectangle.left(), rectangle.top(), rectangle.right(), y0);
                add(next, rectangle.left(), y1, rectangle.right(), rectangle.bottom());
                add(next, rectangle.left(), y0, x0, y1);
                add(next, x1, y0, rectangle.right(), y1);
            }
            remaining = next;
        }
        return remaining;
    }

    private static void add(List<ScreenRectangle> rectangles, int x0, int y0, int x1, int y1) {
        if (x0 < x1 && y0 < y1) rectangles.add(new ScreenRectangle(x0, y0, x1 - x0, y1 - y0));
    }

    public void endFrame() { if (backup != null && ++idleFrames > 120) close(); }

    @Override
    public void close() {
        if (backupView != null) { backupView.close(); backupView = null; }
        if (backup != null) { backup.close(); backup = null; }
        idleFrames = 0;
    }
}
