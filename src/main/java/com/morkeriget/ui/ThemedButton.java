package com.morkeriget.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

// A button in the mod's own look: dark bronze with a frame that turns gold under the mouse, and grey when it can't
// be clicked. Made with Button.builder(...).build(ThemedButton::new), so everything else works like a normal button.
// Client only
public class ThemedButton extends Button {
    public ThemedButton(Builder builder) {
        super(builder);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiTheme.button(graphics, this, 1);
    }
}
