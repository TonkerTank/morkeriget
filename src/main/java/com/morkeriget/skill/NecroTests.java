package com.morkeriget.skill;

import java.util.Map;

import com.morkeriget.Morkeriget;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.PlayerProgress;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The necromancer's reworked tree: some of the variants and the keystones. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class NecroTests {
    // Players need this long after they join before they can be hurt
    private static final int SPAWN_PROTECTION = 61;

    private NecroTests() {
    }

    private static ServerPlayer necromancer(GameTestHelper helper, Map<Upgrade, Integer> ranks) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Progression.setForTests(player, 30, PlayerClass.NECROMANCER, ranks);
        Mana.add(player, Mana.MAX);
        return player;
    }

    private static Husk husk(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawn(EntityType.HUSK, pos);
        husk.setNoAi(true);
        return husk;
    }

    private static int skeletons(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(WitherSkeleton.class, new AABB(player.blockPosition()).inflate(20),
                skeleton -> Minions.isOwnedBy(skeleton, player)).size();
    }

    private static void removeSkeletons(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getEntitiesOfClass(WitherSkeleton.class, new AABB(player.blockPosition()).inflate(20),
                skeleton -> Minions.isOwnedBy(skeleton, player)).forEach(WitherSkeleton::discard);
    }

    // Necromancers who spent their points in the old tree get them back too
    @GameTest(template = "empty")
    public static void oldNecroTreeGivesPointsBack(GameTestHelper helper) {
        PlayerProgress old = new PlayerProgress(12, 0, 0, Map.of("BONE_SPEAR", 12), "NECROMANCER", PlayerProgress.START.slots(), 0, 0, Map.of(), 0);
        helper.assertTrue(old.isFromOldTree(), "Den gamle nekromantiker blev ikke set som fra det gamle træ");
        helper.succeed();
    }

    // Giants: never more than four skeletons, however many times you raise them
    @GameTest(template = "empty")
    public static void giantsAreFewer(GameTestHelper helper) {
        ServerPlayer player = necromancer(helper, Map.of(Upgrade.RAISE_SKELETONS, 5, Upgrade.RAISE_SKELETONS_GIANTS, 1));
        for (int i = 0; i < 4; i++) {
            Skills.resetCooldowns(player);
            Mana.add(player, Mana.MAX);
            Skills.use(player, Skill.RAISE_SKELETONS);
        }
        int count = skeletons(helper, player);
        helper.assertTrue(count == NecroSkills.GIANTS_MAX, "Kæmper gav " + count + " skeletter, ikke " + NecroSkills.GIANTS_MAX);
        removeSkeletons(helper, player);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Sacrifice: no skeletons, and 15% less damage taken
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sacrificeGivesUpSkeletons(GameTestHelper helper) {
        ServerPlayer player = necromancer(helper, Map.of(Upgrade.RAISE_SKELETONS, 1, Upgrade.SACRIFICE, 1));
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    Skills.use(player, Skill.RAISE_SKELETONS);
                    helper.assertTrue(skeletons(helper, player) == 0, "Ofring rejste skeletter alligevel");
                    player.setHealth(player.getMaxHealth());
                    player.hurt(player.damageSources().generic(), 10);
                    float taken = player.getMaxHealth() - player.getHealth();
                    helper.assertTrue(Math.abs(taken - 8.5f) < 0.01, "Ofring tog " + taken + " skade, ikke 8,5");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // Blood Pact: with no mana the skill costs health instead, and without the keystone it isn't used at all
    @GameTest(template = "empty")
    public static void bloodPactPaysWithHealth(GameTestHelper helper) {
        ServerPlayer player = necromancer(helper, Map.of(Upgrade.BONE_SPEAR, 1, Upgrade.BLOOD_PACT, 1));
        Mana.spend(player, Mana.get(player));
        float before = player.getHealth();
        Skills.use(player, Skill.BONE_SPEAR);
        float expected = Skill.BONE_SPEAR.manaCost() * Skills.BLOOD_PACT_HEALTH;
        helper.assertTrue(Math.abs(before - player.getHealth() - expected) < 0.01,
                "Blodpagt kostede " + (before - player.getHealth()) + " liv, ikke " + expected);
        helper.assertTrue(Mana.get(player) == 0, "Blodpagt brugte mana");

        ServerPlayer plain = necromancer(helper, Map.of(Upgrade.BONE_SPEAR, 1));
        Mana.spend(plain, Mana.get(plain));
        float plainBefore = plain.getHealth();
        Skills.use(plain, Skill.BONE_SPEAR);
        helper.assertTrue(plain.getHealth() == plainBefore, "Knoglespyd kostede liv uden Blodpagt");
        TestPlayers.remove(plain);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Weaken: a cursed monster's hits do 30% less
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void weakenedEnemiesHitSofter(GameTestHelper helper) {
        ServerPlayer player = necromancer(helper, Map.of(Upgrade.CURSE, 1, Upgrade.CURSE_WEAKEN, 1));
        Husk husk = husk(helper, new BlockPos(4, 2, 2));
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    Skills.use(player, Skill.CURSE);
                    player.setHealth(player.getMaxHealth());
                    player.hurt(player.damageSources().mobAttack(husk), 10);
                    float taken = player.getMaxHealth() - player.getHealth();
                    helper.assertTrue(Math.abs(taken - 7) < 0.01, "Den forbandede gjorde " + taken + " skade, ikke 7");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // Decay: a cursed monster loses health every second without being hit
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void decayRotsCursedEnemies(GameTestHelper helper) {
        ServerPlayer player = necromancer(helper, Map.of(Upgrade.CURSE, 1, Upgrade.CURSE_DECAY, 1));
        Husk husk = husk(helper, new BlockPos(4, 2, 2));
        helper.startSequence()
                .thenExecute(() -> Skills.use(player, Skill.CURSE))
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Forfald tog ikke liv fra den forbandede");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }
}
