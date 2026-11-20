package com.morkeriget.combat;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Elites;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.loot.MythicItem;
import com.morkeriget.loot.Staffs;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The legendary powers and mythics added in 0.5.0, see LegendaryEffects and MythicEffects. The items are plain armour
// with only the power on them, so no random bonus changes the numbers. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class PowerTests {
    private PowerTests() {
    }

    private static Husk husk(GameTestHelper helper, double x, double z) {
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        Elites.markChecked(husk);
        Vec3 at = helper.absoluteVec(new Vec3(x, 2, z));
        husk.moveTo(at.x, at.y, at.z, 0, 0);
        husk.setNoAi(true);
        helper.getLevel().addFreshEntity(husk);
        return husk;
    }

    private static void wear(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        player.setItemSlot(slot, stack);
    }

    // Executioner: the same hit does 40% more to a monster that is almost dead
    @GameTest(template = "empty")
    public static void executionerHitsHarder(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(1, 2, 1));
        Husk without = husk(helper, 3.5, 1.5);
        Husk with = husk(helper, 3.5, 4.5);
        without.setHealth(5);
        with.setHealth(5);
        without.hurt(player.damageSources().playerAttack(player), 2);
        float plain = 5 - without.getHealth();
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        LegendaryPower.EXECUTIONER.markOn(helmet);
        wear(player, EquipmentSlot.HEAD, helmet);
        with.hurt(player.damageSources().playerAttack(player), 2);
        float executed = 5 - with.getHealth();
        helper.assertTrue(Math.abs(executed - plain * 1.4f) < 0.05f, "Bøddel gav " + executed + " skade, ikke 1,4 x " + plain);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Chain Reaction: killing a monster hurts the one standing next to it
    @GameTest(template = "empty")
    public static void chainReactionHurtsNeighbours(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(1, 2, 1));
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        LegendaryPower.CHAIN_REACTION.markOn(helmet);
        wear(player, EquipmentSlot.HEAD, helmet);
        Husk first = husk(helper, 4.5, 4.5);
        Husk second = husk(helper, 5.5, 4.5);
        first.hurt(player.damageSources().playerAttack(player), 1000);
        helper.assertTrue(second.getHealth() < second.getMaxHealth(), "Kædereaktion ramte ikke monsteret ved siden af");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Winter's Grasp: a monster close by loses health and is slowed without being hit
    @GameTest(template = "empty")
    public static void wintersGraspHurtsNearby(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(1, 2, 1));
        ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
        MythicItem.WINTERS_GRASP.markOn(leggings);
        wear(player, EquipmentSlot.LEGS, leggings);
        Husk husk = husk(helper, 3.5, 1.5);
        for (int i = 0; i < 21; i++) {
            TestPlayers.tick(player);
        }
        helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Vinterens Greb skadede ikke monsteret tæt på");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Bloodreaver: a kill gives Fury, and the next hit does 6% more
    @GameTest(template = "empty")
    public static void bloodreaverFuryFromKills(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(1, 2, 1));
        ItemStack axe = new ItemStack(Items.NETHERITE_AXE);
        MythicItem.BLOODREAVER.markOn(axe);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        Husk without = husk(helper, 3.5, 1.5);
        without.hurt(player.damageSources().playerAttack(player), 2);
        float plain = without.getMaxHealth() - without.getHealth();
        Husk victim = husk(helper, 5.5, 1.5);
        victim.hurt(player.damageSources().playerAttack(player), 1000);
        helper.assertTrue(MythicEffects.fury(player) == 1, "Blodhøsteren gav ikke Raseri ved drab, men " + MythicEffects.fury(player));
        Husk with = husk(helper, 3.5, 4.5);
        with.hurt(player.damageSources().playerAttack(player), 2);
        float furious = with.getMaxHealth() - with.getHealth();
        helper.assertTrue(Math.abs(furious - plain * 1.06f) < 0.05f, "Raseri gav " + furious + " skade, ikke 1,06 x " + plain);
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Eye of the Tempest: using a skill hurts the nearest monster
    @GameTest(template = "empty")
    public static void eyeOfTheTempestStrikesOnSkill(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(1, 2, 1));
        ItemStack staff = new ItemStack(Staffs.STAR.get());
        MythicItem.EYE_OF_THE_TEMPEST.markOn(staff);
        player.setItemInHand(InteractionHand.MAIN_HAND, staff);
        Husk husk = husk(helper, 5.5, 1.5);
        MythicEffects.skillUsed(player);
        helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "Stormens Øje ramte ikke monsteret");
        TestPlayers.remove(player);
        helper.succeed();
    }
}
