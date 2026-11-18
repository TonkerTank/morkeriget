package com.morkeriget.elite;

import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.gametest.TestPlayers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The Arcane, Mortar and Plagued elites, see EliteAttacks. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class EliteAttackTests {
    // Players need this long after they join before they can be hurt
    private static final int SPAWN_PROTECTION = 61;

    private EliteAttackTests() {
    }

    // An elite with only this modifier that stands still, with the player as its target
    private static Husk elite(GameTestHelper helper, EliteModifier modifier, ServerPlayer target) {
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        Elites.markChecked(husk);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        husk.moveTo(at.x, at.y, at.z, 0, 0);
        husk.setNoAi(true);
        helper.getLevel().addFreshEntity(husk);
        Elites.makeElite(husk, 0, List.of());
        modifier.apply(husk);
        husk.setTarget(target);
        return husk;
    }

    // The beams sweep all the way around, so a player standing close by is hit sooner or later
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void arcaneBeamHits(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(4, 2, 1));
        Husk husk = elite(helper, EliteModifier.ARCANE, player);
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> player.setHealth(player.getMaxHealth()))
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth(), "Arcane-strålen ramte ikke spilleren"))
                .thenExecute(() -> {
                    husk.discard();
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // A player who keeps away gets a blast under them
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void mortarHitsFarPlayer(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(9, 2, 1));
        Husk husk = elite(helper, EliteModifier.MORTAR, player);
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> player.setHealth(player.getMaxHealth()))
                .thenWaitUntil(() -> helper.assertTrue(player.getHealth() < player.getMaxHealth(), "Mortar-eksplosionen ramte ikke spilleren"))
                .thenExecute(() -> {
                    husk.discard();
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // Armored elites wear iron armour you can see
    @GameTest(template = "empty")
    public static void armoredEliteWearsArmor(GameTestHelper helper) {
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        Elites.markChecked(husk);
        EliteModifier.ARMORED.apply(husk);
        helper.assertTrue(husk.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "Armored-eliten har ingen brystplade på");
        helper.succeed();
    }

    // Monsters reach less far than in Minecraft: a player 1.3 blocks away is out of reach, one 0.9 blocks away is not
    @GameTest(template = "empty")
    public static void monstersReachLessFar(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(1, 2, 1));
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        Elites.markChecked(husk);
        Vec3 at = player.position();
        husk.moveTo(at.x + 1.3, at.y, at.z, 0, 0);
        helper.assertFalse(husk.isWithinMeleeAttackRange(player), "Husken kan slå en spiller 1,3 blokke væk");
        husk.moveTo(at.x + 0.9, at.y, at.z, 0, 0);
        helper.assertTrue(husk.isWithinMeleeAttackRange(player), "Husken kan ikke slå en spiller 0,9 blokke væk");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Monsters in the pack don't get the leader's Arcane, Mortar, Plagued or Electrified
    @GameTest(template = "empty")
    public static void newModifiersStayWithLeader(GameTestHelper helper) {
        for (EliteModifier modifier : List.of(EliteModifier.ARCANE, EliteModifier.MORTAR, EliteModifier.PLAGUED, EliteModifier.ELECTRIFIED)) {
            helper.assertFalse(modifier.sharedWithPack(), modifier + " gives videre til flokken");
        }
        helper.assertTrue(EliteModifier.BURNING.sharedWithPack(), "Burning gives ikke længere videre til flokken");
        helper.succeed();
    }
}
