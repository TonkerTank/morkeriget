package com.morkeriget.skill;

import com.morkeriget.Morkeriget;
import com.morkeriget.progress.ClientProgress;
import com.morkeriget.progress.ProgressPayload;

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

    static float mana() {
        return mana;
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
        // A black outline, a bronze frame like the skill slots, and a dark track inside
        graphics.fill(x - 2, top - 2, x + WIDTH + 2, bottom + 2, 0xFF000000);
        graphics.fill(x - 1, top - 1, x + WIDTH + 1, bottom + 1, 0xFF8C6E3A);
        graphics.fill(x, top, x + WIDTH, bottom, 0xFF12122A);
        if (filled > 0) {
            graphics.fillGradient(x, bottom - filled, x + WIDTH, bottom, 0xFF5A86FF, 0xFF2348C8);
            // A lighter stripe on the left edge and a bright line on the top make it look a little less flat
            graphics.fill(x, bottom - filled, x + 1, bottom, 0x80A8C0FF);
            graphics.fill(x, bottom - filled, x + WIDTH, bottom - filled + 1, 0xFFA8C0FF);
        }

        // The player's level just above the bar. Like in Diablo 4 the paragon level takes its place once you have one
        ProgressPayload progress = ClientProgress.get();
        boolean paragon = progress.paragonLevel() > 0;
        String level = String.valueOf(paragon ? progress.paragonLevel() : progress.level());
        graphics.drawString(minecraft.font, level, x + WIDTH / 2 - minecraft.font.width(level) / 2, top - 11,
                paragon ? 0xFF55FFFF : 0xFFB080FF);
    }
}
