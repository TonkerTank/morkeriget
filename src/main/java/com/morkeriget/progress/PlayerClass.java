package com.morkeriget.progress;

import java.util.List;
import java.util.Locale;

import com.morkeriget.Text;
import com.morkeriget.skill.Skill;
import net.minecraft.network.chat.MutableComponent;

// The classes a player can pick. Each has a skill tree (see SkillTree) with its skills, and a bonus of its own (see
// Progression, Mana and PlayerHits). The class can be changed in the skill screen, which gives all skill points back
public enum PlayerClass {
    // Names, descriptions and bonuses are in the language files, under class.<name>
    BARBARIAN(0xFF5544, List.of(Skill.WHIRLWIND, Skill.BATTLE_CRY, Skill.LEAP, Skill.FRENZY, Skill.EARTHQUAKE, Skill.RUSH, Skill.DOOM_SLAM)),
    SORCERER(0xB070FF, List.of(Skill.FROST_NOVA, Skill.CHAIN_LIGHTNING, Skill.BLINK, Skill.FIREBALL, Skill.METEOR, Skill.ICE_ARMOR, Skill.THUNDERSTORM)),
    RANGER(0x55DD55, List.of(Skill.MULTISHOT, Skill.ARROW_RAIN, Skill.SMOKE_BOMB, Skill.WOLVES, Skill.EXPLOSIVE_ARROW, Skill.BEAR_TRAP, Skill.ARROW_HURRICANE)),
    NECROMANCER(0x50C8B4, List.of(Skill.BONE_SPEAR, Skill.RAISE_SKELETONS, Skill.BONE_ARMOR, Skill.CURSE, Skill.BLOOD_NOVA, Skill.BONE_PRISON, Skill.ARMY_OF_THE_DEAD));

    public final int color;
    // Every skill in the class's tree, shown in the class screen. Which ones are on the keys is up to the player
    public final List<Skill> skills;

    PlayerClass(int color, List<Skill> skills) {
        this.color = color;
        this.skills = skills;
    }

    public MutableComponent displayName() {
        return Text.tr("class." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent description() {
        return Text.tr("class." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    public MutableComponent bonus() {
        return Text.tr("class." + name().toLowerCase(Locale.ROOT) + ".bonus");
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
