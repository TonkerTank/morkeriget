package com.morkeriget.skill;

import com.morkeriget.Config;

import net.neoforged.neoforge.common.ModConfigSpec;

// The skills the player can use. Mana cost and cooldown come from the config file.
// The potions work like skills that cost no mana, so they get a key, a cooldown and a place on the skill bar
public enum Skill {
    WHIRLWIND("Hvirvelvind", Config.WHIRLWIND_MANA, Config.WHIRLWIND_COOLDOWN),
    FROST_NOVA("Frostnova", Config.FROST_NOVA_MANA, Config.FROST_NOVA_COOLDOWN),
    BLINK("Blink", Config.BLINK_MANA, Config.BLINK_COOLDOWN),
    CHAIN_LIGHTNING("Kædelyn", Config.CHAIN_LIGHTNING_MANA, Config.CHAIN_LIGHTNING_COOLDOWN),
    BATTLE_CRY("Krigsråb", Config.BATTLE_CRY_MANA, Config.BATTLE_CRY_COOLDOWN),
    HEALTH_POTION("Livsdrik", null, Config.HEALTH_POTION_COOLDOWN),
    MANA_POTION("Manadrik", null, Config.MANA_POTION_COOLDOWN);

    public final String displayName;
    // null for the potions
    private final ModConfigSpec.DoubleValue manaCost;
    private final ModConfigSpec.DoubleValue cooldownSeconds;

    Skill(String displayName, ModConfigSpec.DoubleValue manaCost, ModConfigSpec.DoubleValue cooldownSeconds) {
        this.displayName = displayName;
        this.manaCost = manaCost;
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean isPotion() {
        return manaCost == null;
    }

    public float manaCost() {
        return manaCost != null ? manaCost.get().floatValue() : 0;
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
