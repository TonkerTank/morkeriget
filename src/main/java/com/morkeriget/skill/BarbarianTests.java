package com.morkeriget.skill;

import java.util.Locale;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The barbarian's reworked tree: some of the variants and the keystones. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class BarbarianTests {
    // Players need this long after they join before they can be hurt
    private static final int SPAWN_PROTECTION = 61;

    private BarbarianTests() {
    }

    private static ServerPlayer barbarian(GameTestHelper helper, Map<Upgrade, Integer> ranks) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Progression.setForTests(player, 30, PlayerClass.BARBARIAN, ranks);
        Mana.add(player, Mana.MAX);
        return player;
    }

    // Iron Skin: 25% less damage taken and 20% slower
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ironSkinIsToughButSlow(GameTestHelper helper) {
        ServerPlayer player = barbarian(helper, Map.of());
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Progression.setForTests(player, 30, PlayerClass.BARBARIAN, Map.of(Upgrade.IRON_SKIN, 1));
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * 0.8) < 1e-6,
                "Jernhud gav " + player.getAttributeValue(Attributes.MOVEMENT_SPEED) + " fart, ikke " + speed * 0.8);
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    player.setHealth(player.getMaxHealth());
                    player.hurt(player.damageSources().generic(), 10);
                    float taken = player.getMaxHealth() - player.getHealth();
                    helper.assertTrue(Math.abs(taken - 7.5f) < 0.01, "Jernhud tog " + taken + " skade, ikke 7,5");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // Berserker Rage: 30% more damage below half health, and nothing extra above
    @GameTest(template = "empty")
    public static void berserkerRageBelowHalfHealth(GameTestHelper helper) {
        ServerPlayer player = barbarian(helper, Map.of(Upgrade.BERSERKER_RAGE, 1));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 2));
        husk.setNoAi(true);
        husk.hurt(player.damageSources().playerAttack(player), 4);
        float full = husk.getMaxHealth() - husk.getHealth();
        husk.setHealth(husk.getMaxHealth());
        husk.invulnerableTime = 0;
        player.setHealth(player.getMaxHealth() * 0.4f);
        husk.hurt(player.damageSources().playerAttack(player), 4);
        float low = husk.getMaxHealth() - husk.getHealth();
        helper.assertTrue(full > 0 && Math.abs(low - full * 1.3f) < 0.01, "Bersærkergang gav " + low + " skade, ikke " + full * 1.3f);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Call of Blood: health potions do nothing
    @GameTest(template = "empty")
    public static void callOfBloodStopsHealthPotions(GameTestHelper helper) {
        ServerPlayer player = barbarian(helper, Map.of(Upgrade.CALL_OF_BLOOD, 1));
        player.setHealth(4);
        Skills.use(player, Skill.HEALTH_POTION);
        helper.assertTrue(player.getHealth() == 4, "Livs-potionen helede med Blodets kald: " + player.getHealth());
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Light on Your Feet: Leap's cooldown is 40% shorter
    @GameTest(template = "empty")
    public static void lightFeetShortensLeap(GameTestHelper helper) {
        ServerPlayer plain = barbarian(helper, Map.of(Upgrade.LEAP, 1));
        ServerPlayer light = barbarian(helper, Map.of(Upgrade.LEAP, 1, Upgrade.LEAP_LIGHT, 1));
        // Looking ahead, since Leap needs a direction
        plain.setYRot(0);
        light.setYRot(0);
        Skills.use(plain, Skill.LEAP);
        Skills.use(light, Skill.LEAP);
        String tag = "morkeriget_" + Skill.LEAP.name().toLowerCase(Locale.ROOT) + "_ready";
        long now = helper.getLevel().getGameTime();
        long plainCooldown = plain.getPersistentData().getLong(tag) - now;
        long lightCooldown = light.getPersistentData().getLong(tag) - now;
        helper.assertTrue(plainCooldown > 0 && Math.abs(lightCooldown - Math.round(plainCooldown * Skills.LIGHT_FEET_COOLDOWN)) <= 1,
                "Let på tå gav " + lightCooldown + " ticks cooldown, uden den " + plainCooldown);
        TestPlayers.remove(plain);
        TestPlayers.remove(light);
        helper.succeed();
    }

    // Bleeding Wounds: monsters hit by Whirlwind keep losing health afterwards
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void whirlwindBleeds(GameTestHelper helper) {
        ServerPlayer player = barbarian(helper, Map.of(Upgrade.WHIRLWIND, 1, Upgrade.WHIRLWIND_BLEED, 1));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 2));
        husk.setNoAi(true);
        float[] afterHit = new float[1];
        helper.startSequence()
                .thenExecute(() -> {
                    Skills.use(player, Skill.WHIRLWIND);
                    afterHit[0] = husk.getHealth();
                })
                .thenExecuteFor(45, () -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    helper.assertTrue(husk.getHealth() < afterHit[0], "Hvirvelvinden fik ikke monsteret til at bløde");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }
}
