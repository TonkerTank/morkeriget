package com.morkeriget.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.morkeriget.Morkeriget;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// The mod's own look on Minecraft's title screen: the panorama is darkened with a red tint and a dark vignette,
// embers rise from the bottom, a red glow pulses behind the gold "Realm of Darkness" logo, which replaces the
// Minecraft logo. Drawn from TitleScreenMixin. Client only
public final class TitleMenu {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "textures/gui/title_logo.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "textures/gui/title_glow.png");
    private static final ResourceLocation VIGNETTE = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "textures/gui/title_vignette.png");
    private static final int LOGO_TEXTURE_WIDTH = 1024;
    private static final int LOGO_TEXTURE_HEIGHT = 288;
    private static final int SOFT_TEXTURE_SIZE = 256;
    private static final int EMBERS = 80;

    private TitleMenu() {
    }

    // Behind the buttons and the logo, on top of the panorama
    public static void background(GuiGraphics graphics, int width, int height) {
        float time = Util.getMillis() / 1000f;
        // A dark red tint over the panorama, darker at the bottom
        graphics.fillGradient(0, 0, width, height, 0xA0140608, 0xD8060204);
        // A red glow behind the logo that slowly pulses
        float pulse = 0.75f + 0.25f * Mth.sin(time * 1.3f);
        int glowWidth = Math.min(width, 420);
        int glowHeight = 170;
        int logoCenterY = logoTop(height) + logoHeight(height) / 2;
        soft(graphics, GLOW, width / 2 - glowWidth / 2, logoCenterY - glowHeight / 2, glowWidth, glowHeight, 0.55f * pulse);
        // A faint glow rising from the bottom, like a fire below
        graphics.fillGradient(0, height - 70, width, height, 0x00000000, (int) (0x50 * pulse) << 24 | 0x8A1E08);
        embers(graphics, width, height, time);
        soft(graphics, VIGNETTE, 0, 0, width, height, 1f);
    }

    // Small sparks that rise from the bottom of the screen, sway a little and fade out on the way up. Each one is
    // worked out from its number and the time, so nothing has to be kept between frames
    private static void embers(GuiGraphics graphics, int width, int height, float time) {
        for (int i = 0; i < EMBERS; i++) {
            float seedX = hash(i * 3 + 1);
            float seedSpeed = hash(i * 3 + 2);
            float seedPhase = hash(i * 3 + 3);
            float speed = 12 + seedSpeed * 28;
            float travel = height * 0.85f + 20;
            float risen = (time * speed + seedPhase * travel) % travel;
            float life = risen / travel;
            float x = seedX * width + Mth.sin(time * (0.6f + seedSpeed) + i) * (6 + 10 * life);
            float y = height + 4 - risen;
            // Bright while low, then fading out near the end of the way up
            float alpha = Math.min(1, life * 6) * (1 - life);
            float flicker = 0.7f + 0.3f * Mth.sin(time * 9 + i * 1.7f);
            int a = (int) (255 * alpha * flicker);
            if (a <= 4) {
                continue;
            }
            int size = seedSpeed > 0.8f ? 2 : 1;
            int color = seedPhase > 0.5f ? 0xFFB040 : 0xFF6A20;
            int px = (int) x;
            int py = (int) y;
            if (size == 2) {
                // A faint halo around the bigger sparks
                graphics.fill(px - 1, py - 1, px + 3, py + 3, (a / 4) << 24 | 0xFF4A10);
            }
            graphics.fill(px, py, px + size, py + size, a << 24 | color);
        }
    }

    // The gold logo where Minecraft's logo would be. alpha follows the title screen's own fade in
    public static void logo(GuiGraphics graphics, int width, int height, float alpha) {
        int logoHeight = logoHeight(height);
        int logoWidth = logoHeight * LOGO_TEXTURE_WIDTH / LOGO_TEXTURE_HEIGHT;
        if (logoWidth > width - 16) {
            logoWidth = width - 16;
            logoHeight = logoWidth * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH;
        }
        int top = logoTop(height) + (logoHeight(height) - logoHeight) / 2;
        smooth(LOGO);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, alpha);
        graphics.blit(LOGO, width / 2 - logoWidth / 2, top, logoWidth, logoHeight, 0, 0,
                LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // The buttons start at a quarter of the height plus 48, so the logo sits just above them, and gets smaller on
    // short windows
    private static int logoHeight(int height) {
        return Mth.clamp(height / 4 + 48 - 22, 30, 72);
    }

    private static int logoTop(int height) {
        return Math.max(4, height / 4 + 48 - 10 - logoHeight(height));
    }

    // A soft texture stretched over an area, faded by alpha
    private static void soft(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, float alpha) {
        smooth(texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, alpha);
        graphics.blit(texture, x, y, width, height, 0, 0, SOFT_TEXTURE_SIZE, SOFT_TEXTURE_SIZE, SOFT_TEXTURE_SIZE, SOFT_TEXTURE_SIZE);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // Smooth scaling instead of Minecraft's sharp pixels, since these textures are drawn bigger or smaller than they are
    private static void smooth(ResourceLocation texture) {
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
    }

    // A fixed random number from 0 to 1 for each seed
    private static float hash(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
