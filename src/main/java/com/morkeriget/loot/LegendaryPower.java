package com.morkeriget.loot;

import java.util.Arrays;
import java.util.List;

import com.morkeriget.combat.PlayerHits;
import com.morkeriget.progress.PlayerClass;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Every legendary item has one power that changes how a skill works or adds something new, like in Diablo.
// The power is saved on the item and checked where it is used with has(). The ones without a class are for everyone
public enum LegendaryPower {
    MAELSTROM("Malstrøm", PlayerClass.BARBARIAN, "Hvirvelvind trækker fjender ind og gør 30% mere skade"),
    EARTHBREAKER("Jordbrud", PlayerClass.BARBARIAN, "Spring lammer fjenderne, hvor du lander, i 2 sek."),
    STORM_EYE("Stormens Øje", PlayerClass.SORCERER, "Kædelyn springer 3 gange mere"),
    FROST_TRAIL("Frostspor", PlayerClass.SORCERER, "Blink laver en frostnova, hvor du starter og lander"),
    ARROW_STORM("Pilestorm", PlayerClass.RANGER, "Multishot skyder 9 pile i stedet for 5"),
    ENDLESS_RAIN("Uendelig Regn", PlayerClass.RANGER, "Pileregn har 6 bølger i stedet for 3"),
    CORPSE_EXPLOSION("Ligeksplosion", PlayerClass.NECROMANCER, "Dine skeletter eksploderer, når de dør"),
    CONTAGIOUS_CURSE("Smittende Forbandelse", PlayerClass.NECROMANCER, "Forbandelsen spreder sig, når en forbandet fjende dør"),
    AFTERSHOCK("Efterskælv", PlayerClass.BARBARIAN, "Jordskælv varer dobbelt så længe"),
    METEOR_SHOWER("Meteorregn", PlayerClass.SORCERER, "Meteor kalder 2 ekstra meteorer ned omkring målet"),
    CLUSTER_BOMB("Klyngebombe", PlayerClass.RANGER, "Eksplosiv pil sender 3 mindre eksplosioner ud"),
    DEATHS_CAGE("Dødens Bur", PlayerClass.NECROMANCER, "Knoglefængsel forbander også dem, den fanger"),
    CHEAT_DEATH("Snyd Døden", null, "Under 20% liv bliver du usårlig i 2 sek. (hvert 60. sek.)"),
    SOUL_EATER("Sjælesluger", null, "Hvert dræb giver 5 mana"),
    THUNDERSTRIKE("Tordenslag", null, "Knusende slag kalder et lyn ned, der gør 50% mere skade"),
    BLOODLUST("Blodtørst", null, "+3% skade for hvert dræb inden for 5 sek. (højst +30%)");

    private static final String POWER_TAG = "morkeriget_legendary_power";
    // How often a legendary gets a power for the class of the one who found it, or one for everyone
    private static final double OWN_CLASS_CHANCE = 0.7;

    public final String displayName;
    // null for the powers everyone can use
    public final PlayerClass playerClass;
    public final String description;

    LegendaryPower(String displayName, PlayerClass playerClass, String description) {
        this.displayName = displayName;
        this.playerClass = playerClass;
        this.description = description;
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
