package com.morkeriget.smithy;

import java.util.List;

import com.morkeriget.Text;

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
        return gem.name(Gems.tier(stack)).withColor(gem.color);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int tier = Gems.tier(stack);
        lines.add(gem.bonus(tier).withColor(gem.color));
        lines.add(Text.tr("gem.quality", tier, Gem.MAX_TIER).withStyle(ChatFormatting.GRAY));
        lines.add(Text.tr("gem.how_to_socket").withStyle(ChatFormatting.DARK_GRAY));
        if (tier < Gem.MAX_TIER) {
            lines.add(Text.tr("gem.how_to_combine").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
