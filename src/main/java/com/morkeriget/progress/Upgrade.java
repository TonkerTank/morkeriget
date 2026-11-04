package com.morkeriget.progress;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.morkeriget.Text;
import com.morkeriget.skill.Skill;

import net.minecraft.network.chat.MutableComponent;

// Things the player can spend skill points on. The class ones make up the class's skill tree (see SkillTree): the first
// point in a skill unlocks it, and passives make you better at something without a key. The ones without a class are
// for everyone and aren't part of the tree. What a rank actually does is in Skills, Passives, Mana and Progression.
// The reworked trees (all of them now, see Kind) also have variants and keystones: one point each, and only one of a
// skill's variants, and one of the class's keystones, can be picked at a time. Switching between them is free
public enum Upgrade {
    // Barbarian. Three branches: blade, earth and blood
    WHIRLWIND(Kind.SKILL, Skill.WHIRLWIND, PlayerClass.BARBARIAN, 0, 0, null, 0),
    LEAP(Kind.SKILL, Skill.LEAP, PlayerClass.BARBARIAN, 0, 1, null, 0),
    FRENZY(Kind.SKILL, Skill.FRENZY, PlayerClass.BARBARIAN, 0, 2, null, 0),
    RELENTLESS(Kind.PASSIVE, null, PlayerClass.BARBARIAN, 1, 0, WHIRLWIND, 4),
    EARTHQUAKE(Kind.SKILL, Skill.EARTHQUAKE, PlayerClass.BARBARIAN, 1, 1, LEAP, 4),
    BLOODTHIRSTY(Kind.PASSIVE, null, PlayerClass.BARBARIAN, 1, 2, FRENZY, 4),
    RUSH(Kind.SKILL, Skill.RUSH, PlayerClass.BARBARIAN, 2, 0, RELENTLESS, 10),
    DOOM_SLAM(Kind.SKILL, Skill.DOOM_SLAM, PlayerClass.BARBARIAN, 2, 1, EARTHQUAKE, 10),
    BATTLE_CRY(Kind.SKILL, Skill.BATTLE_CRY, PlayerClass.BARBARIAN, 2, 2, BLOODTHIRSTY, 10),
    UNYIELDING(Kind.PASSIVE, null, PlayerClass.BARBARIAN, 3, 0, null, 20),
    WHIRLWIND_BLEED(WHIRLWIND),
    WHIRLWIND_WIDE(WHIRLWIND),
    RUSH_TRAMPLE(RUSH),
    RUSH_LONG(RUSH),
    LEAP_HEAVY(LEAP),
    LEAP_LIGHT(LEAP),
    EARTHQUAKE_WIDE(EARTHQUAKE),
    EARTHQUAKE_CRIPPLE(EARTHQUAKE),
    DOOM_SLAM_ECHO(DOOM_SLAM),
    DOOM_SLAM_QUAKE(DOOM_SLAM),
    FRENZY_BLOOD_RUSH(FRENZY),
    FRENZY_BERSERK(FRENZY),
    BATTLE_CRY_TERRIFY(BATTLE_CRY),
    BATTLE_CRY_SPIRIT(BATTLE_CRY),
    BERSERKER_RAGE(Kind.KEYSTONE, null, PlayerClass.BARBARIAN, 3, 1, null, 20),
    IRON_SKIN(Kind.KEYSTONE, null, PlayerClass.BARBARIAN, 3, 1, null, 20),
    CALL_OF_BLOOD(Kind.KEYSTONE, null, PlayerClass.BARBARIAN, 3, 1, null, 20),
    // Sorcerer. Three branches, fire, frost and lightning, where each box needs a point in the one above it. Each
    // skill has two variants, and at the bottom are Time Warp and the three keystones. See SkillTree
    FIREBALL(Kind.SKILL, Skill.FIREBALL, PlayerClass.SORCERER, 0, 0, null, 0),
    FROST_NOVA(Kind.SKILL, Skill.FROST_NOVA, PlayerClass.SORCERER, 0, 1, null, 0),
    CHAIN_LIGHTNING(Kind.SKILL, Skill.CHAIN_LIGHTNING, PlayerClass.SORCERER, 0, 2, null, 0),
    GLASS_CANNON(Kind.PASSIVE, null, PlayerClass.SORCERER, 1, 0, FIREBALL, 4),
    BLINK(Kind.SKILL, Skill.BLINK, PlayerClass.SORCERER, 1, 1, FROST_NOVA, 4),
    ARCANE_FLOW(Kind.PASSIVE, null, PlayerClass.SORCERER, 1, 2, CHAIN_LIGHTNING, 4),
    METEOR(Kind.SKILL, Skill.METEOR, PlayerClass.SORCERER, 2, 0, GLASS_CANNON, 10),
    ICE_ARMOR(Kind.SKILL, Skill.ICE_ARMOR, PlayerClass.SORCERER, 2, 1, BLINK, 10),
    THUNDERSTORM(Kind.SKILL, Skill.THUNDERSTORM, PlayerClass.SORCERER, 2, 2, ARCANE_FLOW, 10),
    TIME_WARP(Kind.PASSIVE, null, PlayerClass.SORCERER, 3, 0, null, 20),
    FIREBALL_BURN(FIREBALL),
    FIREBALL_SPLINTER(FIREBALL),
    FROST_NOVA_DEEP_FREEZE(FROST_NOVA),
    FROST_NOVA_ICE_MANTLE(FROST_NOVA),
    CHAIN_LIGHTNING_OVERCHARGE(CHAIN_LIGHTNING),
    CHAIN_LIGHTNING_FORK(CHAIN_LIGHTNING),
    BLINK_ARCANE_BLAST(BLINK),
    BLINK_PHASE_SHIFT(BLINK),
    METEOR_IMPACT(METEOR),
    METEOR_MOLTEN_GROUND(METEOR),
    ICE_ARMOR_ICICLES(ICE_ARMOR),
    ICE_ARMOR_THICK(ICE_ARMOR),
    THUNDERSTORM_ROAMING(THUNDERSTORM),
    THUNDERSTORM_CHAIN(THUNDERSTORM),
    // The keystones share one box in the tree, where you pick one of them
    UNSTABLE_MAGIC(Kind.KEYSTONE, null, PlayerClass.SORCERER, 3, 1, null, 20),
    ARCANE_ABUNDANCE(Kind.KEYSTONE, null, PlayerClass.SORCERER, 3, 1, null, 20),
    ARCANE_RHYTHM(Kind.KEYSTONE, null, PlayerClass.SORCERER, 3, 1, null, 20),
    // Ranger. Three branches: bow, powder and wilds
    MULTISHOT(Kind.SKILL, Skill.MULTISHOT, PlayerClass.RANGER, 0, 0, null, 0),
    SMOKE_BOMB(Kind.SKILL, Skill.SMOKE_BOMB, PlayerClass.RANGER, 0, 1, null, 0),
    WOLVES(Kind.SKILL, Skill.WOLVES, PlayerClass.RANGER, 0, 2, null, 0),
    MARKSMAN(Kind.PASSIVE, null, PlayerClass.RANGER, 1, 0, MULTISHOT, 4),
    EXPLOSIVE_ARROW(Kind.SKILL, Skill.EXPLOSIVE_ARROW, PlayerClass.RANGER, 1, 1, SMOKE_BOMB, 4),
    PACK_LEADER(Kind.PASSIVE, null, PlayerClass.RANGER, 1, 2, WOLVES, 4),
    ARROW_HURRICANE(Kind.SKILL, Skill.ARROW_HURRICANE, PlayerClass.RANGER, 2, 0, MARKSMAN, 10),
    ARROW_RAIN(Kind.SKILL, Skill.ARROW_RAIN, PlayerClass.RANGER, 2, 1, EXPLOSIVE_ARROW, 10),
    BEAR_TRAP(Kind.SKILL, Skill.BEAR_TRAP, PlayerClass.RANGER, 2, 2, PACK_LEADER, 10),
    AGILE(Kind.PASSIVE, null, PlayerClass.RANGER, 3, 0, null, 20),
    MULTISHOT_PIERCE(MULTISHOT),
    MULTISHOT_POISON(MULTISHOT),
    ARROW_HURRICANE_FIRE(ARROW_HURRICANE),
    ARROW_HURRICANE_LONG(ARROW_HURRICANE),
    SMOKE_BOMB_POISON(SMOKE_BOMB),
    SMOKE_BOMB_BLIND(SMOKE_BOMB),
    EXPLOSIVE_ARROW_STUN(EXPLOSIVE_ARROW),
    EXPLOSIVE_ARROW_BIG(EXPLOSIVE_ARROW),
    ARROW_RAIN_ICE(ARROW_RAIN),
    ARROW_RAIN_FOCUS(ARROW_RAIN),
    WOLVES_ALPHA(WOLVES),
    WOLVES_PACK(WOLVES),
    BEAR_TRAP_TRIPLE(BEAR_TRAP),
    BEAR_TRAP_POISON(BEAR_TRAP),
    SNIPER(Kind.KEYSTONE, null, PlayerClass.RANGER, 3, 1, null, 20),
    HUNTER_INSTINCT(Kind.KEYSTONE, null, PlayerClass.RANGER, 3, 1, null, 20),
    LIGHT_STEP(Kind.KEYSTONE, null, PlayerClass.RANGER, 3, 1, null, 20),
    // Necromancer. Three branches, bone, minions and blood, like the sorcerer's
    BONE_SPEAR(Kind.SKILL, Skill.BONE_SPEAR, PlayerClass.NECROMANCER, 0, 0, null, 0),
    RAISE_SKELETONS(Kind.SKILL, Skill.RAISE_SKELETONS, PlayerClass.NECROMANCER, 0, 1, null, 0),
    CURSE(Kind.SKILL, Skill.CURSE, PlayerClass.NECROMANCER, 0, 2, null, 0),
    BONE_ARMOR(Kind.SKILL, Skill.BONE_ARMOR, PlayerClass.NECROMANCER, 1, 0, BONE_SPEAR, 4),
    DEATH_LORD(Kind.PASSIVE, null, PlayerClass.NECROMANCER, 1, 1, RAISE_SKELETONS, 4),
    GRAVE_CHILL(Kind.PASSIVE, null, PlayerClass.NECROMANCER, 1, 2, CURSE, 4),
    BONE_PRISON(Kind.SKILL, Skill.BONE_PRISON, PlayerClass.NECROMANCER, 2, 0, BONE_ARMOR, 10),
    ARMY_OF_THE_DEAD(Kind.SKILL, Skill.ARMY_OF_THE_DEAD, PlayerClass.NECROMANCER, 2, 1, DEATH_LORD, 10),
    BLOOD_NOVA(Kind.SKILL, Skill.BLOOD_NOVA, PlayerClass.NECROMANCER, 2, 2, GRAVE_CHILL, 10),
    SOUL_HARVEST(Kind.PASSIVE, null, PlayerClass.NECROMANCER, 3, 0, null, 20),
    BONE_SPEAR_PIERCE(BONE_SPEAR),
    BONE_SPEAR_MARROW(BONE_SPEAR),
    RAISE_SKELETONS_GIANTS(RAISE_SKELETONS),
    RAISE_SKELETONS_HORDE(RAISE_SKELETONS),
    CURSE_WEAKEN(CURSE),
    CURSE_DECAY(CURSE),
    BONE_ARMOR_STORM(BONE_ARMOR),
    BONE_ARMOR_FORTIFIED(BONE_ARMOR),
    BONE_PRISON_CRUSH(BONE_PRISON),
    BONE_PRISON_LARGE(BONE_PRISON),
    ARMY_BLOOD_HARVEST(ARMY_OF_THE_DEAD),
    ARMY_RISEN(ARMY_OF_THE_DEAD),
    BLOOD_NOVA_THIRST(BLOOD_NOVA),
    BLOOD_NOVA_CURSE(BLOOD_NOVA),
    DEATH_PACT(Kind.KEYSTONE, null, PlayerClass.NECROMANCER, 3, 1, null, 20),
    SACRIFICE(Kind.KEYSTONE, null, PlayerClass.NECROMANCER, 3, 1, null, 20),
    BLOOD_PACT(Kind.KEYSTONE, null, PlayerClass.NECROMANCER, 3, 1, null, 20),
    // Paladin. Three branches: hammer, shield and light
    HOLY_HAMMER(Kind.SKILL, Skill.HOLY_HAMMER, PlayerClass.PALADIN, 0, 0, null, 0),
    SHIELD_BASH(Kind.SKILL, Skill.SHIELD_BASH, PlayerClass.PALADIN, 0, 1, null, 0),
    HOLY_LIGHT(Kind.SKILL, Skill.HOLY_LIGHT, PlayerClass.PALADIN, 0, 2, null, 0),
    ZEAL(Kind.PASSIVE, null, PlayerClass.PALADIN, 1, 0, HOLY_HAMMER, 4),
    CONSECRATION(Kind.SKILL, Skill.CONSECRATION, PlayerClass.PALADIN, 1, 1, SHIELD_BASH, 4),
    DEVOTION(Kind.PASSIVE, null, PlayerClass.PALADIN, 1, 2, HOLY_LIGHT, 4),
    SPEAR_OF_JUDGEMENT(Kind.SKILL, Skill.SPEAR_OF_JUDGEMENT, PlayerClass.PALADIN, 2, 0, ZEAL, 10),
    HEAVENS_JUDGEMENT(Kind.SKILL, Skill.HEAVENS_JUDGEMENT, PlayerClass.PALADIN, 2, 1, CONSECRATION, 10),
    HOLY_AURA(Kind.SKILL, Skill.HOLY_AURA, PlayerClass.PALADIN, 2, 2, DEVOTION, 10),
    RETRIBUTION(Kind.PASSIVE, null, PlayerClass.PALADIN, 3, 0, null, 20),
    HOLY_HAMMER_HEAVY(HOLY_HAMMER),
    HOLY_HAMMER_BIG(HOLY_HAMMER),
    SPEAR_FIRE(SPEAR_OF_JUDGEMENT),
    SPEAR_BLAST(SPEAR_OF_JUDGEMENT),
    SHIELD_BASH_WALL(SHIELD_BASH),
    SHIELD_BASH_LONG(SHIELD_BASH),
    CONSECRATION_BIG(CONSECRATION),
    CONSECRATION_HEALING(CONSECRATION),
    HEAVENS_FOCUS(HEAVENS_JUDGEMENT),
    HEAVENS_LIGHT(HEAVENS_JUDGEMENT),
    HOLY_LIGHT_INNER(HOLY_LIGHT),
    HOLY_LIGHT_RADIANT(HOLY_LIGHT),
    HOLY_AURA_MIGHT(HOLY_AURA),
    HOLY_AURA_WIDE(HOLY_AURA),
    MARTYR(Kind.KEYSTONE, null, PlayerClass.PALADIN, 3, 1, null, 20),
    HOLY_WRATH(Kind.KEYSTONE, null, PlayerClass.PALADIN, 3, 1, null, 20),
    UNBREAKABLE(Kind.KEYSTONE, null, PlayerClass.PALADIN, 3, 1, null, 20),
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

    // The rank a skill needs before its variants open
    public static final int VARIANT_RANK = 5;

    public enum Kind {
        SKILL, PASSIVE, VARIANT, KEYSTONE, SHARED
    }

    public final Kind kind;
    // The skill this upgrade unlocks and makes better, or null for everything else, also the variants
    public final Skill skill;
    // The class whose tree it is in, or null for the shared upgrades
    public final PlayerClass playerClass;
    // The row in the tree, from 0. A variant has the row and column of its skill
    public final int row;
    // The column in the tree, or -1 in the old trees, where the boxes of a row come in the order above
    public final int column;
    // The box that needs a point before this one opens (VARIANT_RANK for a variant's skill), or null
    public final Upgrade parent;
    // Points spent in the tree it takes to open it, or -1 in the old trees, where the row decides, see SkillTree
    public final int points;

    // In the old trees
    Upgrade(Skill skill, PlayerClass playerClass, int row) {
        this(skill != null ? Kind.SKILL : playerClass != null ? Kind.PASSIVE : Kind.SHARED, skill, playerClass, row, -1, null, -1);
    }

    Upgrade(Kind kind, Skill skill, PlayerClass playerClass, int row, int column, Upgrade parent, int points) {
        this.kind = kind;
        this.skill = skill;
        this.playerClass = playerClass;
        this.row = row;
        this.column = column;
        this.parent = parent;
        this.points = points;
    }

    // A variant of the skill
    Upgrade(Upgrade skill) {
        this(Kind.VARIANT, null, skill.playerClass, skill.row, skill.column, skill, 0);
    }

    // Names and descriptions are in the language files, under upgrade.<name>
    public MutableComponent displayName() {
        return Text.tr("upgrade." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent description() {
        return Text.tr("upgrade." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    public boolean isPassive() {
        return kind == Kind.PASSIVE;
    }

    // Variants and keystones, where you pick one, see sameChoice
    public boolean isChoice() {
        return kind == Kind.VARIANT || kind == Kind.KEYSTONE;
    }

    // Whether only one of the two can have a point: the variants of the same skill, or the keystones of the same class
    public boolean sameChoice(Upgrade other) {
        return isChoice() && other.kind == kind && (kind == Kind.VARIANT ? other.parent == parent : other.playerClass == playerClass);
    }

    public int maxRank() {
        return switch (kind) {
            case SKILL -> SKILL_MAX_RANK;
            case PASSIVE -> PASSIVE_MAX_RANK;
            case SHARED -> SHARED_MAX_RANK;
            case VARIANT, KEYSTONE -> 1;
        };
    }

    // The variants of this skill. Empty for the skills in the old trees and for everything else
    public List<Upgrade> variants() {
        return Stream.of(values()).filter(upgrade -> upgrade.kind == Kind.VARIANT && upgrade.parent == this).toList();
    }

    // The class's keystones, empty in the old trees
    public static List<Upgrade> keystones(PlayerClass playerClass) {
        return Stream.of(values()).filter(upgrade -> upgrade.kind == Kind.KEYSTONE && upgrade.playerClass == playerClass).toList();
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
