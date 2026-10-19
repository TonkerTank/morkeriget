package com.morkeriget.journey;

// The goals of the season journey, in four chapters like in Diablo 4. Each one is reached when its stat gets to the goal,
// and gives the reward of its chapter, see Journey
public enum Achievement {
    KILLS_1(1, Stat.KILLS, 100, "Dræb 100 monstre"),
    ELITES_1(1, Stat.ELITES, 10, "Dræb 10 elites"),
    BOSSES_1(1, Stat.BOSSES, 1, "Dræb en boss"),
    LEVEL_1(1, Stat.LEVEL, 10, "Nå level 10"),
    RIFTS_1(1, Stat.RIFTS, 1, "Luk en rift"),

    KILLS_2(2, Stat.KILLS, 1000, "Dræb 1.000 monstre"),
    ELITES_2(2, Stat.ELITES, 100, "Dræb 100 elites"),
    BOSSES_2(2, Stat.BOSSES, 10, "Dræb 10 bosser"),
    LEVEL_2(2, Stat.LEVEL, 30, "Nå level 30"),
    RIFT_TIER_2(2, Stat.RIFT_TIER, 5, "Luk en rift V"),
    LEGENDARIES_2(2, Stat.LEGENDARIES, 10, "Find 10 legendariske genstande"),
    HELLTIDE_2(2, Stat.HELLTIDE_CHESTS, 3, "Åbn 3 Helltide-kister"),

    KILLS_3(3, Stat.KILLS, 5000, "Dræb 5.000 monstre"),
    ELITES_3(3, Stat.ELITES, 500, "Dræb 500 elites"),
    BOSSES_3(3, Stat.BOSSES, 50, "Dræb 50 bosser"),
    LEVEL_3(3, Stat.LEVEL, 60, "Nå level 60"),
    RIFT_TIER_3(3, Stat.RIFT_TIER, 10, "Luk en rift X"),
    WORLD_BOSSES_3(3, Stat.WORLD_BOSSES, 1, "Besejr en verdensboss"),
    BOUNTIES_3(3, Stat.BOUNTY_DAYS, 5, "Klar alle dagens opgaver 5 gange"),

    KILLS_4(4, Stat.KILLS, 20000, "Dræb 20.000 monstre"),
    BOSSES_4(4, Stat.BOSSES, 200, "Dræb 200 bosser"),
    LEVEL_4(4, Stat.LEVEL, 100, "Nå level 100"),
    RIFTS_4(4, Stat.RIFTS, 50, "Luk 50 rifts"),
    WORLD_BOSSES_4(4, Stat.WORLD_BOSSES, 10, "Besejr 10 verdensbosser"),
    LEGENDARIES_4(4, Stat.LEGENDARIES, 100, "Find 100 legendariske genstande"),
    MYTHIC_4(4, Stat.MYTHICS, 1, "Find en mytisk genstand");

    public static final int CHAPTERS = 4;
    public static final String[] CHAPTER_NAMES = {"De første skridt", "Mørket vokser", "Helvedes porte", "Mørkets herre"};
    // What each achievement in the chapter gives, shown in the screen
    public static final String[] CHAPTER_REWARDS = {"15 essens og en sjælden genstand", "30 essens og en legendarisk genstand",
            "60 essens, en legendarisk genstand og en ædelsten", "100 essens, en legendarisk genstand og en fin ædelsten"};

    // The things that are counted for the player. LEVEL isn't counted, it is the player's level
    public enum Stat {
        KILLS, ELITES, BOSSES, WORLD_BOSSES, RIFTS, RIFT_TIER, LEVEL, LEGENDARIES, MYTHICS, HELLTIDE_CHESTS, BOUNTY_DAYS
    }

    // 1 to CHAPTERS
    public final int chapter;
    public final Stat stat;
    public final int goal;
    public final String text;

    Achievement(int chapter, Stat stat, int goal, String text) {
        this.chapter = chapter;
        this.stat = stat;
        this.goal = goal;
        this.text = text;
    }
}
