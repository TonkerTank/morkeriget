package com.morkeriget.loot;

import java.util.Locale;

import com.morkeriget.Text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

// How rare a piece of loot is. Rarer loot gets more bonuses, just like in Diablo
public enum Rarity {
    COMMON(ChatFormatting.WHITE, 0, 50, 0xFFFFFF),
    MAGIC(ChatFormatting.BLUE, 1, 30, 0x5577FF),
    RARE(ChatFormatting.YELLOW, 2, 15, 0xFFFF33),
    LEGENDARY(ChatFormatting.GOLD, 4, 5, 0xFF8800),
    // Set items are never rolled at random (weight 0). They drop on purpose, see Loot.createSet
    SET(ChatFormatting.GREEN, 3, 0, 0x33FF55),
    // Mythic items are never rolled at random either. They are the rarest drops, see Loot.createMythic
    MYTHIC(ChatFormatting.LIGHT_PURPLE, 4, 0, 0xCC55FF);

    public final ChatFormatting color;
    public final int affixCount;
    private final int weight;
    // Colour of the light beam over the item when it lies on the ground
    public final int beamColor;

    Rarity(ChatFormatting color, int affixCount, int weight, int beamColor) {
        this.color = color;
        this.affixCount = affixCount;
        this.weight = weight;
        this.beamColor = beamColor;
    }

    // E.g. "Legendary", from the language files
    public MutableComponent displayName() {
        return Text.tr("rarity." + name().toLowerCase(Locale.ROOT));
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
