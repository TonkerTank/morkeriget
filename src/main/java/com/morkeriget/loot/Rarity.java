package com.morkeriget.loot;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

// How rare a piece of loot is. Rarer loot gets more bonuses, just like in Diablo
public enum Rarity {
    COMMON("Almindelig", ChatFormatting.WHITE, 0, 50, 0xFFFFFF),
    MAGIC("Magisk", ChatFormatting.BLUE, 1, 30, 0x5577FF),
    RARE("Sjælden", ChatFormatting.YELLOW, 2, 15, 0xFFFF33),
    LEGENDARY("Legendarisk", ChatFormatting.GOLD, 4, 5, 0xFF8800),
    // Set items are never rolled at random (weight 0). They drop on purpose, see Loot.createSet
    SET("Sæt", ChatFormatting.GREEN, 3, 0, 0x33FF55);

    public final String displayName;
    public final ChatFormatting color;
    public final int affixCount;
    private final int weight;
    // Colour of the light beam over the item when it lies on the ground
    public final int beamColor;

    Rarity(String displayName, ChatFormatting color, int affixCount, int weight, int beamColor) {
        this.displayName = displayName;
        this.color = color;
        this.affixCount = affixCount;
        this.weight = weight;
        this.beamColor = beamColor;
    }

    // Picks a random rarity that is at least as good as minimum
    public static Rarity roll(RandomSource random, Rarity minimum) {
        int total = 0;
        for (Rarity rarity : values()) {
            if (rarity.ordinal() >= minimum.ordinal()) {
                total += rarity.weight;
            }
        }
        int pick = random.nextInt(total);
        for (Rarity rarity : values()) {
            if (rarity.ordinal() >= minimum.ordinal()) {
                pick -= rarity.weight;
                if (pick < 0) {
                    return rarity;
                }
            }
        }
        return minimum;
    }

    public static Rarity byName(String name) {
        for (Rarity rarity : values()) {
            if (rarity.name().equals(name)) {
                return rarity;
            }
        }
        return null;
    }
}
