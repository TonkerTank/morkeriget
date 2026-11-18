package com.morkeriget.guide;

import com.morkeriget.Morkeriget;
import com.morkeriget.feedback.Feedback;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.smithy.Smithy;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class GuideTests {
    private GuideTests() {
    }

    // A new player starts on the first step, goes through them in order, gets essence for each and a rare item at the
    // end, and then the feedback window is sent
    @GameTest(template = "empty")
    public static void guideStepsInOrder(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        helper.assertTrue(Guide.step(player) == 0, "En ny spiller startede ikke på guidens første trin");
        for (GuideStep step : GuideStep.values()) {
            for (int i = 0; i < step.goal; i++) {
                helper.assertTrue(Guide.step(player) == step.ordinal(), "Guiden var ikke på trinnet " + step);
                Guide.done(player, step);
            }
        }
        helper.assertTrue(Guide.step(player) == Guide.DONE, "Guiden blev ikke færdig efter alle trin");
        int essence = player.getInventory().countItem(Smithy.ESSENCE.get());
        helper.assertTrue(essence == 5 * GuideStep.values().length, "Forkert mængde essens fra guiden: " + essence);
        boolean rare = false;
        for (ItemStack stack : player.getInventory().items) {
            rare |= Loot.getRarity(stack) == Rarity.RARE;
        }
        helper.assertTrue(rare, "Ingen sjælden ting efter guiden");
        helper.assertFalse(Feedback.firstTime(player), "Feedback-vinduet blev ikke sendt, da guiden var klaret");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Doing a later step early doesn't count, and a step that needs more than one only moves on at the goal
    @GameTest(template = "empty")
    public static void guideOnlyCountsCurrentStep(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Guide.done(player, GuideStep.SALVAGE);
        helper.assertTrue(Guide.step(player) == 0, "Et senere trin talte, før det var dets tur");
        while (Guide.step(player) < GuideStep.KILL.ordinal()) {
            Guide.done(player, GuideStep.values()[Guide.step(player)]);
        }
        Guide.done(player, GuideStep.KILL);
        helper.assertTrue(Guide.step(player) == GuideStep.KILL.ordinal() && Guide.count(player) == 1,
                "Ét drab skulle tælle 1 af " + GuideStep.KILL.goal);
        TestPlayers.remove(player);
        helper.succeed();
    }
}
