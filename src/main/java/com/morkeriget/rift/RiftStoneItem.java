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

// Right-click to open a rift where you stand. The stone is only used up if the rift really opens
public class RiftStoneItem extends Item {
    public RiftStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!Rifts.start(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Højreklik for at åbne en rift").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("Overlev " + Rifts.WAVES + " bølger og dræb riftens vogter").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("for en sæt-genstand, ekstra loot, essens og XP").withStyle(ChatFormatting.GRAY));
    }
}
