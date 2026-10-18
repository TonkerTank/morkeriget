package com.morkeriget.combat;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.progress.ClientProgress;
import com.morkeriget.progress.Progression;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// The bar at the top of the screen with the name, level and health of the monster you look at or just hit, like in Diablo.
// Client only
public final class TargetFrame {
    private static final int WIDTH = 140;
    // How long the frame stays on the last monster you hit after you look away (20 ticks is one second)
    private static final int HIT_MEMORY = 5 * 20;

    private static int lastHitId = -1;
    private static long lastHitTime;
    // Where the boss bars end this frame, so the target frame goes below them
    private static int bossBarsBottom;

    private TargetFrame() {
    }

    static void onHit(Entity entity) {
        lastHitId = entity.getId();
        lastHitTime = entity.level().getGameTime();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "target_frame"), TargetFrame::render);
    }

    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        bossBarsBottom = 0;
    }

    // Called for every boss bar that is drawn. The next bar would go at y + increment
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBarsBottom = Math.max(bossBarsBottom, event.getY() + event.getIncrement() - 12);
    }

    private static LivingEntity target(Minecraft minecraft) {
        if (minecraft.crosshairPickEntity instanceof LivingEntity looked && looked instanceof Enemy && looked.isAlive()) {
            return looked;
        }
        if (minecraft.level != null && minecraft.level.getGameTime() - lastHitTime < HIT_MEMORY
                && minecraft.level.getEntity(lastHitId) instanceof LivingEntity hit && hit.isAlive()) {
            return hit;
        }
        return null;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        LivingEntity target = target(minecraft);
        if (target == null) {
            return;
        }
        Font font = minecraft.font;
        int left = graphics.guiWidth() / 2 - WIDTH / 2;
        int top = bossBarsBottom + 4;

        Component name = target.getDisplayName();
        int level = MonsterLevels.get(target);
        if (level > 0) {
            // Red if it is above your level, grey if it is below. Paragon counts, like in Progression.monsterLevel
            int playerLevel = ClientProgress.get().level() + ClientProgress.get().paragonLevel() / Progression.PARAGON_PER_MONSTER_LEVEL;
            int levelColor = level > playerLevel ? 0xFF5555 : level < playerLevel ? 0xAAAAAA : 0xFFFFFF;
            String levelText = "Level " + level;
            int nameWidth = font.width(name);
            int totalWidth = nameWidth + 6 + font.width(levelText);
            int x = graphics.guiWidth() / 2 - totalWidth / 2;
            graphics.drawString(font, name, x, top, 0xFFFFFF);
            graphics.drawString(font, levelText, x + nameWidth + 6, top, levelColor);
        } else {
            graphics.drawCenteredString(font, name, graphics.guiWidth() / 2, top, 0xFFFFFF);
        }

        int barTop = top + 11;
        float fraction = Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth()));
        graphics.fill(left - 1, barTop - 1, left + WIDTH + 1, barTop + 11, 0xFF000000);
        graphics.fill(left, barTop, left + WIDTH, barTop + 10, 0xFF3A0E0E);
        graphics.fill(left, barTop, left + Math.round(WIDTH * fraction), barTop + 10, 0xFFC0262D);
        String health = Math.round(target.getHealth()) + " / " + Math.round(target.getMaxHealth());
        graphics.drawCenteredString(font, health, graphics.guiWidth() / 2, barTop + 1, 0xFFFFFF);
    }
}
