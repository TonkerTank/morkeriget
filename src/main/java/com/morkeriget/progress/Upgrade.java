package com.morkeriget.progress;

// Things the player can spend skill points on. Each one can be raised to MAX_RANK.
// What a rank actually does is in Skills, Mana and Progression
public enum Upgrade {
    WHIRLWIND("Hvirvelvind", "+20% skade pr. rank"),
    FROST_NOVA("Frostnova", "+0,5 blok og +0,5 sek. frys pr. rank"),
    BLINK("Blink", "+2 blokke længere pr. rank"),
    CHAIN_LIGHTNING("Kædelyn", "+15% skade og +1 hop pr. rank"),
    BATTLE_CRY("Krigsråb", "+1 sek. varighed pr. rank"),
    VITALITY("Vitalitet", "+1 hjerte pr. rank"),
    SPIRIT("Ånd", "+20% hurtigere mana pr. rank"),
    STRENGTH("Styrke", "+5% skade pr. rank");

    public static final int MAX_RANK = 10;

    public final String displayName;
    public final String description;

    Upgrade(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static Upgrade byId(int id) {
        Upgrade[] values = values();
        return id >= 0 && id < values.length ? values[id] : WHIRLWIND;
    }
}
