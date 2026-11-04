package com.morkeriget.skill;

import java.util.List;
import java.util.Map;

import com.morkeriget.Morkeriget;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The ranger's reworked tree: some of the variants and the keystones. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class RangerTests {
    private RangerTests() {
    }

    private static ServerPlayer ranger(GameTestHelper helper, Map<Upgrade, Integer> ranks) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Progression.setForTests(player, 30, PlayerClass.RANGER, ranks);
        Mana.add(player, Mana.MAX);
        return player;
    }

    private static List<Wolf> wolves(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(Wolf.class, new AABB(player.blockPosition()).inflate(20), wolf -> wolf.isOwnedBy(player));
    }

    // Pierce: every Multishot arrow goes through two monsters
    @GameTest(template = "empty")
    public static void pierceArrowsGoThrough(GameTestHelper helper) {
        ServerPlayer player = ranger(helper, Map.of(Upgrade.MULTISHOT, 1, Upgrade.MULTISHOT_PIERCE, 1));
        Skills.use(player, Skill.MULTISHOT);
        List<Arrow> arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, new AABB(player.blockPosition()).inflate(10),
                arrow -> arrow.getOwner() == player);
        helper.assertTrue(!arrows.isEmpty() && arrows.stream().allMatch(arrow -> arrow.getPierceLevel() == 2),
                "Gennemboring gav ikke pilene gennemboring 2");
        arrows.forEach(Arrow::discard);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Alpha: a single wolf. Big Pack: two more than without it
    @GameTest(template = "empty")
    public static void alphaAndBigPack(GameTestHelper helper) {
        ServerPlayer alpha = ranger(helper, Map.of(Upgrade.WOLVES, 1, Upgrade.WOLVES_ALPHA, 1));
        Skills.use(alpha, Skill.WOLVES);
        int alphaCount = wolves(helper, alpha).size();
        wolves(helper, alpha).forEach(Wolf::discard);
        ServerPlayer plain = ranger(helper, Map.of(Upgrade.WOLVES, 1));
        Skills.use(plain, Skill.WOLVES);
        int plainCount = wolves(helper, plain).size();
        wolves(helper, plain).forEach(Wolf::discard);
        ServerPlayer pack = ranger(helper, Map.of(Upgrade.WOLVES, 1, Upgrade.WOLVES_PACK, 1));
        Skills.use(pack, Skill.WOLVES);
        int packCount = wolves(helper, pack).size();
        wolves(helper, pack).forEach(Wolf::discard);
        helper.assertTrue(alphaCount == 1, "Alfa gav " + alphaCount + " ulve, ikke 1");
        helper.assertTrue(packCount == plainCount + ClassSkills.BIG_PACK_EXTRA, "Stor flok gav " + packCount + " ulve, uden den " + plainCount);
        TestPlayers.remove(alpha);
        TestPlayers.remove(plain);
        TestPlayers.remove(pack);
        helper.succeed();
    }

    // Hunter's Instinct: the fourth arrow hit does twice the damage of the third
    @GameTest(template = "empty")
    public static void hunterInstinctDoublesEveryFourth(GameTestHelper helper) {
        ServerPlayer player = ranger(helper, Map.of(Upgrade.HUNTER_INSTINCT, 1));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 2));
        husk.setNoAi(true);
        float[] losses = new float[RangerSkills.HUNTER_EVERY];
        for (int i = 0; i < RangerSkills.HUNTER_EVERY; i++) {
            Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
            arrow.setOwner(player);
            husk.setHealth(husk.getMaxHealth());
            husk.invulnerableTime = 0;
            husk.hurt(player.damageSources().arrow(arrow, player), 2);
            losses[i] = husk.getMaxHealth() - husk.getHealth();
            arrow.discard();
        }
        float last = losses[RangerSkills.HUNTER_EVERY - 1];
        helper.assertTrue(losses[0] > 0 && Math.abs(last - losses[0] * 2) < 0.01, "Den 4. pil gav " + last + " skade, den 1. " + losses[0]);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Light Step: 20% less max health
    @GameTest(template = "empty")
    public static void lightStepCostsHealth(GameTestHelper helper) {
        ServerPlayer player = ranger(helper, Map.of());
        float health = player.getMaxHealth();
        Progression.setForTests(player, 30, PlayerClass.RANGER, Map.of(Upgrade.LIGHT_STEP, 1));
        helper.assertTrue(Math.abs(player.getMaxHealth() - health * 0.8f) < 0.01, "Let fod gav " + player.getMaxHealth() + " liv, ikke " + health * 0.8f);
        TestPlayers.remove(player);
        helper.succeed();
    }
}
