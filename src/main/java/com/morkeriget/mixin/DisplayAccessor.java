package com.morkeriget.mixin;

import com.mojang.math.Transformation;

import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// The setters on display entities are private, since vanilla only sets them from commands. The skills use them to
// show things like the paladin's hammer and pillars of light, see SkillDisplays
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void morkeriget$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void morkeriget$setInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void morkeriget$setInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void morkeriget$setPosRotInterpolationDuration(int ticks);

    @Invoker("setBrightnessOverride")
    void morkeriget$setBrightnessOverride(Brightness brightness);

    @Invoker("setBillboardConstraints")
    void morkeriget$setBillboardConstraints(Display.BillboardConstraints constraints);

    @Invoker("setGlowColorOverride")
    void morkeriget$setGlowColorOverride(int color);
}
