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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

// On the title screen, the normal text buttons (Singleplayer, Multiplayer, Options and so on) get the mod's bronze
// and gold look. Only plain Button objects are changed, so icon buttons and other mods' own buttons look as they did.
// Client only
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget {
    protected AbstractButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void morkeriget$themed(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        Object self = this;
        if (self.getClass() == Button.class && Minecraft.getInstance().screen instanceof TitleScreen) {
            UiTheme.button(graphics, (Button) self, alpha);
            info.cancel();
        }
    }
}
