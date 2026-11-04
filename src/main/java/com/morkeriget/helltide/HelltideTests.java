package com.morkeriget.helltide;

import com.morkeriget.Morkeriget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Where a helltide is put. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class HelltideTests {
    private HelltideTests() {
    }

    // With sea to the west (x below 0) the helltide always ends up on land, with its edge out of the sea too. With
    // sea everywhere there is no helltide
    @GameTest(template = "empty")
    public static void helltideStaysOutOfWater(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 200; i++) {
            BlockPos center = Helltide.findCenter(random, BlockPos.ZERO, pos -> pos.getX() < 0);
            helper.assertTrue(center == null || center.getX() >= 48, "Helltiden kom til at ligge i vand ved " + center);
        }
        helper.assertTrue(Helltide.findCenter(random, BlockPos.ZERO, pos -> true) == null, "Helltiden startede, selvom alt var vand");
        helper.succeed();
    }
}
