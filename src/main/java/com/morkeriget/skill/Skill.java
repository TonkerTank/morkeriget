package com.morkeriget.skill;

import com.morkeriget.Config;

import net.neoforged.neoforge.common.ModConfigSpec;

// The skills the player can use. Mana cost and cooldown come from the config file
public enum Skill {
    WHIRLWIND("Hvirvelvind", Config.WHIRLWIND_MANA, Config.WHIRLWIND_COOLDOWN),
    FROST_NOVA("Frostnova", Config.FROST_NOVA_MANA, Config.FROST_NOVA_COOLDOWN),
    BLINK("Blink", Config.BLINK_MANA, Config.BLINK_COOLDOWN),
    CHAIN_LIGHTNING("Kædelyn", Config.CHAIN_LIGHTNING_MANA, Config.CHAIN_LIGHTNING_COOLDOWN),
    BATTLE_CRY("Krigsråb", Config.BATTLE_CRY_MANA, Config.BATTLE_CRY_COOLDOWN);

    public final String displayName;
    private final ModConfigSpec.DoubleValue manaCost;
    private final ModConfigSpec.DoubleValue cooldownSeconds;

    Skill(String displayName, ModConfigSpec.DoubleValue manaCost, ModConfigSpec.DoubleValue cooldownSeconds) {
        this.displayName = displayName;
        this.manaCost = manaCost;
        this.cooldownSeconds = cooldownSeconds;
    }

    public float manaCost() {
        return manaCost.get().floatValue();
    }

    // In ticks (20 ticks is one second)
    public int cooldown() {
        return (int) Math.round(cooldownSeconds.get() * 20);
    }

    public static Skill byId(int id) {
        Skill[] values = values();
        return id >= 0 && id < values.length ? values[id] : WHIRLWIND;
    }
}
