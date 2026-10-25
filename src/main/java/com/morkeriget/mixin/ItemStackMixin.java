package com.morkeriget.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Like in Diablo, a player's things don't wear out from being used: weapons, armor and tools only lose durability when
// the player dies, see Durability. Every other way an item takes damage ends up here, so it is stopped at the start
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void morkeriget$noWear(int damage, ServerLevel level, LivingEntity entity, Consumer<Item> onBreak, CallbackInfo info) {
        if (entity instanceof Player) {
            info.cancel();
        }
    }
}
