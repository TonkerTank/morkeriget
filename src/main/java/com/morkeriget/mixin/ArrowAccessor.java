package com.morkeriget.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// How many monsters an arrow goes through is private, since vanilla only sets it from the Piercing enchantment. The
// ranger's Pierce variant of Multishot uses it
@Mixin(AbstractArrow.class)
public interface ArrowAccessor {
    @Invoker("setPierceLevel")
    void morkeriget$setPierceLevel(byte level);
}
