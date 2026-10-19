package com.morkeriget.skill;

import java.util.List;
import java.util.Optional;

import com.morkeriget.Morkeriget;
import com.morkeriget.progress.ClientProgress;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Upgrade;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// A row of skill icons to the right of the mana bar, with the key, the cooldown counting down, and a blue tint
// when there isn't enough mana. Dodge is first, then the skills in order. The potions are on the other side of the hotbar.
// Client only
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

    // Also used by the skill screen
    public static ItemStack icon(Skill skill) {
        return switch (skill) {
            case WHIRLWIND -> new ItemStack(Items.IRON_SWORD);
            case FROST_NOVA -> new ItemStack(Items.BLUE_ICE);
            case BLINK -> new ItemStack(Items.ENDER_PEARL);
            case CHAIN_LIGHTNING -> new ItemStack(Items.LIGHTNING_ROD);
            case THUNDERSTORM -> new ItemStack(Items.TRIDENT);
            case BATTLE_CRY -> new ItemStack(Items.GOAT_HORN);
            case LEAP -> new ItemStack(Items.RABBIT_FOOT);
            case FRENZY -> new ItemStack(Items.REDSTONE);
            case FIREBALL -> new ItemStack(Items.FIRE_CHARGE);
            case MULTISHOT -> new ItemStack(Items.CROSSBOW);
            case ARROW_RAIN -> new ItemStack(Items.ARROW);
            case SMOKE_BOMB -> new ItemStack(Items.GUNPOWDER);
            case WOLVES -> new ItemStack(Items.WOLF_ARMOR);
            case BONE_SPEAR -> new ItemStack(Items.BONE);
            case RAISE_SKELETONS -> new ItemStack(Items.WITHER_SKELETON_SKULL);
            case BONE_ARMOR -> new ItemStack(Items.BONE_BLOCK);
            case CURSE -> new ItemStack(Items.FERMENTED_SPIDER_EYE);
            case EARTHQUAKE -> new ItemStack(Items.CRACKED_STONE_BRICKS);
            case RUSH -> new ItemStack(Items.SADDLE);
            case METEOR -> new ItemStack(Items.MAGMA_BLOCK);
            case ICE_ARMOR -> new ItemStack(Items.PACKED_ICE);
            case EXPLOSIVE_ARROW -> new ItemStack(Items.TNT);
            case BEAR_TRAP -> new ItemStack(Items.TRIPWIRE_HOOK);
            case BLOOD_NOVA -> new ItemStack(Items.REDSTONE_BLOCK);
            case BONE_PRISON -> new ItemStack(Items.IRON_BARS);
            case HEALTH_POTION -> potion(0xE02020);
            case MANA_POTION -> potion(0x3060FF);
        };
    }

    // A potion bottle in red or blue
    private static ItemStack potion(int color) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(color), List.of()));
        return stack;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        // The skills go just right of the mana bar, centered on the hotbar's height. The potions go on the left of the
        // hotbar, past the off hand slot, so the bar doesn't run off small screens
        int x = graphics.guiWidth() / 2 + 91 + 4 + 6 + 6;
        int potionX = graphics.guiWidth() / 2 - 91 - 29 - 4 - SLOT;
        int y = graphics.guiHeight() - 19;

        slot(graphics, minecraft.font, x, y, DODGE_ICON, SkillKeys.DODGE, SkillKeys.dodgeCooldown(), SkillKeys.DODGE_COOLDOWN, false);
        // Before a class is picked there are no skills, only dodge and the potions. Keys without a skill are empty
        PlayerClass playerClass = ClientProgress.get().chosenClass();
        if (playerClass != null) {
            for (int slot = 0; slot < SkillKeys.SKILL_SLOTS.size(); slot++) {
                x += SLOT;
                Skill skill = ClientProgress.get().slot(slot);
                if (skill != null) {
                    skillSlot(graphics, minecraft.font, x, y, skill, SkillKeys.SKILL_SLOTS.get(slot));
                } else {
                    slot(graphics, minecraft.font, x, y, ItemStack.EMPTY, SkillKeys.SKILL_SLOTS.get(slot), 0, 0, false);
                }
            }
        }
        skillSlot(graphics, minecraft.font, potionX, y, Skill.HEALTH_POTION, SkillKeys.HEALTH_POTION);
        skillSlot(graphics, minecraft.font, potionX - SLOT, y, Skill.MANA_POTION, SkillKeys.MANA_POTION);
    }

    private static void skillSlot(GuiGraphics graphics, Font font, int x, int y, Skill skill, KeyMapping key) {
        int i = skill.ordinal();
        slot(graphics, font, x, y, icon(skill), key, remaining[i], total[i], ManaBar.mana() < manaCost(skill));
    }

    // The same as Skills.use works out, with Arcane Flow
    private static float manaCost(Skill skill) {
        return skill.manaCost() * (1 - 0.05f * ClientProgress.get().rank(Upgrade.ARCANE_FLOW));
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
