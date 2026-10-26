package com.morkeriget.dummy;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

// Right-click a block to put a training dummy on it, facing you. It is only used up if there was room
public class TrainingDummyItem extends Item {
    public TrainingDummyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() + 180 : 0;
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                || TrainingDummy.spawn(level, Vec3.atBottomCenterOf(pos), yaw) == null) {
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Højreklik på jorden for at stille den op").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Slå på den for at se din skade pr. sekund").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Sneak og højreklik med tom hånd for at tage den ned").withStyle(ChatFormatting.DARK_GRAY));
    }
}
