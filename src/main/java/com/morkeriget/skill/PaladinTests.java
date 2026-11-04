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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The paladin: the class bonus, Zeal, Holy Light and Holy Hammer. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class PaladinTests {
    // Players need this long after they join before they can be hurt
    private static final int SPAWN_PROTECTION = 61;
    // Long enough for every display a single skill leaves to be gone
    private static final int DISPLAYS_GONE = 50;

    private PaladinTests() {
    }

    private static ServerPlayer paladin(GameTestHelper helper, Map<Upgrade, Integer> ranks) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Progression.setForTests(player, 1, PlayerClass.PALADIN, ranks);
        Mana.add(player, Mana.MAX);
        return player;
    }

    private static Husk husk(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawn(EntityType.HUSK, pos);
        husk.setNoAi(true);
        return husk;
    }

    // A paladin takes 15% less damage with an enemy within 4 blocks, and the full damage without
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void guardWithEnemyClose(GameTestHelper helper) {
        ServerPlayer player = paladin(helper, Map.of());
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    player.setHealth(player.getMaxHealth());
                    player.hurt(player.damageSources().generic(), 10);
                    float alone = player.getMaxHealth() - player.getHealth();
                    helper.assertTrue(Math.abs(alone - 10) < 0.01, "Paladinen tog " + alone + " skade alene, ikke 10");
                    husk(helper, new BlockPos(4, 2, 2));
                    player.setHealth(player.getMaxHealth());
                    player.invulnerableTime = 0;
                    player.hurt(player.damageSources().generic(), 10);
                    float guarded = player.getMaxHealth() - player.getHealth();
                    helper.assertTrue(Math.abs(guarded - 8.5) < 0.01, "Paladinen tog " + guarded + " skade med en fjende tæt på, ikke 8,5");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // Each Zeal rank makes you swing 4% faster
    @GameTest(template = "empty")
    public static void zealGivesAttackSpeed(GameTestHelper helper) {
        ServerPlayer player = paladin(helper, Map.of());
        double before = player.getAttributeValue(Attributes.ATTACK_SPEED);
        Progression.setForTests(player, 10, PlayerClass.PALADIN, Map.of(Upgrade.ZEAL, 5));
        double after = player.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(after - before * 1.2) < 0.01, "Iver 5 gav " + after + " i angrebshastighed, ikke " + before * 1.2);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Holy Light heals the paladin and other players close by, not only the one who used it. The test waits for its
    // pillars of light to go, since a display left behind when a test ends stays forever and is counted by
    // displaysComeAndGo if that test is close by
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void holyLightHealsNearbyPlayers(GameTestHelper helper) {
        ServerPlayer player = paladin(helper, Map.of(Upgrade.HOLY_LIGHT, 1));
        ServerPlayer friend = TestPlayers.survivor(helper, new BlockPos(4, 2, 2));
        player.setHealth(4);
        friend.setHealth(4);
        Skills.use(player, Skill.HOLY_LIGHT);
        helper.assertTrue(player.getHealth() > 8, "Helligt Lys helede ikke paladinen: " + player.getHealth());
        helper.assertTrue(friend.getHealth() > 8, "Helligt Lys helede ikke spilleren ved siden af: " + friend.getHealth());
        TestPlayers.remove(friend);
        TestPlayers.remove(player);
        helper.startSequence()
                .thenIdle(DISPLAYS_GONE)
                .thenSucceed();
    }

    // The skills show their hammer, spear, pillars and holy ground as display entities, and every one of them is gone again
    // once the skills are over
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void displaysComeAndGo(GameTestHelper helper) {
        ServerPlayer player = paladin(helper, Map.of(Upgrade.HOLY_HAMMER, 1, Upgrade.SHIELD_BASH, 1, Upgrade.HOLY_AURA, 1, Upgrade.HOLY_LIGHT, 1,
                Upgrade.CONSECRATION, 1, Upgrade.SPEAR_OF_JUDGEMENT, 1, Upgrade.HEAVENS_JUDGEMENT, 1));
        AABB around = new AABB(helper.absolutePos(new BlockPos(2, 2, 2))).inflate(30);
        helper.startSequence()
                .thenExecute(() -> {
                    for (Skill skill : PlayerClass.PALADIN.skills) {
                        Mana.add(player, Mana.MAX);
                        Skills.use(player, skill);
                    }
                })
                .thenExecuteFor(20, () -> TestPlayers.tick(player))
                .thenExecute(() -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(Display.class, around).isEmpty(),
                        "Paladinens færdigheder viste ingen display-entities"))
                .thenExecuteFor(240, () -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    // Which ones and where, so a failure says if they came from another test close by
                    List<Display> left = helper.getLevel().getEntitiesOfClass(Display.class, around);
                    StringBuilder which = new StringBuilder();
                    for (Display display : left) {
                        which.append(" [").append(display.getType().toShortString()).append(" ved ").append(helper.relativePos(display.blockPosition()))
                                .append(", ").append(display.tickCount).append(" ticks gammel]");
                    }
                    helper.assertTrue(left.isEmpty(), left.size() + " display-entities blev liggende efter færdighederne:" + which);
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // The hammer spirals out and hits a monster standing two blocks away. It also waits for the hammer to go, like
    // holyLightHealsNearbyPlayers
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void holyHammerHitsAround(GameTestHelper helper) {
        ServerPlayer player = paladin(helper, Map.of(Upgrade.HOLY_HAMMER, 1));
        Husk husk = husk(helper, new BlockPos(4, 2, 2));
        helper.startSequence()
                .thenExecute(() -> Skills.use(player, Skill.HOLY_HAMMER))
                .thenExecuteFor(40, () -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Hellig Hammer ramte ikke monsteret");
                    TestPlayers.remove(player);
                })
                .thenIdle(DISPLAYS_GONE)
                .thenSucceed();
    }
}
