package com.morkeriget.skill;

import com.morkeriget.Morkeriget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// A row of skill icons to the right of the mana bar, with the key, the cooldown counting down, and a blue tint
// when there isn't enough mana. Dodge is first, then the skills in order. Client only
public final class SkillBar {
    // One slot is a 16x16 icon with a 1 pixel border that is shared with the next slot
    private static final int SLOT = 17;
    private static final ItemStack DODGE_ICON = new ItemStack(Items.FEATHER);

    // Ticks left of each skill's cooldown and how long it was in total, indexed by Skill.ordinal()
    private static final int[] remaining = new int[Skill.values().length];
    private static final int[] total = new int[Skill.values().length];

    private SkillBar() {
    }

    public static void set(CooldownPayload payload) {
        remaining[payload.skill().ordinal()] = payload.ticks();
        total[payload.skill().ordinal()] = payload.ticks();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "skill_bar"), SkillBar::render);
    }

    // The server only says when a cooldown starts, so the client counts it down itself
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        for (int i = 0; i < remaining.length; i++) {
            if (remaining[i] > 0) {
                remaining[i]--;
            }
        }
    }

    private static ItemStack icon(Skill skill) {
        return new ItemStack(switch (skill) {
            case WHIRLWIND -> Items.IRON_SWORD;
            case FROST_NOVA -> Items.BLUE_ICE;
            case BLINK -> Items.ENDER_PEARL;
            case CHAIN_LIGHTNING -> Items.LIGHTNING_ROD;
            case BATTLE_CRY -> Items.GOAT_HORN;
        });
    }

    private static KeyMapping key(Skill skill) {
        return switch (skill) {
            case WHIRLWIND -> SkillKeys.WHIRLWIND;
            case FROST_NOVA -> SkillKeys.FROST_NOVA;
            case BLINK -> SkillKeys.BLINK;
            case CHAIN_LIGHTNING -> SkillKeys.CHAIN_LIGHTNING;
            case BATTLE_CRY -> SkillKeys.BATTLE_CRY;
        };
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        // Just right of the mana bar, centered on the hotbar's height
        int x = graphics.guiWidth() / 2 + 91 + 4 + 6 + 6;
        int y = graphics.guiHeight() - 19;

        slot(graphics, minecraft.font, x, y, DODGE_ICON, SkillKeys.DODGE, SkillKeys.dodgeCooldown(), SkillKeys.DODGE_COOLDOWN, false);
        for (Skill skill : Skill.values()) {
            x += SLOT;
            int i = skill.ordinal();
            slot(graphics, minecraft.font, x, y, icon(skill), key(skill), remaining[i], total[i], ManaBar.mana() < skill.manaCost());
        }
    }

    private static void slot(GuiGraphics graphics, Font font, int x, int y, ItemStack icon, KeyMapping key,
                             int cooldown, int cooldownTotal, boolean noMana) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF000000);
        graphics.fill(x, y, x + 16, y + 16, 0xFF2A2A2A);
        graphics.renderItem(icon, x, y);

        // Items are drawn in front of everything else, so the overlays and text are moved further forward
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        if (noMana) {
            graphics.fill(x, y, x + 16, y + 16, 0x903050FF);
        }
        if (cooldown > 0 && cooldownTotal > 0) {
            // A dark shade that shrinks from the top as the cooldown runs out, like the vanilla item cooldown
            int shade = Math.round(16f * cooldown / cooldownTotal);
            graphics.fill(x, y + 16 - shade, x + 16, y + 16, 0xB0000000);
            String seconds = String.valueOf((cooldown + 19) / 20);
            graphics.drawString(font, seconds, x + 8 - font.width(seconds) / 2, y + 4, 0xFFFFFF);
        }
        if (!key.isUnbound()) {
            String name = font.plainSubstrByWidth(key.getTranslatedKeyMessage().getString(), 16);
            graphics.drawString(font, name, x + 17 - font.width(name), y + 9, 0xFFD700);
        }
        graphics.pose().popPose();
    }
}
