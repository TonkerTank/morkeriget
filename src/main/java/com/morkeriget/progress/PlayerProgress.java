package com.morkeriget.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.morkeriget.skill.Skill;

// A player's level, XP towards the next level, unspent skill points, the rank of each upgrade, their class
// ("" until they pick one) and the skill on each of the keys 1-4 ("" for an empty key). After level 100 the XP counts
// towards the next paragon level instead, and paragon points are spent on Paragon stats. Saved on the player and kept
// when they die
public record PlayerProgress(int level, int xp, int points, Map<String, Integer> ranks, String playerClass, List<String> slots,
                             int paragonLevel, int paragonPoints, Map<String, Integer> paragon) {
    private static final List<String> NO_SLOTS = Collections.nCopies(SkillTree.SLOTS, "");
    public static final PlayerProgress START = new PlayerProgress(1, 0, 1, Map.of(), "", NO_SLOTS, 0, 0, Map.of());

    // Players saved before classes existed have no class field, and get "" so they pick one.
    // Players saved before the skill tree have no slots, see isFromBeforeSkillTree. Before paragon there was no paragon
    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(PlayerProgress::level),
            Codec.INT.fieldOf("xp").forGetter(PlayerProgress::xp),
            Codec.INT.fieldOf("points").forGetter(PlayerProgress::points),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("ranks").forGetter(PlayerProgress::ranks),
            Codec.STRING.optionalFieldOf("class", "").forGetter(PlayerProgress::playerClass),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(PlayerProgress::slots),
            Codec.INT.optionalFieldOf("paragon_level", 0).forGetter(PlayerProgress::paragonLevel),
            Codec.INT.optionalFieldOf("paragon_points", 0).forGetter(PlayerProgress::paragonPoints),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("paragon", Map.of()).forGetter(PlayerProgress::paragon)
    ).apply(instance, PlayerProgress::new));

    // XP needed to go from this level to the next. Level 1 needs 70, level 2 needs 100, and so on
    public static int xpForNextLevel(int level) {
        return 40 + 30 * level;
    }

    // Every level gives a point, also level 1, so a new player can pick their first skill
    public static int pointsForLevel(int level) {
        return level;
    }

    public int rank(Upgrade upgrade) {
        return ranks.getOrDefault(upgrade.name(), 0);
    }

    public int rank(Paragon stat) {
        return paragon.getOrDefault(stat.name(), 0);
    }

    // The skill on the key (0 to 3), or null
    public Skill slot(int index) {
        return index < slots.size() ? skillByName(slots.get(index)) : null;
    }

    // Before the skill tree you had all four skills from the start, and the points were spent differently.
    // Those players get all their points back once, see Progression.onLogin
    public boolean isFromBeforeSkillTree() {
        return slots.size() != SkillTree.SLOTS;
    }

    // Spends one skill point on the upgrade. A skill that was just unlocked goes on the first free key
    public PlayerProgress withUpgrade(Upgrade upgrade) {
        Map<String, Integer> newRanks = new HashMap<>(ranks);
        newRanks.put(upgrade.name(), rank(upgrade) + 1);
        List<String> newSlots = new ArrayList<>(slots);
        if (upgrade.skill != null && rank(upgrade) == 0 && !newSlots.contains(upgrade.skill.name()) && newSlots.contains("")) {
            newSlots.set(newSlots.indexOf(""), upgrade.skill.name());
        }
        return new PlayerProgress(level, xp, points - 1, Map.copyOf(newRanks), playerClass, List.copyOf(newSlots),
                paragonLevel, paragonPoints, paragon);
    }

    // Puts the skill on the key. If it was on another key already, the two keys swap
    public PlayerProgress withSlot(int index, Skill skill) {
        List<String> newSlots = new ArrayList<>(slots);
        int before = newSlots.indexOf(skill.name());
        if (before >= 0) {
            newSlots.set(before, newSlots.get(index));
        }
        newSlots.set(index, skill.name());
        return new PlayerProgress(level, xp, points, ranks, playerClass, List.copyOf(newSlots), paragonLevel, paragonPoints, paragon);
    }

    // Picks a class (or the same one again) and gives back every skill point, so they can be spent again.
    // Paragon points stay where they are, they aren't tied to a class
    public PlayerProgress withClass(PlayerClass newClass) {
        return new PlayerProgress(level, xp, pointsForLevel(level), Map.of(), newClass.name(), NO_SLOTS, paragonLevel, paragonPoints, paragon);
    }

    // Every skill point back and empty keys, but the same class
    public PlayerProgress respec() {
        return new PlayerProgress(level, xp, pointsForLevel(level), Map.of(), playerClass, NO_SLOTS, paragonLevel, paragonPoints, paragon);
    }

    public PlayerProgress withLevel(int newLevel, int newXp, int newPoints) {
        return new PlayerProgress(newLevel, newXp, newPoints, ranks, playerClass, slots, paragonLevel, paragonPoints, paragon);
    }

    public PlayerProgress withParagonLevel(int newParagonLevel, int newXp, int newParagonPoints) {
        return new PlayerProgress(level, newXp, points, ranks, playerClass, slots, newParagonLevel, newParagonPoints, paragon);
    }

    // Spends one paragon point on the stat
    public PlayerProgress withParagon(Paragon stat) {
        Map<String, Integer> newParagon = new HashMap<>(paragon);
        newParagon.put(stat.name(), rank(stat) + 1);
        return new PlayerProgress(level, xp, points, ranks, playerClass, slots, paragonLevel, paragonPoints - 1, Map.copyOf(newParagon));
    }

    private static Skill skillByName(String name) {
        for (Skill skill : Skill.values()) {
            if (skill.name().equals(name)) {
                return skill;
            }
        }
        return null;
    }
}
