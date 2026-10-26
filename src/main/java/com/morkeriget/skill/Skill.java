package com.morkeriget.skill;

import java.util.Locale;

import com.morkeriget.Config;
import com.morkeriget.Text;

import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;

// The skills the player can use. Mana cost and cooldown come from the config file.
// The potions work like skills that cost no mana, so they get a key, a cooldown and a place on the skill bar
public enum Skill {
    WHIRLWIND(Config.WHIRLWIND_MANA, Config.WHIRLWIND_COOLDOWN),
    FROST_NOVA(Config.FROST_NOVA_MANA, Config.FROST_NOVA_COOLDOWN),
    BLINK(Config.BLINK_MANA, Config.BLINK_COOLDOWN),
    CHAIN_LIGHTNING(Config.CHAIN_LIGHTNING_MANA, Config.CHAIN_LIGHTNING_COOLDOWN),
    BATTLE_CRY(Config.BATTLE_CRY_MANA, Config.BATTLE_CRY_COOLDOWN),
    LEAP(Config.LEAP_MANA, Config.LEAP_COOLDOWN),
    FRENZY(Config.FRENZY_MANA, Config.FRENZY_COOLDOWN),
    FIREBALL(Config.FIREBALL_MANA, Config.FIREBALL_COOLDOWN),
    MULTISHOT(Config.MULTISHOT_MANA, Config.MULTISHOT_COOLDOWN),
    ARROW_RAIN(Config.ARROW_RAIN_MANA, Config.ARROW_RAIN_COOLDOWN),
    SMOKE_BOMB(Config.SMOKE_BOMB_MANA, Config.SMOKE_BOMB_COOLDOWN),
    WOLVES(Config.WOLVES_MANA, Config.WOLVES_COOLDOWN),
    BONE_SPEAR(Config.BONE_SPEAR_MANA, Config.BONE_SPEAR_COOLDOWN),
    RAISE_SKELETONS(Config.RAISE_SKELETONS_MANA, Config.RAISE_SKELETONS_COOLDOWN),
    BONE_ARMOR(Config.BONE_ARMOR_MANA, Config.BONE_ARMOR_COOLDOWN),
    CURSE(Config.CURSE_MANA, Config.CURSE_COOLDOWN),
    EARTHQUAKE(Config.EARTHQUAKE_MANA, Config.EARTHQUAKE_COOLDOWN),
    RUSH(Config.RUSH_MANA, Config.RUSH_COOLDOWN),
    METEOR(Config.METEOR_MANA, Config.METEOR_COOLDOWN),
    ICE_ARMOR(Config.ICE_ARMOR_MANA, Config.ICE_ARMOR_COOLDOWN),
    EXPLOSIVE_ARROW(Config.EXPLOSIVE_ARROW_MANA, Config.EXPLOSIVE_ARROW_COOLDOWN),
    BEAR_TRAP(Config.BEAR_TRAP_MANA, Config.BEAR_TRAP_COOLDOWN),
    BLOOD_NOVA(Config.BLOOD_NOVA_MANA, Config.BLOOD_NOVA_COOLDOWN),
    BONE_PRISON(Config.BONE_PRISON_MANA, Config.BONE_PRISON_COOLDOWN),
    THUNDERSTORM(Config.THUNDERSTORM_MANA, Config.THUNDERSTORM_COOLDOWN),
    DOOM_SLAM(Config.DOOM_SLAM_MANA, Config.DOOM_SLAM_COOLDOWN),
    ARROW_HURRICANE(Config.ARROW_HURRICANE_MANA, Config.ARROW_HURRICANE_COOLDOWN),
    ARMY_OF_THE_DEAD(Config.ARMY_OF_THE_DEAD_MANA, Config.ARMY_OF_THE_DEAD_COOLDOWN),
    HEALTH_POTION(null, Config.HEALTH_POTION_COOLDOWN),
    MANA_POTION(null, Config.MANA_POTION_COOLDOWN);

    // null for the potions
    private final ModConfigSpec.DoubleValue manaCost;
    private final ModConfigSpec.DoubleValue cooldownSeconds;

    Skill(ModConfigSpec.DoubleValue manaCost, ModConfigSpec.DoubleValue cooldownSeconds) {
        this.manaCost = manaCost;
        this.cooldownSeconds = cooldownSeconds;
    }

    // From the language files, under skill.<name>
    public MutableComponent displayName() {
        return Text.tr("skill." + name().toLowerCase(Locale.ROOT));
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
