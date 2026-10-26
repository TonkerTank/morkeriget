package com.morkeriget.loot;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.morkeriget.Text;
import com.morkeriget.combat.PlayerHits;
import com.morkeriget.progress.PlayerClass;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Every legendary item has one power that changes how a skill works or adds something new, like in Diablo.
// The power is saved on the item and checked where it is used with has(). The ones without a class are for everyone
public enum LegendaryPower {
    // Names and descriptions are in the language files, under power.<name>
    MAELSTROM(PlayerClass.BARBARIAN),
    EARTHBREAKER(PlayerClass.BARBARIAN),
    STORM_EYE(PlayerClass.SORCERER),
    FROST_TRAIL(PlayerClass.SORCERER),
    ARROW_STORM(PlayerClass.RANGER),
    ENDLESS_RAIN(PlayerClass.RANGER),
    CORPSE_EXPLOSION(PlayerClass.NECROMANCER),
    CONTAGIOUS_CURSE(PlayerClass.NECROMANCER),
    AFTERSHOCK(PlayerClass.BARBARIAN),
    METEOR_SHOWER(PlayerClass.SORCERER),
    CLUSTER_BOMB(PlayerClass.RANGER),
    DEATHS_CAGE(PlayerClass.NECROMANCER),
    CHEAT_DEATH(null),
    SOUL_EATER(null),
    THUNDERSTRIKE(null),
    BLOODLUST(null);

    private static final String POWER_TAG = "morkeriget_legendary_power";
    // How often a legendary gets a power for the class of the one who found it, or one for everyone
    private static final double OWN_CLASS_CHANCE = 0.7;

    // null for the powers everyone can use
    public final PlayerClass playerClass;

    LegendaryPower(PlayerClass playerClass) {
        this.playerClass = playerClass;
    }

    public MutableComponent displayName() {
        return Text.tr("power." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent description() {
        return Text.tr("power." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    // Mostly a power the finder can use. forClass is null when nobody in particular found it
    public static LegendaryPower roll(RandomSource random, PlayerClass forClass) {
        List<LegendaryPower> possible = Arrays.asList(values());
        if (forClass != null && random.nextDouble() < OWN_CLASS_CHANCE) {
            possible = possible.stream().filter(power -> power.playerClass == null || power.playerClass == forClass).toList();
        }
        return possible.get(random.nextInt(possible.size()));
    }

    // The power on the item, or null
    public static LegendaryPower of(ItemStack stack) {
        String name = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(POWER_TAG);
        for (LegendaryPower power : values()) {
            if (power.name().equals(name)) {
                return power;
            }
        }
        return null;
    }

    // Whether the player wears the power or has it on the weapon in their hand
    public static boolean has(ServerPlayer player, LegendaryPower power) {
        for (ItemStack stack : PlayerHits.equippedItems(player)) {
            if (of(stack) == power) {
                return true;
            }
        }
        return false;
    }

    public void markOn(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(POWER_TAG, name()));
    }
}
