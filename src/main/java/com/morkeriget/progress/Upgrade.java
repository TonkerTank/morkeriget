package com.morkeriget.progress;

import java.util.Locale;

import com.morkeriget.Text;
import com.morkeriget.skill.Skill;

import net.minecraft.network.chat.MutableComponent;

// Things the player can spend skill points on. The class ones make up the class's skill tree (see SkillTree): the first
// point in a skill unlocks it, and passives make you better at something without a key. The ones without a class are
// for everyone and aren't part of the tree. What a rank actually does is in Skills, Passives, Mana and Progression
public enum Upgrade {
    // Barbarian
    WHIRLWIND(Skill.WHIRLWIND, PlayerClass.BARBARIAN, 0),
    LEAP(Skill.LEAP, PlayerClass.BARBARIAN, 0),
    UNYIELDING(null, PlayerClass.BARBARIAN, 0),
    BATTLE_CRY(Skill.BATTLE_CRY, PlayerClass.BARBARIAN, 1),
    FRENZY(Skill.FRENZY, PlayerClass.BARBARIAN, 1),
    BLOODTHIRSTY(null, PlayerClass.BARBARIAN, 1),
    EARTHQUAKE(Skill.EARTHQUAKE, PlayerClass.BARBARIAN, 2),
    RUSH(Skill.RUSH, PlayerClass.BARBARIAN, 2),
    RELENTLESS(null, PlayerClass.BARBARIAN, 2),
    DOOM_SLAM(Skill.DOOM_SLAM, PlayerClass.BARBARIAN, 3),
    // Sorcerer
    FROST_NOVA(Skill.FROST_NOVA, PlayerClass.SORCERER, 0),
    FIREBALL(Skill.FIREBALL, PlayerClass.SORCERER, 0),
    GLASS_CANNON(null, PlayerClass.SORCERER, 0),
    CHAIN_LIGHTNING(Skill.CHAIN_LIGHTNING, PlayerClass.SORCERER, 1),
    BLINK(Skill.BLINK, PlayerClass.SORCERER, 1),
    ARCANE_FLOW(null, PlayerClass.SORCERER, 1),
    METEOR(Skill.METEOR, PlayerClass.SORCERER, 2),
    ICE_ARMOR(Skill.ICE_ARMOR, PlayerClass.SORCERER, 2),
    TIME_WARP(null, PlayerClass.SORCERER, 2),
    THUNDERSTORM(Skill.THUNDERSTORM, PlayerClass.SORCERER, 3),
    // Ranger
    MULTISHOT(Skill.MULTISHOT, PlayerClass.RANGER, 0),
    SMOKE_BOMB(Skill.SMOKE_BOMB, PlayerClass.RANGER, 0),
    MARKSMAN(null, PlayerClass.RANGER, 0),
    ARROW_RAIN(Skill.ARROW_RAIN, PlayerClass.RANGER, 1),
    WOLVES(Skill.WOLVES, PlayerClass.RANGER, 1),
    AGILE(null, PlayerClass.RANGER, 1),
    EXPLOSIVE_ARROW(Skill.EXPLOSIVE_ARROW, PlayerClass.RANGER, 2),
    BEAR_TRAP(Skill.BEAR_TRAP, PlayerClass.RANGER, 2),
    PACK_LEADER(null, PlayerClass.RANGER, 2),
    ARROW_HURRICANE(Skill.ARROW_HURRICANE, PlayerClass.RANGER, 3),
    // Necromancer
    BONE_SPEAR(Skill.BONE_SPEAR, PlayerClass.NECROMANCER, 0),
    BONE_ARMOR(Skill.BONE_ARMOR, PlayerClass.NECROMANCER, 0),
    GRAVE_CHILL(null, PlayerClass.NECROMANCER, 0),
    RAISE_SKELETONS(Skill.RAISE_SKELETONS, PlayerClass.NECROMANCER, 1),
    CURSE(Skill.CURSE, PlayerClass.NECROMANCER, 1),
    DEATH_LORD(null, PlayerClass.NECROMANCER, 1),
    BLOOD_NOVA(Skill.BLOOD_NOVA, PlayerClass.NECROMANCER, 2),
    BONE_PRISON(Skill.BONE_PRISON, PlayerClass.NECROMANCER, 2),
    SOUL_HARVEST(null, PlayerClass.NECROMANCER, 2),
    ARMY_OF_THE_DEAD(Skill.ARMY_OF_THE_DEAD, PlayerClass.NECROMANCER, 3),
    // For everyone
    VITALITY(null, null, 0),
    SPIRIT(null, null, 0),
    STRENGTH(null, null, 0);

    // Skills go to 20, passives and the shared upgrades to 10. There are far more ranks than skill points at level 100,
    // so you have to pick what to get good at
    private static final int SKILL_MAX_RANK = 20;
    private static final int PASSIVE_MAX_RANK = 10;
    private static final int SHARED_MAX_RANK = 10;
    // Skill ranks above this still add damage, but things like radius, duration, jumps and how many summons stop
    // growing, or they would get silly. Instead each rank above it takes COOLDOWN_PER_RANK off the skill's cooldown
    public static final int UTILITY_MAX_RANK = 10;
    public static final float COOLDOWN_PER_RANK = 0.02f;

    // The skill this upgrade unlocks and makes better, or null for passives and the shared upgrades
    public final Skill skill;
    // The class whose tree it is in, or null for the shared upgrades
    public final PlayerClass playerClass;
    // The row in the tree, from 0
    public final int row;

    Upgrade(Skill skill, PlayerClass playerClass, int row) {
        this.skill = skill;
        this.playerClass = playerClass;
        this.row = row;
    }

    // Names and descriptions are in the language files, under upgrade.<name>
    public MutableComponent displayName() {
        return Text.tr("upgrade." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent description() {
        return Text.tr("upgrade." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    public boolean isPassive() {
        return skill == null && playerClass != null;
    }

    public int maxRank() {
        return isPassive() ? PASSIVE_MAX_RANK : playerClass == null ? SHARED_MAX_RANK : SKILL_MAX_RANK;
    }

    // The rank that counts for radius, duration, jumps and how many summons, see UTILITY_MAX_RANK
    public static int utility(int rank) {
        return Math.min(rank, UTILITY_MAX_RANK);
    }

    // How much shorter the skill's cooldown is from its ranks above UTILITY_MAX_RANK, e.g. 0.9 at rank 15
    public static float cooldownScale(int rank) {
        return 1 - COOLDOWN_PER_RANK * Math.max(0, rank - UTILITY_MAX_RANK);
    }

    // The upgrade that unlocks the skill, or null for the potions
    public static Upgrade forSkill(Skill skill) {
        for (Upgrade upgrade : values()) {
            if (upgrade.skill == skill) {
                return upgrade;
            }
        }
        return null;
    }

    public static Upgrade byId(int id) {
        Upgrade[] values = values();
        return id >= 0 && id < values.length ? values[id] : WHIRLWIND;
    }
}
