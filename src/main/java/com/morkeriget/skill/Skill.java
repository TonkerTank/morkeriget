package com.morkeriget.skill;

// The skills the player can use. Each one costs mana and has a cooldown in ticks (20 ticks is one second)
public enum Skill {
    WHIRLWIND("Hvirvelvind", 30, 2 * 20),
    FROST_NOVA("Frostnova", 40, 8 * 20),
    BLINK("Blink", 20, 3 * 20);

    public final String displayName;
    public final float manaCost;
    public final int cooldown;

    Skill(String displayName, float manaCost, int cooldown) {
        this.displayName = displayName;
        this.manaCost = manaCost;
        this.cooldown = cooldown;
    }

    public static Skill byId(int id) {
        Skill[] values = values();
        return id >= 0 && id < values.length ? values[id] : WHIRLWIND;
    }
}
