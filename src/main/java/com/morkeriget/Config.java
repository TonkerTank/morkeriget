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
    public static final ModConfigSpec.DoubleValue ELITE_CHANCE;
    public static final ModConfigSpec.DoubleValue SECOND_MODIFIER_CHANCE;
    public static final ModConfigSpec.DoubleValue ELITE_HEALTH;
    public static final ModConfigSpec.DoubleValue ELITE_DAMAGE;
    public static final ModConfigSpec.DoubleValue BOSS_CHANCE;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE;
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
        DROP_CHANCE = BUILDER.comment("Chance for at et almindeligt monster dropper en genstand (0.08 = 8 %)")
                .defineInRange("dropChance", 0.08, 0, 1);
        AFFIX_PER_LEVEL = BUILDER.comment("Hvor meget stærkere bonusser bliver pr. genstandslevel (0.05 = +5 %, så level 21 er dobbelt så stærkt).",
                        "Det gælder skade, rustning og hårdhed. Liv vokser halvt så hurtigt. Procent-bonusser som fart,",
                        "angrebshastighed, livsstjæl og torne vokser ikke")
                .defineInRange("affixScalingPerLevel", 0.05, 0, 1);
        SET_CHANCE_BOSS = BUILDER.comment("Chance for at en boss' legendariske genstand i stedet er en sæt-genstand (0.3 = 30 %)")
                .defineInRange("setChanceBoss", 0.3, 0, 1);
        SET_CHANCE_ELITE = BUILDER.comment("Chance for at en elite dropper en sæt-genstand i stedet for sin normale genstand (0.02 = 2 %)")
                .defineInRange("setChanceElite", 0.02, 0, 1);
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
        BUILDER.pop();

        BUILDER.push("bosses");
        BOSS_CHANCE = BUILDER.comment("Chance for at et monster spawner som boss (0.005 = 0,5 %). Sæt til 1 for at teste")
                .defineInRange("bossChance", 0.005, 0, 1);
        BOSS_HEALTH = BUILDER.comment("Hvor mange gange mere liv en boss har end et almindeligt monster")
                .defineInRange("healthMultiplier", 4.0, 1, 100);
        BOSS_DAMAGE = BUILDER.comment("Hvor mange gange mere skade en boss gør end et almindeligt monster")
                .defineInRange("damageMultiplier", 2.0, 1, 100);
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
