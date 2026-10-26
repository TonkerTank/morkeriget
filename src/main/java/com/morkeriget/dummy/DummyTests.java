package com.morkeriget.dummy;

import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Elites;
import com.morkeriget.gametest.TestPlayers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The training dummy. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class DummyTests {
    private DummyTests() {
    }

    private static Husk dummy(GameTestHelper helper) {
        return TrainingDummy.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(5.5, 2, 5.5)), 0);
    }

    // It never dies, counts what the player does to it and isn't an elite
    @GameTest(template = "empty")
    public static void dummyCountsDamageAndNeverDies(GameTestHelper helper) {
        Husk dummy = dummy(helper);
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        helper.assertFalse(Elites.isElite(dummy), "Træningsdukken blev en elite");
        dummy.hurt(player.damageSources().playerAttack(player), 5000);
        helper.assertTrue(dummy.isAlive() && dummy.getHealth() == dummy.getMaxHealth(), "Træningsdukken døde eller blev ikke helet");
        double first = TrainingDummy.total(dummy, player);
        helper.assertTrue(first > 0, "Træningsdukken talte ikke slaget");
        dummy.invulnerableTime = 0;
        dummy.hurt(player.damageSources().playerAttack(player), 10);
        helper.assertTrue(TrainingDummy.total(dummy, player) > first, "Træningsdukken talte ikke det andet slag");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // What a player's wolf does counts for the player
    @GameTest(template = "empty")
    public static void dummyCountsPets(GameTestHelper helper) {
        Husk dummy = dummy(helper);
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        wolf.tame(player);
        dummy.hurt(dummy.damageSources().mobAttack(wolf), 6);
        helper.assertTrue(TrainingDummy.total(dummy, player) > 0, "Ulvens skade tæller ikke for spilleren");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Burning has no attacker, but counts for the player who set it on fire
    @GameTest(template = "empty")
    public static void dummyCountsBurning(GameTestHelper helper) {
        Husk dummy = dummy(helper);
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        dummy.hurt(player.damageSources().playerAttack(player), 1);
        double hit = TrainingDummy.total(dummy, player);
        dummy.invulnerableTime = 0;
        dummy.hurt(dummy.damageSources().onFire(), 4);
        helper.assertTrue(TrainingDummy.total(dummy, player) > hit, "Ilden tæller ikke for spilleren, der satte ild til");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // /kill still gets rid of it
    @GameTest(template = "empty")
    public static void dummyCanBeKilled(GameTestHelper helper) {
        Husk dummy = dummy(helper);
        dummy.kill();
        helper.assertTrue(dummy.isDeadOrDying(), "/kill fjernede ikke træningsdukken");
        helper.succeed();
    }

    // The item puts one up and is used up, and sneaking with an empty hand takes it down and gives the item back
    @GameTest(template = "empty")
    public static void dummyItemPlacesAndTakesDown(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TrainingDummy.ITEM.get()));
        helper.placeAt(player, player.getMainHandItem(), new BlockPos(5, 0, 5), Direction.UP);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Genstanden blev ikke brugt op");
        List<Husk> placed = helper.getLevel().getEntitiesOfClass(Husk.class, helper.getBounds(), TrainingDummy::isDummy);
        helper.assertTrue(placed.size() == 1, "Genstanden stillede " + placed.size() + " træningsdukker op");
        Husk dummy = placed.get(0);
        player.setShiftKeyDown(true);
        CommonHooks.onInteractEntity(player, dummy, InteractionHand.MAIN_HAND);
        helper.assertTrue(dummy.isRemoved(), "Træningsdukken blev ikke taget ned");
        helper.assertTrue(player.getInventory().countItem(TrainingDummy.ITEM.get()) == 1, "Spilleren fik ikke træningsdukken tilbage");
        TestPlayers.remove(player);
        helper.succeed();
    }
}
