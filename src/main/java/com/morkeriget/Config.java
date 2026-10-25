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
        DROP_CHANCE = BUILDER.comment("Chance for at et almindeligt monster dropper en genstand (0.25 = 25 %)")
                .defineInRange("dropChance", 0.25, 0, 1);
        AFFIX_PER_LEVEL = BUILDER.comment("Hvor meget stærkere bonusser bliver pr. genstandslevel (0.05 = +5 %, så level 21 er dobbelt så stærkt).",
                        "Det gælder skade, rustning og hårdhed. Liv vokser halvt så hurtigt. Procent-bonusser som fart,",
                        "angrebshastighed, livsstjæl og torne vokser kun lidt: op til 50 % mere ved level 100")
                .defineInRange("affixScalingPerLevel", 0.05, 0, 1);
        SET_CHANCE_BOSS = BUILDER.comment("Chance for at en boss' legendariske genstand i stedet er en sæt-genstand (0.3 = 30 %)")
                .defineInRange("setChanceBoss", 0.3, 0, 1);
        SET_CHANCE_ELITE = BUILDER.comment("Chance for at en elite dropper en sæt-genstand i stedet for sin normale genstand (0.02 = 2 %)")
                .defineInRange("setChanceElite", 0.02, 0, 1);
        MYTHIC_CHANCE_BOSS = BUILDER.comment("Chance for at en boss dropper en mytisk genstand i stedet for sin legendariske (0.03 = 3 %)")
                .defineInRange("mythicChanceBoss", 0.03, 0, 1);
        MYTHIC_CHANCE_RIFT = BUILDER.comment("Chance pr. rift-niveau for en mytisk genstand, når en rift lukkes (0.01 = 1 %, så rift X giver 10 %)")
                .defineInRange("mythicChancePerRiftTier", 0.01, 0, 1);
        BUILDER.pop();

        BUILDER.push("elites");
        ELITE_CHANCE = BUILDER.comment("Chance for at et monster spawner som elite (0.05 = 5 %)")
                .defineInRange("eliteChance", 0.05, 0, 1);
        SECOND_MODIFIER_CHANCE = BUILDER.comment("Chance for at en elite får to modifiers i stedet for én")
                .defineInRange("secondModifierChance", 0.3, 0, 1);
        ELITE_HEALTH = BUILDER.comment("Hvor mange gange mere liv en elite har end et almindeligt monster")
                .defineInRange("healthMultiplier", 3.0, 1, 100);
        ELITE_DAMAGE = BUILDER.comment("Hvor mange gange mere skade en elite gør end et almindeligt monster")
                .defineInRange("damageMultiplier", 1.35, 1, 100);
        PACK_MIN_SIZE = BUILDER.comment("Hvor mange monstre der mindst følger med en elite ude i verden. Sæt begge til 0 for ingen flokke")
                .defineInRange("packMinSize", 2, 0, 10);
        PACK_MAX_SIZE = BUILDER.comment("Hvor mange monstre der højst følger med en elite ude i verden")
                .defineInRange("packMaxSize", 4, 0, 10);
        BUILDER.pop();

        BUILDER.push("bosses");
        BOSS_CHANCE = BUILDER.comment("Chance for at et monster spawner som boss (0.005 = 0,5 %). Sæt til 1 for at teste")
                .defineInRange("bossChance", 0.005, 0, 1);
        BOSS_HEALTH = BUILDER.comment("Hvor mange gange mere liv en boss har end et almindeligt monster")
                .defineInRange("healthMultiplier", 4.0, 1, 100);
        BOSS_DAMAGE = BUILDER.comment("Hvor mange gange mere skade en boss gør end et almindeligt monster")
                .defineInRange("damageMultiplier", 2.0, 1, 100);
        BUILDER.pop();

        BUILDER.push("worldBoss");
        WORLD_BOSS_INTERVAL = BUILDER.comment("Minutter mellem verdensbosser. Spillerne får besked 5 minutter før den vågner")
                .defineInRange("intervalMinutes", 40, 6, 1440);
        WORLD_BOSS_HEALTH = BUILDER.comment("Hvor mange gange mere liv en verdensboss har end en almindelig boss")
                .defineInRange("healthMultiplier", 8.0, 1, 1000);
        WORLD_BOSS_MYTHIC_CHANCE = BUILDER.comment("Chance for at hver spiller, der var med, får en mytisk genstand (0.15 = 15 %)")
                .defineInRange("mythicChance", 0.15, 0, 1);
        BUILDER.pop();

        BUILDER.push("skills");
        MANA_REGEN = BUILDER.comment("Hvor meget mana du får igen pr. sekund (max er 100)")
                .defineInRange("manaRegenPerSecond", 5.0, 0, 100);
        WHIRLWIND_MANA = BUILDER.comment("Hvirvelvind: mana det koster").defineInRange("whirlwindMana", 30.0, 0, 100);
        WHIRLWIND_COOLDOWN = BUILDER.comment("Hvirvelvind: cooldown i sekunder").defineInRange("whirlwindCooldown", 2.0, 0, 600);
        FROST_NOVA_MANA = BUILDER.comment("Frostnova: mana det koster").defineInRange("frostNovaMana", 40.0, 0, 100);
        FROST_NOVA_COOLDOWN = BUILDER.comment("Frostnova: cooldown i sekunder").defineInRange("frostNovaCooldown", 8.0, 0, 600);
        BLINK_MANA = BUILDER.comment("Blink: mana det koster").defineInRange("blinkMana", 20.0, 0, 100);
        BLINK_COOLDOWN = BUILDER.comment("Blink: cooldown i sekunder").defineInRange("blinkCooldown", 3.0, 0, 600);
        CHAIN_LIGHTNING_MANA = BUILDER.comment("Kædelyn: mana det koster").defineInRange("chainLightningMana", 35.0, 0, 100);
        CHAIN_LIGHTNING_COOLDOWN = BUILDER.comment("Kædelyn: cooldown i sekunder").defineInRange("chainLightningCooldown", 4.0, 0, 600);
        BATTLE_CRY_MANA = BUILDER.comment("Krigsråb: mana det koster").defineInRange("battleCryMana", 50.0, 0, 100);
        BATTLE_CRY_COOLDOWN = BUILDER.comment("Krigsråb: cooldown i sekunder").defineInRange("battleCryCooldown", 20.0, 0, 600);
        LEAP_MANA = BUILDER.comment("Spring (barbar): mana det koster").defineInRange("leapMana", 25.0, 0, 100);
        LEAP_COOLDOWN = BUILDER.comment("Spring (barbar): cooldown i sekunder").defineInRange("leapCooldown", 6.0, 0, 600);
        FRENZY_MANA = BUILDER.comment("Raseri (barbar): mana det koster").defineInRange("frenzyMana", 40.0, 0, 100);
        FRENZY_COOLDOWN = BUILDER.comment("Raseri (barbar): cooldown i sekunder").defineInRange("frenzyCooldown", 15.0, 0, 600);
        FIREBALL_MANA = BUILDER.comment("Ildkugle (troldmand): mana det koster").defineInRange("fireballMana", 30.0, 0, 100);
        FIREBALL_COOLDOWN = BUILDER.comment("Ildkugle (troldmand): cooldown i sekunder").defineInRange("fireballCooldown", 3.0, 0, 600);
        MULTISHOT_MANA = BUILDER.comment("Multiskud (jæger): mana det koster").defineInRange("multishotMana", 25.0, 0, 100);
        MULTISHOT_COOLDOWN = BUILDER.comment("Multiskud (jæger): cooldown i sekunder").defineInRange("multishotCooldown", 3.0, 0, 600);
        ARROW_RAIN_MANA = BUILDER.comment("Pileregn (jæger): mana det koster").defineInRange("arrowRainMana", 45.0, 0, 100);
        ARROW_RAIN_COOLDOWN = BUILDER.comment("Pileregn (jæger): cooldown i sekunder").defineInRange("arrowRainCooldown", 10.0, 0, 600);
        SMOKE_BOMB_MANA = BUILDER.comment("Røgbombe (jæger): mana det koster").defineInRange("smokeBombMana", 30.0, 0, 100);
        SMOKE_BOMB_COOLDOWN = BUILDER.comment("Røgbombe (jæger): cooldown i sekunder").defineInRange("smokeBombCooldown", 15.0, 0, 600);
        WOLVES_MANA = BUILDER.comment("Ulveflok (jæger): mana det koster").defineInRange("wolvesMana", 60.0, 0, 100);
        WOLVES_COOLDOWN = BUILDER.comment("Ulveflok (jæger): cooldown i sekunder").defineInRange("wolvesCooldown", 30.0, 0, 600);
        BONE_SPEAR_MANA = BUILDER.comment("Knoglespyd (nekromantiker): mana det koster").defineInRange("boneSpearMana", 20.0, 0, 100);
        BONE_SPEAR_COOLDOWN = BUILDER.comment("Knoglespyd (nekromantiker): cooldown i sekunder").defineInRange("boneSpearCooldown", 1.5, 0, 600);
        RAISE_SKELETONS_MANA = BUILDER.comment("Rejs skeletter (nekromantiker): mana det koster").defineInRange("raiseSkeletonsMana", 50.0, 0, 100);
        RAISE_SKELETONS_COOLDOWN = BUILDER.comment("Rejs skeletter (nekromantiker): cooldown i sekunder").defineInRange("raiseSkeletonsCooldown", 8.0, 0, 600);
        BONE_ARMOR_MANA = BUILDER.comment("Knoglerustning (nekromantiker): mana det koster").defineInRange("boneArmorMana", 35.0, 0, 100);
        BONE_ARMOR_COOLDOWN = BUILDER.comment("Knoglerustning (nekromantiker): cooldown i sekunder").defineInRange("boneArmorCooldown", 18.0, 0, 600);
        CURSE_MANA = BUILDER.comment("Forbandelse (nekromantiker): mana det koster").defineInRange("curseMana", 30.0, 0, 100);
        CURSE_COOLDOWN = BUILDER.comment("Forbandelse (nekromantiker): cooldown i sekunder").defineInRange("curseCooldown", 12.0, 0, 600);
        EARTHQUAKE_MANA = BUILDER.comment("Jordskælv (barbar): mana det koster").defineInRange("earthquakeMana", 45.0, 0, 100);
        EARTHQUAKE_COOLDOWN = BUILDER.comment("Jordskælv (barbar): cooldown i sekunder").defineInRange("earthquakeCooldown", 12.0, 0, 600);
        RUSH_MANA = BUILDER.comment("Stormløb (barbar): mana det koster").defineInRange("rushMana", 25.0, 0, 100);
        RUSH_COOLDOWN = BUILDER.comment("Stormløb (barbar): cooldown i sekunder").defineInRange("rushCooldown", 5.0, 0, 600);
        METEOR_MANA = BUILDER.comment("Meteor (troldmand): mana det koster").defineInRange("meteorMana", 50.0, 0, 100);
        METEOR_COOLDOWN = BUILDER.comment("Meteor (troldmand): cooldown i sekunder").defineInRange("meteorCooldown", 8.0, 0, 600);
        ICE_ARMOR_MANA = BUILDER.comment("Isrustning (troldmand): mana det koster").defineInRange("iceArmorMana", 35.0, 0, 100);
        ICE_ARMOR_COOLDOWN = BUILDER.comment("Isrustning (troldmand): cooldown i sekunder").defineInRange("iceArmorCooldown", 20.0, 0, 600);
        EXPLOSIVE_ARROW_MANA = BUILDER.comment("Eksplosiv pil (jæger): mana det koster").defineInRange("explosiveArrowMana", 25.0, 0, 100);
        EXPLOSIVE_ARROW_COOLDOWN = BUILDER.comment("Eksplosiv pil (jæger): cooldown i sekunder").defineInRange("explosiveArrowCooldown", 4.0, 0, 600);
        BEAR_TRAP_MANA = BUILDER.comment("Bjørnefælde (jæger): mana det koster").defineInRange("bearTrapMana", 30.0, 0, 100);
        BEAR_TRAP_COOLDOWN = BUILDER.comment("Bjørnefælde (jæger): cooldown i sekunder").defineInRange("bearTrapCooldown", 8.0, 0, 600);
        BLOOD_NOVA_MANA = BUILDER.comment("Blodnova (nekromantiker): mana det koster").defineInRange("bloodNovaMana", 35.0, 0, 100);
        BLOOD_NOVA_COOLDOWN = BUILDER.comment("Blodnova (nekromantiker): cooldown i sekunder").defineInRange("bloodNovaCooldown", 6.0, 0, 600);
        BONE_PRISON_MANA = BUILDER.comment("Knoglefængsel (nekromantiker): mana det koster").defineInRange("bonePrisonMana", 40.0, 0, 100);
        BONE_PRISON_COOLDOWN = BUILDER.comment("Knoglefængsel (nekromantiker): cooldown i sekunder").defineInRange("bonePrisonCooldown", 14.0, 0, 600);
        THUNDERSTORM_MANA = BUILDER.comment("Tordenvejr (troldmand): mana det koster").defineInRange("thunderstormMana", 60.0, 0, 100);
        THUNDERSTORM_COOLDOWN = BUILDER.comment("Tordenvejr (troldmand): cooldown i sekunder").defineInRange("thunderstormCooldown", 16.0, 0, 600);
        DOOM_SLAM_MANA = BUILDER.comment("Dommedagsslag (barbar): mana det koster").defineInRange("doomSlamMana", 60.0, 0, 100);
        DOOM_SLAM_COOLDOWN = BUILDER.comment("Dommedagsslag (barbar): cooldown i sekunder").defineInRange("doomSlamCooldown", 16.0, 0, 600);
        ARROW_HURRICANE_MANA = BUILDER.comment("Pileorkan (jæger): mana det koster").defineInRange("arrowHurricaneMana", 60.0, 0, 100);
        ARROW_HURRICANE_COOLDOWN = BUILDER.comment("Pileorkan (jæger): cooldown i sekunder").defineInRange("arrowHurricaneCooldown", 18.0, 0, 600);
        ARMY_OF_THE_DEAD_MANA = BUILDER.comment("De Dødes Hær (nekromantiker): mana det koster").defineInRange("armyOfTheDeadMana", 60.0, 0, 100);
        ARMY_OF_THE_DEAD_COOLDOWN = BUILDER.comment("De Dødes Hær (nekromantiker): cooldown i sekunder").defineInRange("armyOfTheDeadCooldown", 16.0, 0, 600);
        HEALTH_POTION_HEAL = BUILDER.comment("Livsdrik: hvor meget af dit max liv den giver tilbage (0.4 = 40 %)")
                .defineInRange("healthPotionHeal", 0.4, 0, 1);
        HEALTH_POTION_COOLDOWN = BUILDER.comment("Livsdrik: cooldown i sekunder").defineInRange("healthPotionCooldown", 20.0, 0, 600);
        MANA_POTION_MANA = BUILDER.comment("Manadrik: hvor meget mana den giver (max er 100)")
                .defineInRange("manaPotionMana", 50.0, 0, 100);
        MANA_POTION_COOLDOWN = BUILDER.comment("Manadrik: cooldown i sekunder").defineInRange("manaPotionCooldown", 20.0, 0, 600);
        BUILDER.pop();

        BUILDER.push("levels");
        XP_MULTIPLIER = BUILDER.comment("Ganger al XP fra monstre med dette tal. 2 = dobbelt så hurtigt op i level")
                .defineInRange("xpMultiplier", 1.0, 0, 100);
        BUILDER.pop();

        BUILDER.push("monsterLevels");
        MONSTER_LEVELS = BUILDER.comment("Om monstre får et level efter den nærmeste spillers level")
                .define("enabled", true);
        MONSTER_LEVEL_SPREAD = BUILDER.comment("Hvor mange levels et monster kan være over eller under spilleren")
                .defineInRange("levelSpread", 1, 0, 10);
        HEALTH_PER_LEVEL = BUILDER.comment("Ekstra liv pr. level over 1 (0.08 = +8 %, så level 11 har 80 % mere liv)")
                .defineInRange("healthPerLevel", 0.08, 0, 10);
        DAMAGE_PER_LEVEL = BUILDER.comment("Ekstra skade pr. level over 1 (0.07 = +7 %)")
                .defineInRange("damagePerLevel", 0.07, 0, 10);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
