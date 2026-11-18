package com.morkeriget.gametest;

import com.morkeriget.elite.Elites;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;

// Monsters for the game tests. Like GameTestHelper.spawn, but marked as checked before they join the world, so they
// never roll to become an elite or a boss. That would change the damage they do and take, and make tests fail now and then
public final class TestMonsters {
    private TestMonsters() {
    }

    public static Husk husk(GameTestHelper helper, BlockPos pos) {
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        Elites.markChecked(husk);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
        husk.moveTo(at.x, at.y, at.z, 0, 0);
        helper.getLevel().addFreshEntity(husk);
        return husk;
    }
}
