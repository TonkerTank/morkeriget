package com.morkeriget.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// The look of every screen in the mod, so they all match: dark panels with a bronze and gold frame like in Diablo,
// sunken slots, bars and titles. Everything is drawn with rectangles, so no textures are needed. Client only
public final class UiTheme {
    // Text colours that read well on the dark panels
    public static final int TITLE = 0xFFE8C46A;
    public static final int TEXT = 0xFFE6DCC8;
    public static final int MUTED = 0xFF9A8F7C;
    public static final int DIM = 0xFF6B6255;
    public static final int GOOD = 0xFF7CE07C;
    public static final int BAD = 0xFFE06A5A;

    private static final int SHADOW = 0xFF000000;
    private static final int BRONZE_DARK = 0xFF4A3818;
    private static final int BRONZE = 0xFF8C6E3A;
    private static final int GOLD = 0xFFD9B060;
    private static final int PANEL_TOP = 0xF2221B26;
    private static final int PANEL_BOTTOM = 0xF2120E15;

    private UiTheme() {
    }

    // A big panel: a black outline, a bronze frame lit from the top left, a dark background that fades down,
    // and a small gold mark in each corner
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x - 3, y - 3, right + 3, bottom + 3, SHADOW);
        graphics.fill(x - 2, y - 2, right + 2, bottom + 2, BRONZE_DARK);
        // Light on the top and left edge, shade on the bottom and right
        graphics.fill(x - 2, y - 2, right + 2, y - 1, BRONZE);
        graphics.fill(x - 2, y - 2, x - 1, bottom + 2, BRONZE);
        graphics.fill(x - 1, y - 1, right + 1, bottom + 1, SHADOW);
        graphics.fillGradient(x, y, right, bottom, PANEL_TOP, PANEL_BOTTOM);
        // A faint line just inside the frame, so the panel looks set in
        graphics.fill(x, y, right, y + 1, 0x20FFFFFF);
        corner(graphics, x - 2, y - 2);
        corner(graphics, right - 1, y - 2);
        corner(graphics, x - 2, bottom - 1);
        corner(graphics, right - 1, bottom - 1);
    }

    // A small gold diamond on a corner of a frame
    private static void corner(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y, x + 4, y + 3, SHADOW);
        graphics.fill(x, y - 1, x + 3, y + 4, SHADOW);
        graphics.fill(x, y, x + 3, y + 3, GOLD);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, 0xFFFFF0C0);
    }

    // A smaller box inside a panel, e.g. for one skill or one bounty. accent is the colour of the frame, e.g. the class
    // colour, or 0 for the normal bronze
    public static void box(GuiGraphics graphics, int x, int y, int width, int height, int accent, boolean lit) {
        int frame = accent != 0 ? 0xFF000000 | accent : BRONZE_DARK;
        graphics.fill(x, y, x + width, y + height, SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, lit ? frame : darker(frame));
        graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2, lit ? 0xFF2A2230 : 0xFF19151C, lit ? 0xFF17121A : 0xFF100D12);
    }

    // A sunken slot for an item, 18 by 18 around the 16 by 16 item at (x, y)
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0A080C);
        // Dark on the top and left inside, light on the bottom and right, so it looks pressed in
        graphics.fill(x, y, x + 16, y + 16, 0xFF2C2630);
        graphics.fill(x, y, x + 16, y + 1, 0xFF141016);
        graphics.fill(x, y, x + 1, y + 16, 0xFF141016);
        graphics.fill(x, y + 15, x + 16, y + 16, 0xFF48404C);
        graphics.fill(x + 15, y, x + 16, y + 16, 0xFF48404C);
    }

    // A bar that fills from the left, with a dark track, a lighter top line and an outline
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, SHADOW);
        graphics.fill(x, y, x + width, y + height, 0xFF1A1519);
        int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            graphics.fillGradient(x, y, x + filled, y + height, 0xFF000000 | lighter(color), 0xFF000000 | color);
            graphics.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
        }
    }

    // The screen's title in gold, with a line on each side that fades out
    public static void title(GuiGraphics graphics, Font font, Component text, int centerX, int y) {
        title(graphics, font, text, centerX, y, TITLE);
    }

    // The same with the text in another colour, e.g. the class colour. The lines stay gold
    public static void title(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        title(graphics, font, text, centerX, y, color, 40);
    }

    // With shorter lines, for titles that have buttons close by
    public static void title(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color, int lineLength) {
        int half = font.width(text) / 2;
        graphics.drawCenteredString(font, text, centerX, y, color);
        int lineY = y + font.lineHeight / 2 - 1;
        for (int side = -1; side <= 1; side += 2) {
            int start = centerX + side * (half + 6);
            for (int i = 0; i < lineLength; i++) {
                int alpha = 0xC0 - i * 0xC0 / lineLength;
                int px = start + side * i;
                graphics.fill(px, lineY, px + 1, lineY + 1, (alpha << 24) | (GOLD & 0xFFFFFF));
            }
        }
        // A small diamond at the inner end of each line
        graphics.fill(centerX - half - 5, lineY - 1, centerX - half - 3, lineY + 2, GOLD);
        graphics.fill(centerX + half + 3, lineY - 1, centerX + half + 5, lineY + 2, GOLD);
    }

    public static void title(GuiGraphics graphics, Font font, String text, int centerX, int y) {
        title(graphics, font, Component.literal(text), centerX, y);
    }

    public static void title(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        title(graphics, font, Component.literal(text), centerX, y, color);
    }

    // A thin gold line that fades out at both ends, to split a panel into parts
    public static void divider(GuiGraphics graphics, int x, int y, int width) {
        for (int i = 0; i < width; i++) {
            float edge = Math.min(i, width - 1 - i) / (width * 0.25f);
            int alpha = (int) (0x90 * Math.min(1, edge));
            graphics.fill(x + i, y, x + i + 1, y + 1, (alpha << 24) | (BRONZE & 0xFFFFFF));
        }
    }

    private static int darker(int color) {
        int r = (color >> 16 & 0xFF) / 2;
        int g = (color >> 8 & 0xFF) / 2;
        int b = (color & 0xFF) / 2;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int lighter(int color) {
        int r = Math.min(255, (color >> 16 & 0xFF) + 50);
        int g = Math.min(255, (color >> 8 & 0xFF) + 50);
        int b = Math.min(255, (color & 0xFF) + 50);
        return r << 16 | g << 8 | b;
    }
}
