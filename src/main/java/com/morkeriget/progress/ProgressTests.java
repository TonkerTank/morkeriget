package com.morkeriget.progress;

import com.morkeriget.Morkeriget;
import com.morkeriget.gametest.TestPlayers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class ProgressTests {
    private static final ResourceLocation TEST_SPEED = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "test_speed");

    private ProgressTests() {
    }

    // A class gives its starter weapon once: picking it again gives nothing, and another class gives its own
    @GameTest(template = "empty")
    public static void starterWeaponOncePerClass(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        StarterWeapons.give(player, PlayerClass.BARBARIAN);
        int swords = player.getInventory().countItem(Items.IRON_SWORD);
        StarterWeapons.give(player, PlayerClass.BARBARIAN);
        int again = player.getInventory().countItem(Items.IRON_SWORD);
        StarterWeapons.give(player, PlayerClass.RANGER);
        int bows = player.getInventory().countItem(Items.BOW);
        helper.assertTrue(swords == 1 && again == 1, "Barbaren fik " + swords + " og så " + again + " sværd, ikke 1 og 1");
        helper.assertTrue(bows == 1, "Rangeren fik " + bows + " buer, ikke 1");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // The starter weapon isn't given again after the player dies and respawns
    @GameTest(template = "empty")
    public static void starterWeaponNotAgainAfterDeath(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        StarterWeapons.give(player, PlayerClass.BARBARIAN);
        player.getInventory().clearContent();
        player.setHealth(0);
        ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        StarterWeapons.give(respawned, PlayerClass.BARBARIAN);
        int swords = respawned.getInventory().countItem(Items.IRON_SWORD);
        helper.assertTrue(swords == 0, "Barbaren fik sværdet igen efter døden");
        TestPlayers.remove(respawned);
        helper.succeed();
    }

    // Lots of speed is taken down to 2.5 times the normal, and the cap goes away again with the speed
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void speedIsCapped(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double normal = speed.getValue();
        speed.addTransientModifier(new AttributeModifier(TEST_SPEED, 5.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        helper.startSequence()
                .thenExecuteFor(21, () -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    helper.assertTrue(speed.getValue() <= speed.getBaseValue() * 2.5 + 1e-9,
                            "Farten er " + speed.getValue() / speed.getBaseValue() + " gange den normale");
                    speed.removeModifier(TEST_SPEED);
                })
                .thenExecuteFor(21, () -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(speed.getValue() - normal) < 1e-9, "Grænsen blev ikke fjernet igen, farten er " + speed.getValue());
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }
}
