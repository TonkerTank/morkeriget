package com.morkeriget.smithy;

import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// One slot for the item at the top and the player's normal inventory below. The two buttons in SmithyScreen
// are sent to the server as menu button clicks, so no extra packets are needed
public class SmithyMenu extends AbstractContainerMenu {
    public static final int SALVAGE = 0;
    public static final int REROLL = 1;
    // Where the slots are drawn, used by SmithyScreen too
    static final int ITEM_X = 80;
    static final int ITEM_Y = 22;
    static final int INVENTORY_Y = 84;

    private final SimpleContainer input = new SimpleContainer(1);

    public SmithyMenu(int id, Inventory inventory) {
        super(Smithy.MENU.get(), id);
        addSlot(new Slot(input, 0, ITEM_X, ITEM_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Loot.getRarity(stack) != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    ItemStack item() {
        return slots.get(0).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack stack = input.getItem(0);
        Rarity rarity = Loot.getRarity(stack);
        if (rarity == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == SALVAGE) {
            input.setItem(0, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(new ItemStack(Smithy.ESSENCE.get(), Smithy.salvageValue(rarity)));
            serverPlayer.playNotifySound(SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            return true;
        }
        if (id == REROLL) {
            int cost = Smithy.rerollCost(rarity);
            if (cost == 0) {
                serverPlayer.displayClientMessage(Component.literal("Almindelige genstande har ingen bonusser at re-rolle"), true);
                return false;
            }
            if (player.getInventory().countItem(Smithy.ESSENCE.get()) < cost) {
                serverPlayer.displayClientMessage(Component.literal("Du har brug for " + cost + " essens"), true);
                return false;
            }
            ContainerHelper.clearOrCountMatchingItems(player.getInventory(), item -> item.is(Smithy.ESSENCE.get()), cost, false);
            input.setItem(0, Loot.reroll(stack, player.getRandom()));
            serverPlayer.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            return true;
        }
        return false;
    }

    // The item goes back to the player when the smithy is closed
    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, input);
    }

    // Shift-click: the item goes to the inventory, and loot from the inventory goes into the smithy slot
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index == 0
                ? moveItemStackTo(stack, 1, slots.size(), true)
                : slots.get(0).mayPlace(stack) && moveItemStackTo(stack, 0, 1, false);
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
}
