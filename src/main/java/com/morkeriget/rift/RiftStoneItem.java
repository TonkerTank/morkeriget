package com.morkeriget.rift;

import java.util.List;

import com.morkeriget.Text;

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

// Right-click to open a rift of the stone's tier where you stand. The stone is only used up if the rift really opens
public class RiftStoneItem extends Item {
    public RiftStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!Rifts.start(serverPlayer, Rifts.tier(stack), RiftAffix.of(stack))) {
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // "Rift Stone III"
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).append(" " + Rifts.roman(Rifts.tier(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int tier = Rifts.tier(stack);
        lines.add(Text.tr("stone.tooltip.use").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Text.tr("stone.tooltip.goal", Rifts.WAVES).withStyle(ChatFormatting.GRAY));
        lines.add(Text.tr("stone.tooltip.rewards").withStyle(ChatFormatting.GRAY));
        if (tier > 1) {
            lines.add(Text.tr("stone.tooltip.tier", Rifts.roman(tier), tier - 1).withStyle(ChatFormatting.DARK_PURPLE));
        }
        List<RiftAffix> affixes = RiftAffix.of(stack);
        for (RiftAffix affix : affixes) {
            lines.add(affix.warning());
        }
        if (!affixes.isEmpty()) {
            lines.add(RiftAffix.rewardLine(affixes.size()));
        }
        if (tier < Rifts.MAX_TIER) {
            lines.add(Text.tr("stone.tooltip.next", Rifts.roman(tier + 1)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
