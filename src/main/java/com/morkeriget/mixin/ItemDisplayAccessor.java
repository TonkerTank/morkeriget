package com.morkeriget.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// See DisplayAccessor
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
    @Invoker("setItemStack")
    void morkeriget$setItemStack(ItemStack stack);

    @Invoker("setItemTransform")
    void morkeriget$setItemTransform(ItemDisplayContext context);
}
