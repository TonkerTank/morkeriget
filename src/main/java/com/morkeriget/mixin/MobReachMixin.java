package com.morkeriget.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.morkeriget.Config;
import com.morkeriget.skill.Minions;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

// Minecraft lets mobs hit you about 0.83 blocks past their own body, which feels like zombies reach you from too far
// away. For hostile monsters this takes it down to the "meleeReach" setting in the config. Bigger monsters, like bosses,
// still reach further, since their body is bigger. The player's own minions keep Minecraft's reach
@Mixin(Mob.class)
public abstract class MobReachMixin {
    private static final double VANILLA_REACH = Math.sqrt(2.04F) - 0.6F;

    @Inject(method = "getAttackBoundingBox", at = @At("RETURN"), cancellable = true)
    private void morkeriget$shorterReach(CallbackInfoReturnable<AABB> info) {
        if ((Object) this instanceof Enemy && !Minions.isAlly((Mob) (Object) this)) {
            double cut = VANILLA_REACH - Config.MONSTER_REACH.get();
            if (cut != 0) {
                info.setReturnValue(info.getReturnValue().inflate(-cut, 0, -cut));
            }
        }
    }
}
