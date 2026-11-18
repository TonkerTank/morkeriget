package com.morkeriget.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.morkeriget.ui.PauseMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;

// Gives the pause menu the mod's look, see PauseMenu. Drawn after Minecraft's blurred background, before the buttons.
// Client only
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void morkeriget$background(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        PauseMenu.background((PauseScreen) (Object) this, graphics);
    }
}
