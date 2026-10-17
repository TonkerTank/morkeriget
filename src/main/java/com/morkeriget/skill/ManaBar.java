package com.morkeriget.skill;

import com.morkeriget.Morkeriget;
import com.morkeriget.progress.ClientProgress;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// Draws the mana bar as a blue column to the right of the hotbar. Client only
public final class ManaBar {
    private static final int WIDTH = 6;
    private static final int HEIGHT = 22;
    private static float mana = Mana.MAX;

    private ManaBar() {
    }

    public static void set(ManaPayload payload) {
        mana = payload.mana();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "mana_bar"), ManaBar::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 91 + 4;
        int bottom = graphics.guiHeight() - 1;
        int top = bottom - HEIGHT;
        int filled = Math.round(HEIGHT * Math.max(0, mana) / Mana.MAX);
        graphics.fill(x - 1, top - 1, x + WIDTH + 1, bottom + 1, 0xFF000000);
        graphics.fill(x, top, x + WIDTH, bottom, 0xFF1A1A33);
        graphics.fill(x, bottom - filled, x + WIDTH, bottom, 0xFF3366FF);
        // A lighter stripe on the left edge makes it look a little less flat
        graphics.fill(x, bottom - filled, x + 1, bottom, 0xFF6F93FF);

        // The player's level just above the bar
        String level = String.valueOf(ClientProgress.get().level());
        graphics.drawString(minecraft.font, level, x + WIDTH / 2 - minecraft.font.width(level) / 2, top - 10, 0xFFB080FF);
    }
}
