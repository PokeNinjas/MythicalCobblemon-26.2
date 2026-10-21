package com.cobblemon.mod.common.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draw the editor from the same unchanged artwork used by Cobblemon's sign blocks. */
public final class SignEditorBackground {
    private SignEditorBackground() {}

    public static boolean draw(GuiGraphicsExtractor graphics, String woodName, boolean wall) {
        String wood = switch (woodName) {
            case "cobblemon:apricorn" -> "apricorn";
            case "cobblemon:saccharine" -> "saccharine";
            default -> null;
        };
        if (wood == null) return false;
        var texture = Identifier.withDefaultNamespace("textures/entity/signs/" + wood + ".png");
        graphics.pose().translate(0F, 27F);
        graphics.pose().scale(3.9F, 3.9F);
        // Native editor uses a 24x12 board and, for standing signs, a 2x14 post.
        // The legacy 64x32 texture stores their front faces at (2,2) and (2,16).
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -12, -13, 2F, 2F, 24, 12, 64, 32);
        if (!wall) graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -1, -1, 2F, 16F, 2, 14, 64, 32);
        return true;
    }
}
