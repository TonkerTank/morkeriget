package com.morkeriget.jewelry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.items.ItemStackHandler;

// The player's jewelry slots: the first ones take rings, the last ones take amulets
public class JewelryHandler extends ItemStackHandler {
    // Jewelry can have at most this many stat bonuses (a legendary has 4 affixes, and there can be 2 gems)
    static final int MAX_BONUSES = 6;

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

    // Saves from before there were more slots had two rings and an amulet. Loading them would shrink the slots back to
    // three, so they are put into the new slots, with the amulet in the first amulet slot
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        if (stacks.size() != Jewelry.SLOTS) {
            NonNullList<ItemStack> old = stacks;
            stacks = NonNullList.withSize(Jewelry.SLOTS, ItemStack.EMPTY);
            for (int slot = 0; slot < old.size(); slot++) {
                ItemStack stack = old.get(slot);
                int target = stack.is(Jewelry.AMULET.get()) ? firstEmpty(Jewelry.RING_SLOTS, Jewelry.SLOTS) : firstEmpty(0, Jewelry.RING_SLOTS);
                if (!stack.isEmpty() && target >= 0) {
                    stacks.set(target, stack);
                }
            }
        }
    }

    private int firstEmpty(int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (stacks.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (holder instanceof ServerPlayer player) {
            Jewelry.applyStats(player);
        }
    }
}
