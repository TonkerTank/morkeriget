package com.morkeriget.elite;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// How likely a monster is to spawn as an elite, see Elites.eliteChance. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class EliteSpawnTests {
    private EliteSpawnTests() {
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 2, z + 0.5));
        zombie.moveTo(at.x, at.y, at.z, 0, 0);
        return zombie;
    }

    // Under the open sky the chance is higher than under a roof, which is how a cave counts
    @GameTest(template = "empty")
    public static void surfaceHasMoreElites(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 5, 1), Blocks.STONE);
        double roofed = Elites.eliteChance(zombie(helper, 1, 1));
        // The test area has a ceiling, so the open one stands on top of whatever is highest there
        Zombie onTop = zombie(helper, 3, 3);
        int top = helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, onTop.getBlockX(), onTop.getBlockZ());
        onTop.moveTo(onTop.getX(), top, onTop.getZ(), 0, 0);
        double open = Elites.eliteChance(onTop);
        helper.assertTrue(roofed == Config.ELITE_CHANCE.get(), "Under et tag skulle chancen være den normale, men var " + roofed);
        helper.assertTrue(open == Math.min(1, Config.ELITE_CHANCE.get() * Config.SURFACE_ELITE_MULTIPLIER.get()),
                "Under åben himmel skulle chancen være ganget op, men var " + open);
        helper.succeed();
    }
}
