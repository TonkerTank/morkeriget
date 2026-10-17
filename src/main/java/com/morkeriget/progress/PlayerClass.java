package com.morkeriget.progress;

import java.util.List;

import com.morkeriget.skill.Skill;

// The classes a player can pick. Each has four skills on the keys 1 to 4 and a bonus of its own (see Progression,
// Mana and PlayerHits). The class can be changed in the skill screen, which gives all skill points back
public enum PlayerClass {
    BARBARIAN("Barbar", 0xFF5544, "Kæmper tæt på og tåler meget", "+20% max liv",
            List.of(Skill.WHIRLWIND, Skill.BATTLE_CRY, Skill.LEAP, Skill.FRENZY)),
    SORCERER("Troldmand", 0xB070FF, "Kaster is, lyn og ild", "+40% hurtigere mana",
            List.of(Skill.FROST_NOVA, Skill.CHAIN_LIGHTNING, Skill.BLINK, Skill.FIREBALL)),
    RANGER("Jæger", 0x55DD55, "Skyder på afstand og har ulve med", "+10% fart og +25% pileskade",
            List.of(Skill.MULTISHOT, Skill.ARROW_RAIN, Skill.SMOKE_BOMB, Skill.WOLVES)),
    NECROMANCER("Nekromantiker", 0x50C8B4, "Rejser de døde og forbander sine fjender", "Heler 5% når en fjende dør nær dig",
            List.of(Skill.BONE_SPEAR, Skill.RAISE_SKELETONS, Skill.BONE_ARMOR, Skill.CURSE));

    public final String displayName;
    public final int color;
    public final String description;
    public final String bonus;
    // In the order of the keys 1 to 4
    public final List<Skill> skills;

    PlayerClass(String displayName, int color, String description, String bonus, List<Skill> skills) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
        this.bonus = bonus;
        this.skills = skills;
    }

    // Whether a player of this class may put points into the upgrade. The ones without a skill are for everyone
    public boolean canUse(Upgrade upgrade) {
        return upgrade.skill == null || skills.contains(upgrade.skill);
    }

    // null for "" (no class picked yet) and unknown names
    public static PlayerClass byName(String name) {
        for (PlayerClass playerClass : values()) {
            if (playerClass.name().equals(name)) {
                return playerClass;
            }
        }
        return null;
    }
}
