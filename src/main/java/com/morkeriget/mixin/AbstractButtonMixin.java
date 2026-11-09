package com.morkeriget.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.morkeriget.ui.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ModsButton;

// On the title screen, the normal text buttons (Singleplayer, Multiplayer, Options and so on) get the mod's bronze
// and gold look, and so do NeoForge's Mods button and the icon buttons for language and accessibility. Only these are
// changed, so other mods' own buttons look as they did.
// Client only
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget {
    protected AbstractButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void morkeriget$themed(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        Object self = this;
        Class<?> type = self.getClass();
        if ((type == Button.class || type == ModsButton.class || type == SpriteIconButton.CenteredIcon.class) && Minecraft.getInstance().screen instanceof TitleScreen) {
            UiTheme.button(graphics, (Button) self, alpha);
            info.cancel();
        }
    }
}
