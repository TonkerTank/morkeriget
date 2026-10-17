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
    LEAP("Spring", Config.LEAP_MANA, Config.LEAP_COOLDOWN),
    FRENZY("Raseri", Config.FRENZY_MANA, Config.FRENZY_COOLDOWN),
    FIREBALL("Ildkugle", Config.FIREBALL_MANA, Config.FIREBALL_COOLDOWN),
    MULTISHOT("Multiskud", Config.MULTISHOT_MANA, Config.MULTISHOT_COOLDOWN),
    ARROW_RAIN("Pileregn", Config.ARROW_RAIN_MANA, Config.ARROW_RAIN_COOLDOWN),
    SMOKE_BOMB("Røgbombe", Config.SMOKE_BOMB_MANA, Config.SMOKE_BOMB_COOLDOWN),
    WOLVES("Ulveflok", Config.WOLVES_MANA, Config.WOLVES_COOLDOWN),
    BONE_SPEAR("Knoglespyd", Config.BONE_SPEAR_MANA, Config.BONE_SPEAR_COOLDOWN),
    RAISE_SKELETONS("Rejs skeletter", Config.RAISE_SKELETONS_MANA, Config.RAISE_SKELETONS_COOLDOWN),
    BONE_ARMOR("Knoglerustning", Config.BONE_ARMOR_MANA, Config.BONE_ARMOR_COOLDOWN),
    CURSE("Forbandelse", Config.CURSE_MANA, Config.CURSE_COOLDOWN),
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
