package com.morkeriget.progress;

// What paragon points are spent on. After level 100 every paragon level gives one point, so there is always something
// to get. Each stat goes up to its own maxRank, so the strong ones like speed can't get silly.
// What a rank does is in Progression.applyPassives, Progression.onDeath, Mana, Skills and PlayerHits
public enum Paragon {
    DAMAGE("Skade", "+1% skade pr. point", 200),
    HEALTH("Liv", "+1% max liv pr. point", 200),
    MANA("Mana", "+2% hurtigere mana pr. point", 100),
    SPEED("Fart", "+0,5% fart pr. point", 60),
    TOUGHNESS("Hårdhed", "-0,2% skade taget pr. point", 100),
    LIFESTEAL("Livsstjæl", "+0,1% livsstjæl pr. point", 50),
    COOLDOWN("Nedkøling", "-0,2% cooldown pr. point", 100),
    WISDOM("Visdom", "+1% XP pr. point", 100);

    public final String displayName;
    public final String description;
    public final int maxRank;

    Paragon(String displayName, String description, int maxRank) {
        this.displayName = displayName;
        this.description = description;
        this.maxRank = maxRank;
    }

    // XP from one paragon level to the next. It starts a little above what level 99 to 100 took
    public static int xpForNextLevel(int paragonLevel) {
        return 3100 + 60 * paragonLevel;
    }

    public static Paragon byId(int id) {
        Paragon[] values = values();
        return id >= 0 && id < values.length ? values[id] : DAMAGE;
    }
}
