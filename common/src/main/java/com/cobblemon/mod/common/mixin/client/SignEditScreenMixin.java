package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.SignEditorBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.world.level.block.PlainSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignEditScreen.class)
public abstract class SignEditScreenMixin extends AbstractSignEditScreen {
    protected SignEditScreenMixin(SignBlockEntity sign, boolean front, boolean filtered) {
        super(sign, front, filtered);
    }

    @Inject(method = "extractSignBackground", at = @At("HEAD"), cancellable = true)
    private void cobblemon$drawSignEditor(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        boolean wall = PlainSignBlock.getAttachmentPoint(sign.getBlockState()) == PlainSignBlock.Attachment.WALL;
        if (SignEditorBackground.draw(graphics, woodType.name(), wall)) ci.cancel();
    }
}
