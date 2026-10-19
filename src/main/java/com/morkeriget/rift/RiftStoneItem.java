package com.morkeriget.rift;

import java.util.List;

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

    // "Riftsten III"
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).append(" " + Rifts.roman(Rifts.tier(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int tier = Rifts.tier(stack);
        lines.add(Component.literal("Højreklik for at åbne en rift").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("Overlev " + Rifts.WAVES + " bølger og dræb riftens vogter").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("for en sæt-genstand, ekstra loot, essens og XP").withStyle(ChatFormatting.GRAY));
        if (tier > 1) {
            lines.add(Component.literal("Niveau " + Rifts.roman(tier) + ": monstre +" + (tier - 1) + " level, flere fjender og bedre loot")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        List<RiftAffix> affixes = RiftAffix.of(stack);
        for (RiftAffix affix : affixes) {
            lines.add(Component.literal("⚠ " + affix.displayName + ": ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(affix.description).withStyle(ChatFormatting.GRAY)));
        }
        if (!affixes.isEmpty()) {
            lines.add(Component.literal("+" + affixes.size() + " ekstra genstande og +" + Math.round(affixes.size() * RiftAffix.REWARD_PER_AFFIX * 100)
                    + "% essens, XP og chance for mytisk").withStyle(ChatFormatting.GOLD));
        }
        if (tier < Rifts.MAX_TIER) {
            lines.add(Component.literal("Luk riften for at få en riftsten " + Rifts.roman(tier + 1)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
