package com.morkeriget.uber;

import java.util.List;

import com.morkeriget.dungeon.Dungeons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Right-click to fight Mørkets Herre. It is only used up if the arena really opens
public class UberSummonItem extends Item {
    public UberSummonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!Dungeons.startUber(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Højreklik for at kalde Mørkets Herre").withStyle(ChatFormatting.DARK_PURPLE));
        lines.add(Component.literal("Spillets stærkeste boss, med tre faser").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Spillere tæt på dig kommer med").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Høj chance for mytiske genstande").withStyle(ChatFormatting.GOLD));
    }
}
