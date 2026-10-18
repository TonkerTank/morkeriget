package com.morkeriget.progress;

// What paragon points are spent on. After level 100 every paragon level gives one point, so there is always something
// to get. Each stat goes up to MAX_RANK. What a rank does is in Progression.applyPassives and Mana
public enum Paragon {
    DAMAGE("Skade", "+1% skade pr. point"),
    HEALTH("Liv", "+1% max liv pr. point"),
    MANA("Mana", "+2% hurtigere mana pr. point"),
    SPEED("Fart", "+0,5% fart pr. point");

    public static final int MAX_RANK = 50;

    public final String displayName;
    public final String description;

    Paragon(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
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
