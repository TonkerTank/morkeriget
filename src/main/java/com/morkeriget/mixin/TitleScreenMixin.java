package com.morkeriget.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.morkeriget.ui.TitleMenu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

// Gives Minecraft's title screen the mod's look, see TitleMenu: a dark background with embers over the panorama, and
// the Realm of Darkness logo instead of the Minecraft logo and the yellow splash text. Client only
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    // The title screen's own background method is empty, and is called after the panorama and before the buttons
    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void morkeriget$background(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        TitleMenu.background(graphics, width, height);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void morkeriget$logo(LogoRenderer logoRenderer, GuiGraphics graphics, int screenWidth, float alpha) {
        TitleMenu.logo(graphics, width, height, alpha);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"))
    private void morkeriget$noSplash(SplashRenderer splash, GuiGraphics graphics, int screenWidth, Font font, int color) {
    }
}
