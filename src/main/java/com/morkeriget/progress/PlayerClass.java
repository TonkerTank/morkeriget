package com.morkeriget.progress;

import java.util.List;
import java.util.Locale;

import com.morkeriget.Text;
import com.morkeriget.skill.Skill;
import net.minecraft.network.chat.MutableComponent;

// The classes a player can pick. Each has a skill tree (see SkillTree) with its skills, and a bonus of its own (see
// Progression, Mana and PlayerHits). The class can be changed in the skill screen, which gives all skill points back
public enum PlayerClass {
    // Names, descriptions and bonuses are in the language files, under class.<name>. The number after the colour is the
    // tree version: raising it gives every player of the class their points back once, when the tree has been reworked
    BARBARIAN(0xFF5544, 0, List.of(Skill.WHIRLWIND, Skill.BATTLE_CRY, Skill.LEAP, Skill.FRENZY, Skill.EARTHQUAKE, Skill.RUSH, Skill.DOOM_SLAM)),
    SORCERER(0xB070FF, 1, List.of(Skill.FROST_NOVA, Skill.CHAIN_LIGHTNING, Skill.BLINK, Skill.FIREBALL, Skill.METEOR, Skill.ICE_ARMOR, Skill.THUNDERSTORM)),
    RANGER(0x55DD55, 0, List.of(Skill.MULTISHOT, Skill.ARROW_RAIN, Skill.SMOKE_BOMB, Skill.WOLVES, Skill.EXPLOSIVE_ARROW, Skill.BEAR_TRAP, Skill.ARROW_HURRICANE)),
    NECROMANCER(0x50C8B4, 0, List.of(Skill.BONE_SPEAR, Skill.RAISE_SKELETONS, Skill.BONE_ARMOR, Skill.CURSE, Skill.BLOOD_NOVA, Skill.BONE_PRISON, Skill.ARMY_OF_THE_DEAD)),
    PALADIN(0xFFD24A, 0, List.of(Skill.HOLY_HAMMER, Skill.SHIELD_BASH, Skill.HOLY_AURA, Skill.HOLY_LIGHT, Skill.CONSECRATION, Skill.SPEAR_OF_JUDGEMENT, Skill.HEAVENS_JUDGEMENT));

    public final int color;
    // Every skill in the class's tree, shown in the class screen. Which ones are on the keys is up to the player
    public final List<Skill> skills;
    public final int treeVersion;

    PlayerClass(int color, int treeVersion, List<Skill> skills) {
        this.treeVersion = treeVersion;
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
