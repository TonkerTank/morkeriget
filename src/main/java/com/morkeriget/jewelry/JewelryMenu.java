package com.morkeriget.jewelry;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

// The jewelry slots at the top and the player's normal inventory below, so jewelry can be moved in and out
public class JewelryMenu extends AbstractContainerMenu {
    // Where the slots are drawn, used by JewelryScreen too
    static final int JEWELRY_Y = 26;
    static final int INVENTORY_Y = 84;

    // Used on the client. The real slots live on the server and the game keeps the two in sync
    public JewelryMenu(int id, Inventory inventory) {
        this(id, inventory, new JewelryHandler(null));
    }

    public JewelryMenu(int id, Inventory inventory, IItemHandler jewelry) {
        super(Jewelry.MENU.get(), id);
        // Ring, ring, then a gap and the amulet
        addSlot(new JewelrySlot(jewelry, 0, 53, JEWELRY_Y));
        addSlot(new JewelrySlot(jewelry, 1, 71, JEWELRY_Y));
        addSlot(new JewelrySlot(jewelry, 2, 107, JEWELRY_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    // Shift-click: jewelry goes to the inventory, and rings and amulets from the inventory go into a free slot
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < Jewelry.SLOTS
                ? moveItemStackTo(stack, Jewelry.SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, Jewelry.SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    private static class JewelrySlot extends SlotItemHandler {
        JewelrySlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return JewelryHandler.fits(getSlotIndex(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
