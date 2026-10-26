package com.morkeriget.dungeon;

import java.util.List;

import com.morkeriget.Text;
import com.morkeriget.rift.RiftAffix;
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
            if (!Dungeons.start(serverPlayer, Dungeons.tier(stack), RiftAffix.of(stack))) {
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // "Dungeon Key III"
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).append(" " + Rifts.roman(Dungeons.tier(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int tier = Dungeons.tier(stack);
        lines.add(Text.tr("key.tooltip.use").withStyle(ChatFormatting.DARK_RED));
        lines.add(Text.tr("tooltip.party").withStyle(ChatFormatting.GRAY));
        lines.add(Text.tr("key.tooltip.goal").withStyle(ChatFormatting.GRAY));
        lines.add(Text.tr("key.tooltip.themes").withStyle(ChatFormatting.GRAY));
        lines.add(Text.tr("key.tooltip.lives", Dungeon.LIVES).withStyle(ChatFormatting.GRAY));
        if (tier > 1) {
            lines.add(Text.tr("key.tooltip.tier", Rifts.roman(tier), tier - 1).withStyle(ChatFormatting.RED));
        }
        List<RiftAffix> affixes = RiftAffix.of(stack);
        for (RiftAffix affix : affixes) {
            lines.add(affix.warning());
        }
        if (!affixes.isEmpty()) {
            lines.add(RiftAffix.rewardLine(affixes.size()));
        }
        if (tier < Dungeons.MAX_TIER) {
            lines.add(Text.tr("key.tooltip.next", Rifts.roman(tier + 1)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
