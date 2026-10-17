package com.morkeriget;

import net.neoforged.neoforge.common.ModConfigSpec;

// Settings in config/morkeriget-common.toml. The game reloads the file when it is saved, but stats are given
// to monsters when they spawn, so changes only affect new monsters. The comments are what the player sees in the file
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue DROP_CHANCE;
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
    public static final ModConfigSpec.DoubleValue XP_MULTIPLIER;

    static {
        BUILDER.push("loot");
        DROP_CHANCE = BUILDER.comment("Chance for at et almindeligt monster dropper en genstand (0.08 = 8 %)")
                .defineInRange("dropChance", 0.08, 0, 1);
        BUILDER.pop();

        BUILDER.push("elites");
        ELITE_CHANCE = BUILDER.comment("Chance for at et monster spawner som elite (0.05 = 5 %)")
                .defineInRange("eliteChance", 0.05, 0, 1);
        SECOND_MODIFIER_CHANCE = BUILDER.comment("Chance for at en elite får to modifiers i stedet for én")
                .defineInRange("secondModifierChance", 0.3, 0, 1);
        ELITE_HEALTH = BUILDER.comment("Hvor mange gange mere liv en elite har end et almindeligt monster")
                .defineInRange("healthMultiplier", 3.0, 1, 100);
        ELITE_DAMAGE = BUILDER.comment("Hvor mange gange mere skade en elite gør end et almindeligt monster")
                .defineInRange("damageMultiplier", 1.5, 1, 100);
        BUILDER.pop();

        BUILDER.push("bosses");
        BOSS_CHANCE = BUILDER.comment("Chance for at et monster spawner som boss (0.005 = 0,5 %). Sæt til 1 for at teste")
                .defineInRange("bossChance", 0.005, 0, 1);
        BOSS_HEALTH = BUILDER.comment("Hvor mange gange mere liv en boss har end et almindeligt monster")
                .defineInRange("healthMultiplier", 4.0, 1, 100);
        BOSS_DAMAGE = BUILDER.comment("Hvor mange gange mere skade en boss gør end et almindeligt monster")
                .defineInRange("damageMultiplier", 2.25, 1, 100);
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
        BUILDER.pop();

        BUILDER.push("levels");
        XP_MULTIPLIER = BUILDER.comment("Ganger al XP fra monstre med dette tal. 2 = dobbelt så hurtigt op i level")
                .defineInRange("xpMultiplier", 1.0, 0, 100);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
