package com.morkeriget.journey;

import java.util.Locale;

import com.morkeriget.Text;

import net.minecraft.network.chat.MutableComponent;

// The goals of the season journey, in four chapters like in Diablo 4. Each one is reached when its stat gets to the goal,
// and gives the reward of its chapter, see Journey
public enum Achievement {
    KILLS_1(1, Stat.KILLS, 100),
    ELITES_1(1, Stat.ELITES, 10),
    BOSSES_1(1, Stat.BOSSES, 1),
    LEVEL_1(1, Stat.LEVEL, 10),
    RIFTS_1(1, Stat.RIFTS, 1),

    KILLS_2(2, Stat.KILLS, 1000),
    ELITES_2(2, Stat.ELITES, 100),
    BOSSES_2(2, Stat.BOSSES, 10),
    LEVEL_2(2, Stat.LEVEL, 30),
    RIFT_TIER_2(2, Stat.RIFT_TIER, 5),
    LEGENDARIES_2(2, Stat.LEGENDARIES, 10),
    HELLTIDE_2(2, Stat.HELLTIDE_CHESTS, 3),

    KILLS_3(3, Stat.KILLS, 5000),
    ELITES_3(3, Stat.ELITES, 500),
    BOSSES_3(3, Stat.BOSSES, 50),
    LEVEL_3(3, Stat.LEVEL, 60),
    RIFT_TIER_3(3, Stat.RIFT_TIER, 10),
    WORLD_BOSSES_3(3, Stat.WORLD_BOSSES, 1),
    BOUNTIES_3(3, Stat.BOUNTY_DAYS, 5),

    KILLS_4(4, Stat.KILLS, 20000),
    BOSSES_4(4, Stat.BOSSES, 200),
    LEVEL_4(4, Stat.LEVEL, 100),
    RIFTS_4(4, Stat.RIFTS, 50),
    WORLD_BOSSES_4(4, Stat.WORLD_BOSSES, 10),
    LEGENDARIES_4(4, Stat.LEGENDARIES, 100),
    MYTHIC_4(4, Stat.MYTHICS, 1);

    public static final int CHAPTERS = 4;


    // The things that are counted for the player. LEVEL isn't counted, it is the player's level
    public enum Stat {
        KILLS, ELITES, BOSSES, WORLD_BOSSES, RIFTS, RIFT_TIER, LEVEL, LEGENDARIES, MYTHICS, HELLTIDE_CHESTS, BOUNTY_DAYS
    }

    // 1 to CHAPTERS
    public final int chapter;
    public final Stat stat;
    public final int goal;

    Achievement(int chapter, Stat stat, int goal) {
        this.chapter = chapter;
        this.stat = stat;
        this.goal = goal;
    }

    public MutableComponent text() {
        return Text.tr("achievement." + name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent chapterName(int chapter) {
        return Text.tr("journey.chapter." + chapter);
    }

    // What each achievement in the chapter gives, shown in the screen
    public static MutableComponent chapterReward(int chapter) {
        return Text.tr("journey.reward." + chapter);
    }
}
