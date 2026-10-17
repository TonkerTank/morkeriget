package com.morkeriget.jewelry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.items.ItemStackHandler;

// The player's jewelry slots: the first ones take rings, the last one takes an amulet
public class JewelryHandler extends ItemStackHandler {
    // Jewelry can have at most this many stat bonuses (a legendary has 4 affixes)
    static final int MAX_BONUSES = 4;

    // The player this belongs to, or null for the empty copy the client uses to show the menu
    private final IAttachmentHolder holder;

    public JewelryHandler(IAttachmentHolder holder) {
        super(Jewelry.SLOTS);
        this.holder = holder;
    }

    public static boolean fits(int slot, ItemStack stack) {
        return slot < Jewelry.RING_SLOTS ? stack.is(Jewelry.RING.get()) : stack.is(Jewelry.AMULET.get());
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return fits(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (holder instanceof ServerPlayer player) {
            Jewelry.applyStats(player);
        }
    }
}
