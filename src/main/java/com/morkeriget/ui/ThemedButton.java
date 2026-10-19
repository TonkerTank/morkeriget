package com.morkeriget.ui;

import net.minecraft.client.Minecraft;
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
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();
        boolean hover = active && isHoveredOrFocused();
        int frame = !active ? 0xFF3A342E : hover ? 0xFFE8C46A : 0xFF8C6E3A;
        int top = !active ? 0xFF262224 : hover ? 0xFF5A4428 : 0xFF3A2D1E;
        int low = !active ? 0xFF1A1719 : hover ? 0xFF33250F : 0xFF21180F;
        graphics.fill(x, y, right, bottom, 0xFF000000);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, frame);
        graphics.fillGradient(x + 2, y + 2, right - 2, bottom - 2, top, low);
        // Light along the top inside edge
        graphics.fill(x + 2, y + 2, right - 2, y + 3, active ? 0x30FFFFFF : 0x10FFFFFF);
        int color = !active ? 0xFF7A7064 : hover ? 0xFFFFF0C0 : 0xFFE6D3A3;
        renderString(graphics, Minecraft.getInstance().font, color);
    }
}
