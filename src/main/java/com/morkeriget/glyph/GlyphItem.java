package com.morkeriget.glyph;

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

// A glyph. Its name says which kind and level, and right-clicking puts it on the paragon board, see Glyphs.socket
public class GlyphItem extends Item {
    public GlyphItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Glyphs.Socketed glyph = Glyphs.of(stack);
        if (glyph == null) {
            return super.getName(stack);
        }
        return Component.literal("Glyf: " + glyph.glyph().displayName + " (" + glyph.level() + ")").withStyle(glyph.glyph().color);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!Glyphs.socket(serverPlayer, stack)) {
                return InteractionResultHolder.fail(stack);
            }
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Glyphs.Socketed glyph = Glyphs.of(stack);
        if (glyph == null) {
            return;
        }
        lines.add(Component.literal(glyph.glyph().describe(glyph.level())).withStyle(ChatFormatting.BLUE));
        lines.add(Component.literal("Højreklik for at sætte den på paragon-tavlen").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Stiger et level for hvert dungeon, du klarer, op til 2 pr. tier").withStyle(ChatFormatting.DARK_GRAY));
    }
}
