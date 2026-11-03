package com.morkeriget.feedback;

import com.morkeriget.Morkeriget;
import com.morkeriget.gametest.TestPlayers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class FeedbackTests {
    private FeedbackTests() {
    }

    // Joining the world sends the feedback window, and after that it doesn't come again
    @GameTest(template = "empty")
    public static void feedbackShownOnce(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        helper.assertFalse(Feedback.firstTime(player), "Feedback-vinduet blev ikke sendt, da spilleren kom ind");
        helper.assertFalse(Feedback.firstTime(player), "Feedback-vinduet kom igen");
        TestPlayers.remove(player);
        helper.succeed();
    }
}
