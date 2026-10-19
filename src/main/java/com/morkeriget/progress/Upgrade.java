package com.morkeriget.progress;

import com.morkeriget.skill.Skill;

// Things the player can spend skill points on. The class ones make up the class's skill tree (see SkillTree): the first
// point in a skill unlocks it, and passives make you better at something without a key. The ones without a class are
// for everyone and aren't part of the tree. What a rank actually does is in Skills, Passives, Mana and Progression
public enum Upgrade {
    // Barbarian
    WHIRLWIND("Hvirvelvind", "+20% skade pr. rank", Skill.WHIRLWIND, PlayerClass.BARBARIAN, 0),
    LEAP("Spring", "+20% skade pr. rank", Skill.LEAP, PlayerClass.BARBARIAN, 0),
    UNYIELDING("Ukuelig", "-3% skade taget pr. rank", null, PlayerClass.BARBARIAN, 0),
    BATTLE_CRY("Krigsråb", "+1 sek. varighed pr. rank", Skill.BATTLE_CRY, PlayerClass.BARBARIAN, 1),
    FRENZY("Raseri", "+1 sek. varighed pr. rank", Skill.FRENZY, PlayerClass.BARBARIAN, 1),
    BLOODTHIRSTY("Blodtørstig", "+2% livsstjæl pr. rank", null, PlayerClass.BARBARIAN, 1),
    EARTHQUAKE("Jordskælv", "+20% skade pr. rank", Skill.EARTHQUAKE, PlayerClass.BARBARIAN, 2),
    RUSH("Stormløb", "+20% skade pr. rank", Skill.RUSH, PlayerClass.BARBARIAN, 2),
    RELENTLESS("Ubarmhjertig", "+4% knusende slag pr. rank", null, PlayerClass.BARBARIAN, 2),
    DOOM_SLAM("Dommedagsslag", "+20% skade og større chokbølger pr. rank", Skill.DOOM_SLAM, PlayerClass.BARBARIAN, 3),
    // Sorcerer
    FROST_NOVA("Frostnova", "+0,5 blok og +0,5 sek. frys pr. rank", Skill.FROST_NOVA, PlayerClass.SORCERER, 0),
    FIREBALL("Ildkugle", "+20% skade pr. rank", Skill.FIREBALL, PlayerClass.SORCERER, 0),
    GLASS_CANNON("Glaskanon", "+6% skade pr. rank", null, PlayerClass.SORCERER, 0),
    CHAIN_LIGHTNING("Kædelyn", "+15% skade og +1 hop pr. rank", Skill.CHAIN_LIGHTNING, PlayerClass.SORCERER, 1),
    BLINK("Blink", "+2 blokke længere pr. rank", Skill.BLINK, PlayerClass.SORCERER, 1),
    ARCANE_FLOW("Arkan strøm", "-5% mana-pris pr. rank", null, PlayerClass.SORCERER, 1),
    METEOR("Meteor", "+20% skade pr. rank", Skill.METEOR, PlayerClass.SORCERER, 2),
    ICE_ARMOR("Isrustning", "+1 sek. varighed pr. rank", Skill.ICE_ARMOR, PlayerClass.SORCERER, 2),
    TIME_WARP("Tidsforvrængning", "-4% cooldown pr. rank", null, PlayerClass.SORCERER, 2),
    THUNDERSTORM("Tordenvejr", "+20% skade og +1 lyn hver 2. rank", Skill.THUNDERSTORM, PlayerClass.SORCERER, 3),
    // Ranger
    MULTISHOT("Multiskud", "+15% skade pr. rank", Skill.MULTISHOT, PlayerClass.RANGER, 0),
    SMOKE_BOMB("Røgbombe", "+0,5 sek. varighed pr. rank", Skill.SMOKE_BOMB, PlayerClass.RANGER, 0),
    MARKSMAN("Skarpskytte", "+6% pileskade pr. rank", null, PlayerClass.RANGER, 0),
    ARROW_RAIN("Pileregn", "+15% skade og større område pr. rank", Skill.ARROW_RAIN, PlayerClass.RANGER, 1),
    WOLVES("Ulveflok", "+15% styrke, +1 ulv hver 3. rank, bedre rustning hver 5.", Skill.WOLVES, PlayerClass.RANGER, 1),
    AGILE("Smidig", "+3% fart pr. rank", null, PlayerClass.RANGER, 1),
    EXPLOSIVE_ARROW("Eksplosiv pil", "+15% skade pr. rank", Skill.EXPLOSIVE_ARROW, PlayerClass.RANGER, 2),
    BEAR_TRAP("Bjørnefælde", "+15% skade pr. rank", Skill.BEAR_TRAP, PlayerClass.RANGER, 2),
    PACK_LEADER("Flokleder", "+10% ulve-styrke pr. rank", null, PlayerClass.RANGER, 2),
    ARROW_HURRICANE("Pileorkan", "+15% skade og +2 pile hver 2. rank", Skill.ARROW_HURRICANE, PlayerClass.RANGER, 3),
    // Necromancer
    BONE_SPEAR("Knoglespyd", "+20% skade pr. rank", Skill.BONE_SPEAR, PlayerClass.NECROMANCER, 0),
    BONE_ARMOR("Knoglerustning", "+5% af dit liv som skjold pr. rank", Skill.BONE_ARMOR, PlayerClass.NECROMANCER, 0),
    GRAVE_CHILL("Gravens kulde", "+4% skade mod forbandede pr. rank", null, PlayerClass.NECROMANCER, 0),
    RAISE_SKELETONS("Rejs skeletter", "+15% styrke, +1 pr. kast hver 3. rank, bedre udstyr hver 5.", Skill.RAISE_SKELETONS, PlayerClass.NECROMANCER, 1),
    CURSE("Forbandelse", "+5% ekstra skade pr. rank", Skill.CURSE, PlayerClass.NECROMANCER, 1),
    DEATH_LORD("Dødens herre", "+10% minion-styrke pr. rank", null, PlayerClass.NECROMANCER, 1),
    BLOOD_NOVA("Blodnova", "+20% skade pr. rank", Skill.BLOOD_NOVA, PlayerClass.NECROMANCER, 2),
    BONE_PRISON("Knoglefængsel", "+0,3 sek. fanget pr. rank", Skill.BONE_PRISON, PlayerClass.NECROMANCER, 2),
    SOUL_HARVEST("Sjælehøst", "+1% ekstra heling pr. dræb pr. rank", null, PlayerClass.NECROMANCER, 2),
    ARMY_OF_THE_DEAD("De Dødes Hær", "+20% skade og +1 arm hver 2. rank", Skill.ARMY_OF_THE_DEAD, PlayerClass.NECROMANCER, 3),
    // For everyone
    VITALITY("Vitalitet", "+1 hjerte pr. rank", null, null, 0),
    SPIRIT("Ånd", "+20% hurtigere mana pr. rank", null, null, 0),
    STRENGTH("Styrke", "+5% skade pr. rank", null, null, 0);

    // Skills go to 20, passives and the shared upgrades to 10. There are far more ranks than skill points at level 100,
    // so you have to pick what to get good at
    private static final int SKILL_MAX_RANK = 20;
    private static final int PASSIVE_MAX_RANK = 10;
    private static final int SHARED_MAX_RANK = 10;
    // Skill ranks above this still add damage, but things like radius, duration, jumps and how many summons stop
    // growing, or they would get silly. Instead each rank above it takes COOLDOWN_PER_RANK off the skill's cooldown
    public static final int UTILITY_MAX_RANK = 10;
    public static final float COOLDOWN_PER_RANK = 0.02f;

    public final String displayName;
    public final String description;
    // The skill this upgrade unlocks and makes better, or null for passives and the shared upgrades
    public final Skill skill;
    // The class whose tree it is in, or null for the shared upgrades
    public final PlayerClass playerClass;
    // The row in the tree, from 0
    public final int row;

    Upgrade(String displayName, String description, Skill skill, PlayerClass playerClass, int row) {
        this.displayName = displayName;
        this.description = description;
        this.skill = skill;
        this.playerClass = playerClass;
        this.row = row;
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
