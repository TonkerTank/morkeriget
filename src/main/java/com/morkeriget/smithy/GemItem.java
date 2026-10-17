package com.morkeriget.smithy;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// A gem. The name and bonus depend on the tier saved on the stack
public class GemItem extends Item {
    private final Gem gem;

    public GemItem(Gem gem, Properties properties) {
        super(properties);
        this.gem = gem;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(gem.name(Gems.tier(stack))).withColor(gem.color);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int tier = Gems.tier(stack);
        lines.add(Component.literal(gem.bonus(tier)).withColor(gem.color));
        lines.add(Component.literal("Kvalitet " + tier + "/" + Gem.MAX_TIER).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Sæt i en sokkel i Smedjen (K)").withStyle(ChatFormatting.DARK_GRAY));
        if (tier < Gem.MAX_TIER) {
            lines.add(Component.literal("3 ens kan kombineres til en bedre").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
