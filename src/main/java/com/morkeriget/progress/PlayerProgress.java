package com.morkeriget.progress;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// A player's level, XP towards the next level, unspent skill points, the rank of each upgrade and their class
// ("" until they pick one). Saved on the player and kept when they die
public record PlayerProgress(int level, int xp, int points, Map<String, Integer> ranks, String playerClass) {
    public static final PlayerProgress START = new PlayerProgress(1, 0, 0, Map.of(), "");

    // Players saved before classes existed have no class field, and get "" so they pick one
    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(PlayerProgress::level),
            Codec.INT.fieldOf("xp").forGetter(PlayerProgress::xp),
            Codec.INT.fieldOf("points").forGetter(PlayerProgress::points),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("ranks").forGetter(PlayerProgress::ranks),
            Codec.STRING.optionalFieldOf("class", "").forGetter(PlayerProgress::playerClass)
    ).apply(instance, PlayerProgress::new));

    // XP needed to go from this level to the next. Level 1 needs 70, level 2 needs 100, and so on
    public static int xpForNextLevel(int level) {
        return 40 + 30 * level;
    }

    public int rank(Upgrade upgrade) {
        return ranks.getOrDefault(upgrade.name(), 0);
    }

    // Spends one skill point on the upgrade
    public PlayerProgress withUpgrade(Upgrade upgrade) {
        Map<String, Integer> newRanks = new HashMap<>(ranks);
        newRanks.put(upgrade.name(), rank(upgrade) + 1);
        return new PlayerProgress(level, xp, points - 1, Map.copyOf(newRanks), playerClass);
    }

    // Picks a class and gives back every skill point, so they can be spent on the new class's skills
    public PlayerProgress withClass(PlayerClass newClass) {
        return new PlayerProgress(level, xp, level - 1, Map.of(), newClass.name());
    }

    public PlayerProgress withLevel(int newLevel, int newXp, int newPoints) {
        return new PlayerProgress(newLevel, newXp, newPoints, ranks, playerClass);
    }
}
