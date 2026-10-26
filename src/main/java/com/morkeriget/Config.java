package com.morkeriget;

import net.neoforged.neoforge.common.ModConfigSpec;

// Settings in config/morkeriget-common.toml. The game reloads the file when it is saved, but stats are given
// to monsters when they spawn, so changes only affect new monsters. The comments are what the player sees in the file
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue AFFIX_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SET_CHANCE_BOSS;
    public static final ModConfigSpec.DoubleValue SET_CHANCE_ELITE;
    public static final ModConfigSpec.DoubleValue MYTHIC_CHANCE_BOSS;
    public static final ModConfigSpec.DoubleValue MYTHIC_CHANCE_RIFT;
    public static final ModConfigSpec.DoubleValue ELITE_CHANCE;
    public static final ModConfigSpec.DoubleValue SECOND_MODIFIER_CHANCE;
    public static final ModConfigSpec.DoubleValue ELITE_HEALTH;
    public static final ModConfigSpec.DoubleValue ELITE_DAMAGE;
    public static final ModConfigSpec.IntValue PACK_MIN_SIZE;
    public static final ModConfigSpec.IntValue PACK_MAX_SIZE;
    public static final ModConfigSpec.DoubleValue BOSS_CHANCE;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE;
    public static final ModConfigSpec.IntValue WORLD_BOSS_INTERVAL;
    public static final ModConfigSpec.DoubleValue WORLD_BOSS_HEALTH;
    public static final ModConfigSpec.DoubleValue WORLD_BOSS_MYTHIC_CHANCE;
    public static final ModConfigSpec.DoubleValue MANA_REGEN;
    public static final ModConfigSpec.DoubleValue WHIRLWIND_MANA;
    public static final ModConfigSpec.DoubleValue WHIRLWIND_COOLDOWN;
    public static final ModConfigSpec.DoubleValue FROST_NOVA_MANA;
    public static final ModConfigSpec.DoubleValue FROST_NOVA_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BLINK_MANA;
    public static final ModConfigSpec.DoubleValue BLINK_COOLDOWN;
    public static final ModConfigSpec.DoubleValue CHAIN_LIGHTNING_MANA;
    public static final ModConfigSpec.DoubleValue CHAIN_LIGHTNING_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BATTLE_CRY_MANA;
    public static final ModConfigSpec.DoubleValue BATTLE_CRY_COOLDOWN;
    public static final ModConfigSpec.DoubleValue LEAP_MANA;
    public static final ModConfigSpec.DoubleValue LEAP_COOLDOWN;
    public static final ModConfigSpec.DoubleValue FRENZY_MANA;
    public static final ModConfigSpec.DoubleValue FRENZY_COOLDOWN;
    public static final ModConfigSpec.DoubleValue FIREBALL_MANA;
    public static final ModConfigSpec.DoubleValue FIREBALL_COOLDOWN;
    public static final ModConfigSpec.DoubleValue MULTISHOT_MANA;
    public static final ModConfigSpec.DoubleValue MULTISHOT_COOLDOWN;
    public static final ModConfigSpec.DoubleValue ARROW_RAIN_MANA;
    public static final ModConfigSpec.DoubleValue ARROW_RAIN_COOLDOWN;
    public static final ModConfigSpec.DoubleValue SMOKE_BOMB_MANA;
    public static final ModConfigSpec.DoubleValue SMOKE_BOMB_COOLDOWN;
    public static final ModConfigSpec.DoubleValue WOLVES_MANA;
    public static final ModConfigSpec.DoubleValue WOLVES_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BONE_SPEAR_MANA;
    public static final ModConfigSpec.DoubleValue BONE_SPEAR_COOLDOWN;
    public static final ModConfigSpec.DoubleValue RAISE_SKELETONS_MANA;
    public static final ModConfigSpec.DoubleValue RAISE_SKELETONS_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BONE_ARMOR_MANA;
    public static final ModConfigSpec.DoubleValue BONE_ARMOR_COOLDOWN;
    public static final ModConfigSpec.DoubleValue CURSE_MANA;
    public static final ModConfigSpec.DoubleValue CURSE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_MANA;
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue RUSH_MANA;
    public static final ModConfigSpec.DoubleValue RUSH_COOLDOWN;
    public static final ModConfigSpec.DoubleValue METEOR_MANA;
    public static final ModConfigSpec.DoubleValue METEOR_COOLDOWN;
    public static final ModConfigSpec.DoubleValue ICE_ARMOR_MANA;
    public static final ModConfigSpec.DoubleValue ICE_ARMOR_COOLDOWN;
    public static final ModConfigSpec.DoubleValue EXPLOSIVE_ARROW_MANA;
    public static final ModConfigSpec.DoubleValue EXPLOSIVE_ARROW_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BEAR_TRAP_MANA;
    public static final ModConfigSpec.DoubleValue BEAR_TRAP_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BLOOD_NOVA_MANA;
    public static final ModConfigSpec.DoubleValue BLOOD_NOVA_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BONE_PRISON_MANA;
    public static final ModConfigSpec.DoubleValue BONE_PRISON_COOLDOWN;
    public static final ModConfigSpec.DoubleValue THUNDERSTORM_MANA;
    public static final ModConfigSpec.DoubleValue THUNDERSTORM_COOLDOWN;
    public static final ModConfigSpec.DoubleValue DOOM_SLAM_MANA;
    public static final ModConfigSpec.DoubleValue DOOM_SLAM_COOLDOWN;
    public static final ModConfigSpec.DoubleValue ARROW_HURRICANE_MANA;
    public static final ModConfigSpec.DoubleValue ARROW_HURRICANE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue ARMY_OF_THE_DEAD_MANA;
    public static final ModConfigSpec.DoubleValue ARMY_OF_THE_DEAD_COOLDOWN;
    public static final ModConfigSpec.DoubleValue HEALTH_POTION_HEAL;
    public static final ModConfigSpec.DoubleValue HEALTH_POTION_COOLDOWN;
    public static final ModConfigSpec.DoubleValue MANA_POTION_MANA;
    public static final ModConfigSpec.DoubleValue MANA_POTION_COOLDOWN;
    public static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue MONSTER_LEVELS;
    public static final ModConfigSpec.IntValue MONSTER_LEVEL_SPREAD;
    public static final ModConfigSpec.DoubleValue HEALTH_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue DAMAGE_PER_LEVEL;

    static {
        BUILDER.push("loot");
        DROP_CHANCE = BUILDER.comment("Chance that a normal monster drops an item (0.25 = 25%)")
                .defineInRange("dropChance", 0.25, 0, 1);
        AFFIX_PER_LEVEL = BUILDER.comment("How much stronger bonuses get per item level (0.05 = +5%, so level 21 is twice as strong).",
                        "This counts for damage, armor and toughness. Health grows half as fast. Percent bonuses like speed,",
                        "attack speed, life steal and thorns only grow a little: up to 50% more at level 100")
                .defineInRange("affixScalingPerLevel", 0.05, 0, 1);
        SET_CHANCE_BOSS = BUILDER.comment("Chance that a boss's legendary item is a set item instead (0.3 = 30%)")
                .defineInRange("setChanceBoss", 0.3, 0, 1);
        SET_CHANCE_ELITE = BUILDER.comment("Chance that an elite drops a set item instead of its normal item (0.02 = 2%)")
                .defineInRange("setChanceElite", 0.02, 0, 1);
        MYTHIC_CHANCE_BOSS = BUILDER.comment("Chance that a boss drops a mythic item instead of its legendary one (0.03 = 3%)")
                .defineInRange("mythicChanceBoss", 0.03, 0, 1);
        MYTHIC_CHANCE_RIFT = BUILDER.comment("Chance per rift tier for a mythic item when a rift is closed (0.01 = 1%, so rift X gives 10%)")
                .defineInRange("mythicChancePerRiftTier", 0.01, 0, 1);
        BUILDER.pop();

        BUILDER.push("elites");
        ELITE_CHANCE = BUILDER.comment("Chance that a monster spawns as an elite (0.05 = 5%)")
                .defineInRange("eliteChance", 0.05, 0, 1);
        SECOND_MODIFIER_CHANCE = BUILDER.comment("Chance that an elite gets two modifiers instead of one")
                .defineInRange("secondModifierChance", 0.3, 0, 1);
        ELITE_HEALTH = BUILDER.comment("How many times more health an elite has than a normal monster")
                .defineInRange("healthMultiplier", 3.0, 1, 100);
        ELITE_DAMAGE = BUILDER.comment("How many times more damage an elite does than a normal monster")
                .defineInRange("damageMultiplier", 1.35, 1, 100);
        PACK_MIN_SIZE = BUILDER.comment("The fewest monsters that come with an elite out in the world. Set both to 0 for no packs")
                .defineInRange("packMinSize", 2, 0, 10);
        PACK_MAX_SIZE = BUILDER.comment("The most monsters that come with an elite out in the world")
                .defineInRange("packMaxSize", 4, 0, 10);
        BUILDER.pop();

        BUILDER.push("bosses");
        BOSS_CHANCE = BUILDER.comment("Chance that a monster spawns as a boss (0.005 = 0.5%). Set to 1 to test")
                .defineInRange("bossChance", 0.005, 0, 1);
        BOSS_HEALTH = BUILDER.comment("How many times more health a boss has than a normal monster")
                .defineInRange("healthMultiplier", 4.0, 1, 100);
        BOSS_DAMAGE = BUILDER.comment("How many times more damage a boss does than a normal monster")
                .defineInRange("damageMultiplier", 2.0, 1, 100);
        BUILDER.pop();

        BUILDER.push("worldBoss");
        WORLD_BOSS_INTERVAL = BUILDER.comment("Minutes between world bosses. Players are told 5 minutes before it wakes up")
                .defineInRange("intervalMinutes", 40, 6, 1440);
        WORLD_BOSS_HEALTH = BUILDER.comment("How many times more health a world boss has than a normal boss")
                .defineInRange("healthMultiplier", 8.0, 1, 1000);
        WORLD_BOSS_MYTHIC_CHANCE = BUILDER.comment("Chance that each player who took part gets a mythic item (0.15 = 15%)")
                .defineInRange("mythicChance", 0.15, 0, 1);
        BUILDER.pop();

        BUILDER.push("skills");
        MANA_REGEN = BUILDER.comment("How much mana you get back per second (max is 100)")
                .defineInRange("manaRegenPerSecond", 5.0, 0, 100);
        WHIRLWIND_MANA = BUILDER.comment("Whirlwind: mana cost").defineInRange("whirlwindMana", 30.0, 0, 100);
        WHIRLWIND_COOLDOWN = BUILDER.comment("Whirlwind: cooldown in seconds").defineInRange("whirlwindCooldown", 2.0, 0, 600);
        FROST_NOVA_MANA = BUILDER.comment("Frost Nova: mana cost").defineInRange("frostNovaMana", 40.0, 0, 100);
        FROST_NOVA_COOLDOWN = BUILDER.comment("Frost Nova: cooldown in seconds").defineInRange("frostNovaCooldown", 8.0, 0, 600);
        BLINK_MANA = BUILDER.comment("Blink: mana cost").defineInRange("blinkMana", 20.0, 0, 100);
        BLINK_COOLDOWN = BUILDER.comment("Blink: cooldown in seconds").defineInRange("blinkCooldown", 3.0, 0, 600);
        CHAIN_LIGHTNING_MANA = BUILDER.comment("Chain Lightning: mana cost").defineInRange("chainLightningMana", 35.0, 0, 100);
        CHAIN_LIGHTNING_COOLDOWN = BUILDER.comment("Chain Lightning: cooldown in seconds").defineInRange("chainLightningCooldown", 4.0, 0, 600);
        BATTLE_CRY_MANA = BUILDER.comment("Battle Cry: mana cost").defineInRange("battleCryMana", 50.0, 0, 100);
        BATTLE_CRY_COOLDOWN = BUILDER.comment("Battle Cry: cooldown in seconds").defineInRange("battleCryCooldown", 20.0, 0, 600);
        LEAP_MANA = BUILDER.comment("Leap (barbarian): mana cost").defineInRange("leapMana", 25.0, 0, 100);
        LEAP_COOLDOWN = BUILDER.comment("Leap (barbarian): cooldown in seconds").defineInRange("leapCooldown", 6.0, 0, 600);
        FRENZY_MANA = BUILDER.comment("Frenzy (barbarian): mana cost").defineInRange("frenzyMana", 40.0, 0, 100);
        FRENZY_COOLDOWN = BUILDER.comment("Frenzy (barbarian): cooldown in seconds").defineInRange("frenzyCooldown", 15.0, 0, 600);
        FIREBALL_MANA = BUILDER.comment("Fireball (sorcerer): mana cost").defineInRange("fireballMana", 30.0, 0, 100);
        FIREBALL_COOLDOWN = BUILDER.comment("Fireball (sorcerer): cooldown in seconds").defineInRange("fireballCooldown", 3.0, 0, 600);
        MULTISHOT_MANA = BUILDER.comment("Multishot (ranger): mana cost").defineInRange("multishotMana", 25.0, 0, 100);
        MULTISHOT_COOLDOWN = BUILDER.comment("Multishot (ranger): cooldown in seconds").defineInRange("multishotCooldown", 3.0, 0, 600);
        ARROW_RAIN_MANA = BUILDER.comment("Arrow Rain (ranger): mana cost").defineInRange("arrowRainMana", 45.0, 0, 100);
        ARROW_RAIN_COOLDOWN = BUILDER.comment("Arrow Rain (ranger): cooldown in seconds").defineInRange("arrowRainCooldown", 10.0, 0, 600);
        SMOKE_BOMB_MANA = BUILDER.comment("Smoke Bomb (ranger): mana cost").defineInRange("smokeBombMana", 30.0, 0, 100);
        SMOKE_BOMB_COOLDOWN = BUILDER.comment("Smoke Bomb (ranger): cooldown in seconds").defineInRange("smokeBombCooldown", 15.0, 0, 600);
        WOLVES_MANA = BUILDER.comment("Wolf Pack (ranger): mana cost").defineInRange("wolvesMana", 60.0, 0, 100);
        WOLVES_COOLDOWN = BUILDER.comment("Wolf Pack (ranger): cooldown in seconds").defineInRange("wolvesCooldown", 30.0, 0, 600);
        BONE_SPEAR_MANA = BUILDER.comment("Bone Spear (necromancer): mana cost").defineInRange("boneSpearMana", 20.0, 0, 100);
        BONE_SPEAR_COOLDOWN = BUILDER.comment("Bone Spear (necromancer): cooldown in seconds").defineInRange("boneSpearCooldown", 1.5, 0, 600);
        RAISE_SKELETONS_MANA = BUILDER.comment("Raise Skeletons (necromancer): mana cost").defineInRange("raiseSkeletonsMana", 50.0, 0, 100);
        RAISE_SKELETONS_COOLDOWN = BUILDER.comment("Raise Skeletons (necromancer): cooldown in seconds").defineInRange("raiseSkeletonsCooldown", 8.0, 0, 600);
        BONE_ARMOR_MANA = BUILDER.comment("Bone Armor (necromancer): mana cost").defineInRange("boneArmorMana", 35.0, 0, 100);
        BONE_ARMOR_COOLDOWN = BUILDER.comment("Bone Armor (necromancer): cooldown in seconds").defineInRange("boneArmorCooldown", 18.0, 0, 600);
        CURSE_MANA = BUILDER.comment("Curse (necromancer): mana cost").defineInRange("curseMana", 30.0, 0, 100);
        CURSE_COOLDOWN = BUILDER.comment("Curse (necromancer): cooldown in seconds").defineInRange("curseCooldown", 12.0, 0, 600);
        EARTHQUAKE_MANA = BUILDER.comment("Earthquake (barbarian): mana cost").defineInRange("earthquakeMana", 45.0, 0, 100);
        EARTHQUAKE_COOLDOWN = BUILDER.comment("Earthquake (barbarian): cooldown in seconds").defineInRange("earthquakeCooldown", 12.0, 0, 600);
        RUSH_MANA = BUILDER.comment("Rush (barbarian): mana cost").defineInRange("rushMana", 25.0, 0, 100);
        RUSH_COOLDOWN = BUILDER.comment("Rush (barbarian): cooldown in seconds").defineInRange("rushCooldown", 5.0, 0, 600);
        METEOR_MANA = BUILDER.comment("Meteor (sorcerer): mana cost").defineInRange("meteorMana", 50.0, 0, 100);
        METEOR_COOLDOWN = BUILDER.comment("Meteor (sorcerer): cooldown in seconds").defineInRange("meteorCooldown", 8.0, 0, 600);
        ICE_ARMOR_MANA = BUILDER.comment("Ice Armor (sorcerer): mana cost").defineInRange("iceArmorMana", 35.0, 0, 100);
        ICE_ARMOR_COOLDOWN = BUILDER.comment("Ice Armor (sorcerer): cooldown in seconds").defineInRange("iceArmorCooldown", 20.0, 0, 600);
        EXPLOSIVE_ARROW_MANA = BUILDER.comment("Explosive Arrow (ranger): mana cost").defineInRange("explosiveArrowMana", 25.0, 0, 100);
        EXPLOSIVE_ARROW_COOLDOWN = BUILDER.comment("Explosive Arrow (ranger): cooldown in seconds").defineInRange("explosiveArrowCooldown", 4.0, 0, 600);
        BEAR_TRAP_MANA = BUILDER.comment("Bear Trap (ranger): mana cost").defineInRange("bearTrapMana", 30.0, 0, 100);
        BEAR_TRAP_COOLDOWN = BUILDER.comment("Bear Trap (ranger): cooldown in seconds").defineInRange("bearTrapCooldown", 8.0, 0, 600);
        BLOOD_NOVA_MANA = BUILDER.comment("Blood Nova (necromancer): mana cost").defineInRange("bloodNovaMana", 35.0, 0, 100);
        BLOOD_NOVA_COOLDOWN = BUILDER.comment("Blood Nova (necromancer): cooldown in seconds").defineInRange("bloodNovaCooldown", 6.0, 0, 600);
        BONE_PRISON_MANA = BUILDER.comment("Bone Prison (necromancer): mana cost").defineInRange("bonePrisonMana", 40.0, 0, 100);
        BONE_PRISON_COOLDOWN = BUILDER.comment("Bone Prison (necromancer): cooldown in seconds").defineInRange("bonePrisonCooldown", 14.0, 0, 600);
        THUNDERSTORM_MANA = BUILDER.comment("Thunderstorm (sorcerer): mana cost").defineInRange("thunderstormMana", 60.0, 0, 100);
        THUNDERSTORM_COOLDOWN = BUILDER.comment("Thunderstorm (sorcerer): cooldown in seconds").defineInRange("thunderstormCooldown", 16.0, 0, 600);
        DOOM_SLAM_MANA = BUILDER.comment("Doom Slam (barbarian): mana cost").defineInRange("doomSlamMana", 60.0, 0, 100);
        DOOM_SLAM_COOLDOWN = BUILDER.comment("Doom Slam (barbarian): cooldown in seconds").defineInRange("doomSlamCooldown", 16.0, 0, 600);
        ARROW_HURRICANE_MANA = BUILDER.comment("Arrow Hurricane (ranger): mana cost").defineInRange("arrowHurricaneMana", 60.0, 0, 100);
        ARROW_HURRICANE_COOLDOWN = BUILDER.comment("Arrow Hurricane (ranger): cooldown in seconds").defineInRange("arrowHurricaneCooldown", 18.0, 0, 600);
        ARMY_OF_THE_DEAD_MANA = BUILDER.comment("Army of the Dead (necromancer): mana cost").defineInRange("armyOfTheDeadMana", 60.0, 0, 100);
        ARMY_OF_THE_DEAD_COOLDOWN = BUILDER.comment("Army of the Dead (necromancer): cooldown in seconds").defineInRange("armyOfTheDeadCooldown", 16.0, 0, 600);
        HEALTH_POTION_HEAL = BUILDER.comment("Health Potion: how much of your max health it gives back (0.4 = 40%)")
                .defineInRange("healthPotionHeal", 0.4, 0, 1);
        HEALTH_POTION_COOLDOWN = BUILDER.comment("Health Potion: cooldown in seconds").defineInRange("healthPotionCooldown", 20.0, 0, 600);
        MANA_POTION_MANA = BUILDER.comment("Mana Potion: how much mana it gives (max is 100)")
                .defineInRange("manaPotionMana", 50.0, 0, 100);
        MANA_POTION_COOLDOWN = BUILDER.comment("Mana Potion: cooldown in seconds").defineInRange("manaPotionCooldown", 20.0, 0, 600);
        BUILDER.pop();

        BUILDER.push("levels");
        XP_MULTIPLIER = BUILDER.comment("Multiplies all XP from monsters by this number. 2 = levels up twice as fast")
                .defineInRange("xpMultiplier", 1.0, 0, 100);
        BUILDER.pop();

        BUILDER.push("monsterLevels");
        MONSTER_LEVELS = BUILDER.comment("Whether monsters get a level based on the nearest player's level")
                .define("enabled", true);
        MONSTER_LEVEL_SPREAD = BUILDER.comment("How many levels a monster can be above or below the player")
                .defineInRange("levelSpread", 1, 0, 10);
        HEALTH_PER_LEVEL = BUILDER.comment("Extra health per level above 1 (0.08 = +8%, so level 11 has 80% more health)")
                .defineInRange("healthPerLevel", 0.08, 0, 10);
        DAMAGE_PER_LEVEL = BUILDER.comment("Extra damage per level above 1 (0.07 = +7%)")
                .defineInRange("damagePerLevel", 0.07, 0, 10);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
