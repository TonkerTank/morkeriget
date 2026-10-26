package com.morkeriget.progress;


import java.util.Locale;

import com.morkeriget.Text;

import net.minecraft.network.chat.MutableComponent;

// What paragon points are spent on. After level 100 every paragon level gives one point, so there is always something
// to get. Each stat goes up to its own maxRank, so the strong ones like speed can't get silly.
// What a rank does is in Progression.applyPassives, Progression.onDeath, Mana, Skills and PlayerHits
public enum Paragon {
    // Names and descriptions are in the language files, under paragon.<name>
    DAMAGE(200),
    HEALTH(200),
    MANA(100),
    SPEED(60),
    TOUGHNESS(100),
    LIFESTEAL(50),
    COOLDOWN(100),
    WISDOM(100);

    public final int maxRank;

    Paragon(int maxRank) {
        this.maxRank = maxRank;
    }

    public MutableComponent displayName() {
        return Text.tr("paragon." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent description() {
        return Text.tr("paragon." + name().toLowerCase(Locale.ROOT) + ".desc");
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
