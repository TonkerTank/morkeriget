package com.morkeriget.elite;

import com.morkeriget.Morkeriget;
import com.morkeriget.Text;
import com.morkeriget.gametest.TestPlayers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The boss phases and the shield, see Bosses. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class BossTests {
    // Players need this long after they join before they can be hurt
    private static final int SPAWN_PROTECTION = 61;

    private BossTests() {
    }

    // Like the dungeons do it: marked as checked first, so it isn't made an elite on its own when it joins the world
    private static WitherSkeleton spawn(GameTestHelper helper) {
        WitherSkeleton monster = EntityType.WITHER_SKELETON.create(helper.getLevel());
        Elites.markChecked(monster);
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 2, 5.5));
        monster.moveTo(at.x, at.y, at.z, 0, 0);
        helper.getLevel().addFreshEntity(monster);
        return monster;
    }

    // Mørkets Herre is bigger than a boss, gets a shield and helpers at half health and a third phase at a quarter
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void uberBossPhases(GameTestHelper helper) {
        WitherSkeleton boss = spawn(helper);
        Bosses.makeUberBoss(boss);
        helper.assertTrue(Text.is(boss.getCustomName(), "boss.uber"), "Uber-bossen hedder " + boss.getName().getString());
        helper.assertTrue(boss.getAttributeValue(Attributes.SCALE) >= 2.0, "Uber-bossen er ikke større end en normal boss");
        boss.setHealth(boss.getMaxHealth() * 0.45f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Bosses.isEnraged(boss), "Fase 2 startede ikke ved halvt liv"))
                .thenExecute(() -> {
                    helper.assertTrue(Bosses.isShielded(boss), "Intet skjold i fase 2");
                    helper.assertFalse(Bosses.inFinalPhase(boss), "Fase 3 startede allerede ved 45% liv");
                    float before = boss.getHealth();
                    boss.hurt(helper.getLevel().damageSources().generic(), 10);
                    helper.assertTrue(boss.getHealth() == before, "Skjoldet tog ikke slaget");
                    boss.setHealth(boss.getMaxHealth() * 0.2f);
                })
                .thenWaitUntil(() -> helper.assertTrue(Bosses.inFinalPhase(boss), "Fase 3 startede ikke ved en fjerdedel liv"))
                .thenSucceed();
    }

    // Doom Circles go off on time even if the boss has no target anymore, e.g. because the player died
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void doomCirclesGoOffWithoutTarget(GameTestHelper helper) {
        WitherSkeleton boss = spawn(helper);
        Bosses.makeBoss(boss);
        // No AI, so it never picks a target of its own
        boss.setNoAi(true);
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> BossAbility.DOOM_CIRCLES.use(helper.getLevel(), boss, player))
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth(), "Dødscirklen gik ikke af, da bossen intet mål havde"))
                .thenExecute(() -> TestPlayers.remove(player))
                .thenSucceed();
    }

    // Slowness and weakness on a boss are weaker and shorter, but a normal monster gets them as they are
    @GameTest(template = "empty")
    public static void bossesCannotBeHeldStill(GameTestHelper helper) {
        WitherSkeleton boss = spawn(helper);
        Bosses.makeBoss(boss);
        boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 9));
        boss.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 4));
        MobEffectInstance slow = boss.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffectInstance weak = boss.getEffect(MobEffects.WEAKNESS);
        helper.assertTrue(slow != null && slow.getAmplifier() <= 1 && slow.getDuration() <= 30,
                "Bossen fik langsomhed " + (slow != null ? slow.getAmplifier() + " i " + slow.getDuration() + " ticks" : "slet ikke"));
        helper.assertTrue(weak != null && weak.getAmplifier() == 0 && weak.getDuration() <= 30, "Bossen fik for stærk svaghed");
        WitherSkeleton normal = spawn(helper);
        normal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 9));
        helper.assertTrue(normal.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 9, "Et almindeligt monster fik ikke den fulde langsomhed");
        helper.succeed();
    }

    // A normal boss never gets a third phase
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void normalBossHasNoThirdPhase(GameTestHelper helper) {
        WitherSkeleton boss = spawn(helper);
        Bosses.makeBoss(boss);
        boss.setHealth(boss.getMaxHealth() * 0.2f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Bosses.isEnraged(boss), "Fase 2 startede ikke"))
                .thenIdle(10)
                .thenExecute(() -> helper.assertFalse(Bosses.inFinalPhase(boss), "En normal boss fik fase 3"))
                .thenSucceed();
    }

    // The shield breaks when the helpers are dead
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shieldBreaksWhenHelpersDie(GameTestHelper helper) {
        WitherSkeleton boss = spawn(helper);
        Bosses.makeBoss(boss);
        boss.setHealth(boss.getMaxHealth() * 0.45f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Bosses.isShielded(boss), "Intet skjold i fase 2"))
                .thenExecute(() -> {
                    var helpers = helper.getLevel().getEntitiesOfClass(Monster.class, boss.getBoundingBox().inflate(32),
                            monster -> Bosses.isShieldMinion(monster, boss));
                    helper.assertTrue(!helpers.isEmpty(), "Bossen kaldte ingen tjenere");
                    helpers.forEach(Monster::kill);
                })
                .thenWaitUntil(() -> helper.assertFalse(Bosses.isShielded(boss), "Skjoldet blev ikke brudt, da tjenerne døde"))
                .thenSucceed();
    }

    // /kill goes through the shield
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void killGoesThroughShield(GameTestHelper helper) {
        WitherSkeleton boss = spawn(helper);
        Bosses.makeBoss(boss);
        boss.setHealth(boss.getMaxHealth() * 0.45f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Bosses.isShielded(boss), "Intet skjold i fase 2"))
                .thenExecute(() -> {
                    boss.kill();
                    helper.assertTrue(boss.isDeadOrDying(), "/kill gik ikke gennem skjoldet");
                })
                .thenSucceed();
    }
}
