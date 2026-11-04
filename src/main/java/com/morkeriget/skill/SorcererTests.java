package com.morkeriget.skill;

import java.util.Map;

import com.morkeriget.Morkeriget;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.PlayerProgress;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.SkillTree;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The sorcerer's reworked tree: the rules for variants and keystones, the points given back from the old tree, and
// some of the variants and keystones. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class SorcererTests {
    private SorcererTests() {
    }

    private static ServerPlayer sorcerer(GameTestHelper helper, Map<Upgrade, Integer> ranks) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Progression.setForTests(player, 30, PlayerClass.SORCERER, ranks);
        Mana.add(player, Mana.MAX);
        return player;
    }

    private static Husk husk(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawn(EntityType.HUSK, pos);
        husk.setNoAi(true);
        return husk;
    }

    // A variant opens at rank 5 in its skill and costs a point, switching to the other one is free and takes the first
    // one away, and the boxes further down need the one above them and enough points in the tree
    @GameTest(template = "empty")
    public static void variantsAndKeystonesFollowTheRules(GameTestHelper helper) {
        PlayerClass sorcerer = PlayerClass.SORCERER;
        PlayerProgress progress = PlayerProgress.START.withClass(sorcerer).withLevel(30, 0, 30);
        helper.assertFalse(SkillTree.canRaise(sorcerer, Upgrade.FIREBALL_BURN, progress.points(), progress::rank),
                "Brand kunne vælges uden Ildkugle");
        for (int i = 0; i < 4; i++) {
            progress = progress.withUpgrade(Upgrade.FIREBALL);
        }
        PlayerProgress atFour = progress;
        helper.assertFalse(SkillTree.canRaise(sorcerer, Upgrade.FIREBALL_BURN, atFour.points(), atFour::rank),
                "Brand kunne vælges ved rank 4");
        progress = progress.withUpgrade(Upgrade.FIREBALL);
        PlayerProgress atFive = progress;
        helper.assertTrue(SkillTree.canRaise(sorcerer, Upgrade.FIREBALL_BURN, atFive.points(), atFive::rank),
                "Brand kunne ikke vælges ved rank 5");
        int before = progress.points();
        progress = progress.withUpgrade(Upgrade.FIREBALL_BURN);
        helper.assertTrue(progress.points() == before - 1, "Brand kostede ikke 1 point");
        PlayerProgress burn = progress;
        helper.assertTrue(SkillTree.canRaise(sorcerer, Upgrade.FIREBALL_SPLINTER, 0, burn::rank),
                "Man kunne ikke skifte til Splinter uden point");
        progress = progress.withUpgrade(Upgrade.FIREBALL_SPLINTER);
        helper.assertTrue(progress.points() == before - 1, "Det kostede point at skifte variant");
        helper.assertTrue(progress.rank(Upgrade.FIREBALL_BURN) == 0 && progress.rank(Upgrade.FIREBALL_SPLINTER) == 1,
                "Skiftet til Splinter fjernede ikke Brand");

        // 6 points spent: Glass Cannon is open, Meteor needs Glass Cannon first, and the keystones need 20
        PlayerProgress six = progress;
        helper.assertTrue(SkillTree.isOpen(sorcerer, Upgrade.GLASS_CANNON, six::rank), "Glaskanon var ikke åben");
        helper.assertFalse(SkillTree.isOpen(sorcerer, Upgrade.METEOR, six::rank), "Meteor var åben uden Glaskanon");
        helper.assertFalse(SkillTree.isOpen(sorcerer, Upgrade.BLINK, six::rank), "Blink var åben uden Frostnova");
        helper.assertFalse(SkillTree.isOpen(sorcerer, Upgrade.UNSTABLE_MAGIC, six::rank), "Keystones var åbne ved 6 point");
        for (int i = 0; i < 14; i++) {
            progress = progress.withUpgrade(Upgrade.FIREBALL);
        }
        PlayerProgress twenty = progress;
        helper.assertTrue(SkillTree.isOpen(sorcerer, Upgrade.UNSTABLE_MAGIC, twenty::rank), "Keystones var ikke åbne ved 20 point");
        progress = progress.withUpgrade(Upgrade.UNSTABLE_MAGIC).withUpgrade(Upgrade.ARCANE_RHYTHM);
        helper.assertTrue(progress.rank(Upgrade.UNSTABLE_MAGIC) == 0 && progress.rank(Upgrade.ARCANE_RHYTHM) == 1,
                "Man kunne have to keystones på én gang");
        helper.succeed();
    }

    // Sorcerers who spent their points in the old tree get them all back once, and other classes keep theirs
    @GameTest(template = "empty")
    public static void oldTreeGivesPointsBack(GameTestHelper helper) {
        PlayerProgress old = new PlayerProgress(12, 0, 0, Map.of("FIREBALL", 12), "SORCERER", PlayerProgress.START.slots(), 0, 0, Map.of(), 0);
        helper.assertTrue(old.isFromOldTree(), "Den gamle troldmand blev ikke set som fra det gamle træ");
        PlayerProgress back = old.respec();
        helper.assertTrue(back.points() == 12 && back.ranks().isEmpty(), "Pointene kom ikke tilbage: " + back.points());
        helper.assertFalse(back.isFromOldTree(), "Pointene ville blive givet tilbage igen");
        PlayerProgress barbarian = new PlayerProgress(12, 0, 0, Map.of("WHIRLWIND", 12), "BARBARIAN", PlayerProgress.START.slots(), 0, 0, Map.of(), 0);
        helper.assertFalse(barbarian.isFromOldTree(), "Barbaren fik pointene tilbage, selvom træet er det samme");
        helper.succeed();
    }

    // Unstable Magic: 40% more damage and 30% less max health
    @GameTest(template = "empty")
    public static void unstableMagicTradesHealthForDamage(GameTestHelper helper) {
        ServerPlayer player = sorcerer(helper, Map.of());
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double health = player.getMaxHealth();
        Progression.setForTests(player, 30, PlayerClass.SORCERER, Map.of(Upgrade.UNSTABLE_MAGIC, 1));
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - damage * 1.4) < 0.01,
                "Ustabil magi gav " + player.getAttributeValue(Attributes.ATTACK_DAMAGE) + " skade, ikke " + damage * 1.4);
        helper.assertTrue(Math.abs(player.getMaxHealth() - health * 0.7) < 0.01,
                "Ustabil magi gav " + player.getMaxHealth() + " liv, ikke " + health * 0.7);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Arcane Rhythm: the first four skills cost mana, the fifth doesn't
    @GameTest(template = "empty")
    public static void arcaneRhythmMakesTheFifthFree(GameTestHelper helper) {
        ServerPlayer player = sorcerer(helper, Map.of(Upgrade.FROST_NOVA, 1, Upgrade.ARCANE_RHYTHM, 1));
        for (int cast = 1; cast <= Skills.RHYTHM_EVERY; cast++) {
            Skills.resetCooldowns(player);
            Mana.add(player, Mana.MAX);
            float before = Mana.get(player);
            Skills.use(player, Skill.FROST_NOVA);
            float spent = before - Mana.get(player);
            if (cast < Skills.RHYTHM_EVERY) {
                helper.assertTrue(spent > 0, "Skill nr. " + cast + " kostede ingen mana");
            } else {
                helper.assertTrue(spent == 0, "Skill nr. " + cast + " kostede " + spent + " mana");
            }
        }
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Deep Freeze: a monster frozen by the nova takes 25% more damage than one that wasn't hit
    @GameTest(template = "empty")
    public static void deepFreezeAddsDamage(GameTestHelper helper) {
        ServerPlayer player = sorcerer(helper, Map.of(Upgrade.FROST_NOVA, 1, Upgrade.FROST_NOVA_DEEP_FREEZE, 1));
        Husk frozen = husk(helper, new BlockPos(4, 2, 2));
        Husk far = husk(helper, new BlockPos(2, 2, 14));
        Skills.use(player, Skill.FROST_NOVA);
        frozen.setHealth(frozen.getMaxHealth());
        frozen.invulnerableTime = 0;
        frozen.hurt(frozen.damageSources().generic(), 4);
        far.hurt(far.damageSources().generic(), 4);
        float frozenLoss = frozen.getMaxHealth() - frozen.getHealth();
        float farLoss = far.getMaxHealth() - far.getHealth();
        helper.assertTrue(farLoss > 0 && Math.abs(frozenLoss - farLoss * 1.25f) < 0.01,
                "Den frosne tog " + frozenLoss + " skade, den anden " + farLoss);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Ice Mantle: two monsters hit give two levels of absorption
    @GameTest(template = "empty")
    public static void iceMantleShieldsPerEnemy(GameTestHelper helper) {
        ServerPlayer player = sorcerer(helper, Map.of(Upgrade.FROST_NOVA, 1, Upgrade.FROST_NOVA_ICE_MANTLE, 1));
        husk(helper, new BlockPos(4, 2, 2));
        husk(helper, new BlockPos(2, 2, 4));
        Skills.use(player, Skill.FROST_NOVA);
        MobEffectInstance absorption = player.getEffect(MobEffects.ABSORPTION);
        helper.assertTrue(absorption != null && absorption.getAmplifier() == 1,
                "Iskappe gav " + (absorption == null ? "ingen absorption" : "absorption " + (absorption.getAmplifier() + 1)) + ", ikke 2");
        TestPlayers.remove(player);
        helper.succeed();
    }
}
