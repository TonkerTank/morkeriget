package com.morkeriget.dungeon;

import java.util.List;

import com.morkeriget.rift.Rifts;

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

// Right-click to open a dungeon of the key's tier. The key is only used up if the dungeon really opens
public class DungeonKeyItem extends Item {
    public DungeonKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!Dungeons.start(serverPlayer, Dungeons.tier(stack))) {
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // "Dungeonnøgle III"
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).append(" " + Rifts.roman(Dungeons.tier(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int tier = Dungeons.tier(stack);
        lines.add(Component.literal("Højreklik for at åbne et dungeon").withStyle(ChatFormatting.DARK_RED));
        lines.add(Component.literal("Spillere tæt på dig kommer med").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Bryd seglet og dræb bossen i det sidste rum").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("I har " + Dungeon.LIVES + " liv tilsammen").withStyle(ChatFormatting.GRAY));
        if (tier > 1) {
            lines.add(Component.literal("Niveau " + Rifts.roman(tier) + ": monstre +" + (tier - 1) + " level, flere rum og bedre loot")
                    .withStyle(ChatFormatting.RED));
        }
        if (tier < Dungeons.MAX_TIER) {
            lines.add(Component.literal("Klar dungeonet for at få en nøgle " + Rifts.roman(tier + 1)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
