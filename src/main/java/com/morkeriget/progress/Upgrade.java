package com.morkeriget.progress;

import com.morkeriget.skill.Skill;

// Things the player can spend skill points on. Each one can be raised to MAX_RANK. Skill upgrades can only be
// raised by the class that has the skill; the ones without a skill are for every class.
// What a rank actually does is in Skills, Mana and Progression
public enum Upgrade {
    WHIRLWIND("Hvirvelvind", "+20% skade pr. rank", Skill.WHIRLWIND),
    FROST_NOVA("Frostnova", "+0,5 blok og +0,5 sek. frys pr. rank", Skill.FROST_NOVA),
    BLINK("Blink", "+2 blokke længere pr. rank", Skill.BLINK),
    CHAIN_LIGHTNING("Kædelyn", "+15% skade og +1 hop pr. rank", Skill.CHAIN_LIGHTNING),
    BATTLE_CRY("Krigsråb", "+1 sek. varighed pr. rank", Skill.BATTLE_CRY),
    LEAP("Spring", "+20% skade pr. rank", Skill.LEAP),
    FRENZY("Raseri", "+1 sek. varighed pr. rank", Skill.FRENZY),
    FIREBALL("Ildkugle", "+20% skade pr. rank", Skill.FIREBALL),
    MULTISHOT("Multiskud", "+15% skade pr. rank", Skill.MULTISHOT),
    ARROW_RAIN("Pileregn", "+15% skade og større område pr. rank", Skill.ARROW_RAIN),
    SMOKE_BOMB("Røgbombe", "+0,5 sek. varighed pr. rank", Skill.SMOKE_BOMB),
    WOLVES("Ulveflok", "+15% styrke, +1 ulv hver 3. rank", Skill.WOLVES),
    BONE_SPEAR("Knoglespyd", "+20% skade pr. rank", Skill.BONE_SPEAR),
    RAISE_SKELETONS("Rejs skeletter", "+15% styrke, +1 pr. kast hver 3. rank", Skill.RAISE_SKELETONS),
    BONE_ARMOR("Knoglerustning", "+5% af dit liv som skjold pr. rank", Skill.BONE_ARMOR),
    CURSE("Forbandelse", "+5% ekstra skade pr. rank", Skill.CURSE),
    VITALITY("Vitalitet", "+1 hjerte pr. rank", null),
    SPIRIT("Ånd", "+20% hurtigere mana pr. rank", null),
    STRENGTH("Styrke", "+5% skade pr. rank", null);

    public static final int MAX_RANK = 10;

    public final String displayName;
    public final String description;
    // The skill this upgrade makes better, or null for the ones every class has
    public final Skill skill;

    Upgrade(String displayName, String description, Skill skill) {
        this.displayName = displayName;
        this.description = description;
        this.skill = skill;
    }

    public static Upgrade byId(int id) {
        Upgrade[] values = values();
        return id >= 0 && id < values.length ? values[id] : WHIRLWIND;
    }
}
