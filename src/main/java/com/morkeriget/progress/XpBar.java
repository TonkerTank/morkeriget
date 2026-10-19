package com.morkeriget.progress;

import com.morkeriget.Morkeriget;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// Takes the place of the vanilla XP bar and level number, and shows the mod's own XP instead: purple towards the next
// level, cyan towards the next paragon level. It fills up smoothly, "+25 XP" pops up when you get XP, and it flashes
// gold on a level up. The level itself is over the mana bar, see ManaBar. Client only
public final class XpBar {
    private static final int WIDTH = 182;
    private static final int HEIGHT = 5;
    // How long "+XP" stays, and the flash after a level up, in milliseconds
    private static final long GAIN_TIME = 2000;
    private static final long FLASH_TIME = 800;

    // The fill drawn now, moving towards the real one
    private static float shown;
    // XP added up since the text popped up, so many kills in a row show one growing number
    private static int gained;
    private static long gainedAt;
    private static long levelUpAt;
    private static boolean synced;

    private XpBar() {
    }

    // Leaving a world, so the next one starts fresh
    public static void reset() {
        synced = false;
        shown = 0;
        gained = 0;
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_BAR, XpBar::render);
        // The vanilla level number would be the vanilla level, which the mod doesn't use
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, (graphics, deltaTracker) -> {
        });
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "xp_gain"), XpBar::renderGain);
    }

    // Called by ClientProgress when the server sends new progress, to see how much XP came in. The first one after
    // joining a world isn't XP coming in, it is just what you had, so it is skipped
    static void update(ProgressPayload before, ProgressPayload after) {
        if (!synced) {
            synced = true;
            return;
        }
        boolean paragonBefore = before.level() >= Progression.MAX_LEVEL;
        boolean levelledUp = after.level() > before.level() || after.paragonLevel() > before.paragonLevel();
        int gain;
        if (levelledUp) {
            // What was left of the old level, plus what is already in the new one. Several levels at once are rare
            gain = Math.max(0, before.xpNeeded() - before.xp()) + after.xp();
            levelUpAt = Util.getMillis();
            // The bar starts empty again
            shown = 0;
        } else if (paragonBefore == (after.level() >= Progression.MAX_LEVEL)) {
            gain = after.xp() - before.xp();
        } else {
            gain = 0;
        }
        if (gain <= 0) {
            return;
        }
        long now = Util.getMillis();
        gained = now - gainedAt < GAIN_TIME ? gained + gain : gain;
        gainedAt = now;
    }

    private static boolean hidden(Minecraft minecraft) {
        // A horse's jump bar takes the place of the XP bar, like in vanilla
        return minecraft.options.hideGui || minecraft.player == null || minecraft.gameMode == null || !minecraft.gameMode.hasExperience()
                || minecraft.player.jumpableVehicle() != null;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (hidden(minecraft)) {
            return;
        }
        ProgressPayload progress = ClientProgress.get();
        boolean paragon = progress.level() >= Progression.MAX_LEVEL;
        float target = Math.min(1, (float) progress.xp() / Math.max(1, progress.xpNeeded()));
        // Glides a bit of the way each frame, so XP pours in instead of jumping
        shown += (target - shown) * Math.min(1, 0.15f * deltaTracker.getGameTimeDeltaTicks() * 4);
        if (Math.abs(target - shown) < 0.001f) {
            shown = target;
        }

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() - 29;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xFF000000);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, paragon ? 0xFF10282A : 0xFF1E1230);
        int filled = Math.round(WIDTH * shown);
        int color = paragon ? 0xFF33CCCC : 0xFF9B4DFF;
        int light = paragon ? 0xFF8CF5F5 : 0xFFC9A0FF;
        long sinceLevelUp = Util.getMillis() - levelUpAt;
        if (sinceLevelUp < FLASH_TIME) {
            // A gold flash over the whole bar that fades out
            int alpha = (int) (255 * (1 - (float) sinceLevelUp / FLASH_TIME));
            graphics.fill(x, y, x + WIDTH, y + HEIGHT, (alpha << 24) | 0xFFD700);
        }
        graphics.fill(x, y, x + filled, y + HEIGHT, color);
        // A lighter line along the top makes it look less flat
        graphics.fill(x, y, x + filled, y + 1, light);
        // Notches every tenth, like the vanilla bar
        for (int i = 1; i < 10; i++) {
            int notch = x + WIDTH * i / 10;
            graphics.fill(notch, y + 1, notch + 1, y + HEIGHT, 0x60000000);
        }
    }

    // "+25 XP" over the right end of the bar, fading out
    private static void renderGain(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        long since = Util.getMillis() - gainedAt;
        if (hidden(minecraft) || gained <= 0 || since > GAIN_TIME) {
            return;
        }
        Font font = minecraft.font;
        String text = "+" + gained + " XP";
        boolean paragon = ClientProgress.get().level() >= Progression.MAX_LEVEL;
        // Fully there for the first half, then fades. Text with almost no alpha is drawn fully, so stop before that
        float fade = since < GAIN_TIME / 2 ? 1 : 1 - (float) (since - GAIN_TIME / 2) / (GAIN_TIME / 2);
        int alpha = Math.max(8, (int) (255 * fade));
        int rise = (int) (since / 200);
        int x = graphics.guiWidth() / 2 + WIDTH / 2 - font.width(text);
        int y = graphics.guiHeight() - 29 - 10 - rise;
        graphics.drawString(font, text, x, y, (alpha << 24) | (paragon ? 0x8CF5F5 : 0xC9A0FF));
    }
}
