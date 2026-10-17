package com.morkeriget.smithy;

import java.util.List;

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

// A slot for the item and a slot for a gem at the top, the player's normal inventory below. The buttons in SmithyScreen
// are sent to the server as menu button clicks, so no extra packets are needed
public class SmithyMenu extends AbstractContainerMenu {
    public static final int SALVAGE = 0;
    public static final int REROLL = 1;
    public static final int SOCKET = 2;
    public static final int UNSOCKET = 3;
    public static final int SALVAGE_ALL_COMMON = 4;
    public static final int SALVAGE_ALL_MAGIC = 5;
    public static final int COMBINE = 6;
    // What it costs to take the gems out of an item
    public static final int UNSOCKET_COST = 3;
    // Where the slots are drawn, used by SmithyScreen too
    static final int ITEM_X = 62;
    static final int GEM_X = 98;
    static final int SLOT_Y = 20;
    static final int INVENTORY_Y = 136;

    private final SimpleContainer input = new SimpleContainer(2);

    public SmithyMenu(int id, Inventory inventory) {
        super(Smithy.MENU.get(), id);
        addSlot(new Slot(input, 0, ITEM_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Loot.getRarity(stack) != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(input, 1, GEM_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Gem.of(stack) != null;
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

    ItemStack gem() {
        return slots.get(1).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == SALVAGE_ALL_COMMON || id == SALVAGE_ALL_MAGIC) {
            return salvageAll(serverPlayer, id == SALVAGE_ALL_COMMON ? Rarity.COMMON : Rarity.MAGIC);
        }
        if (id == COMBINE) {
            return combine(serverPlayer);
        }
        ItemStack stack = input.getItem(0);
        Rarity rarity = Loot.getRarity(stack);
        if (rarity == null) {
            return false;
        }
        return switch (id) {
            case SALVAGE -> salvage(serverPlayer, stack, rarity);
            case REROLL -> reroll(serverPlayer, stack, rarity);
            case SOCKET -> socket(serverPlayer, stack);
            case UNSOCKET -> unsocket(serverPlayer, stack);
            default -> false;
        };
    }

    // Gems in the item are given back
    private boolean salvage(ServerPlayer player, ItemStack stack, Rarity rarity) {
        giveBack(player, Gems.removeAll(stack));
        input.setItem(0, ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(new ItemStack(Smithy.ESSENCE.get(), Smithy.salvageValue(rarity)));
        player.playNotifySound(SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    private boolean reroll(ServerPlayer player, ItemStack stack, Rarity rarity) {
        int cost = Smithy.rerollCost(rarity);
        if (cost == 0) {
            player.displayClientMessage(Component.literal("Almindelige genstande har ingen bonusser at re-rolle"), true);
            return false;
        }
        if (!pay(player, cost)) {
            return false;
        }
        input.setItem(0, Loot.reroll(stack, player.getRandom()));
        player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    private boolean socket(ServerPlayer player, ItemStack stack) {
        Gem gem = Gem.of(input.getItem(1));
        if (gem == null) {
            player.displayClientMessage(Component.literal("Læg en ædelsten i det højre felt"), true);
            return false;
        }
        if (!Gems.insert(stack, gem, Gems.tier(input.getItem(1)))) {
            player.displayClientMessage(Component.literal("Genstanden har ingen ledige sokler"), true);
            return false;
        }
        input.getItem(1).shrink(1);
        input.setChanged();
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    private boolean unsocket(ServerPlayer player, ItemStack stack) {
        if (!Gems.hasGems(stack)) {
            player.displayClientMessage(Component.literal("Der er ingen ædelsten i genstanden"), true);
            return false;
        }
        if (!pay(player, UNSOCKET_COST)) {
            return false;
        }
        giveBack(player, Gems.removeAll(stack));
        input.setChanged();
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    // Salvages every item of this rarity in the inventory. Skips the item in your hand and items with gems in them,
    // so nothing you use or have put work into disappears
    private boolean salvageAll(ServerPlayer player, Rarity rarity) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (slot != inventory.selected && Loot.getRarity(stack) == rarity && !Gems.hasGems(stack)) {
                inventory.items.set(slot, ItemStack.EMPTY);
                count++;
            }
        }
        if (count == 0) {
            player.displayClientMessage(Component.literal("Ingen " + rarity.displayName.toLowerCase() + "e genstande at smelte om"), true);
            return false;
        }
        int essence = count * Smithy.salvageValue(rarity);
        inventory.placeItemBackInInventory(new ItemStack(Smithy.ESSENCE.get(), essence));
        player.displayClientMessage(Component.literal("Smeltede " + count + " genstande om til " + essence + " essens"), true);
        player.playNotifySound(SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    private static boolean pay(ServerPlayer player, int cost) {
        if (player.getInventory().countItem(Smithy.ESSENCE.get()) < cost) {
            player.displayClientMessage(Component.literal("Du har brug for " + cost + " essens"), true);
            return false;
        }
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), item -> item.is(Smithy.ESSENCE.get()), cost, false);
        return true;
    }

    private static void giveBack(ServerPlayer player, List<ItemStack> gems) {
        for (ItemStack gem : gems) {
            player.getInventory().placeItemBackInInventory(gem);
        }
    }

    // Three gems of the same kind and tier in the gem slot become one of the next tier
    private boolean combine(ServerPlayer player) {
        ItemStack gems = input.getItem(1);
        Gem gem = Gem.of(gems);
        if (gem == null || gems.getCount() < 3) {
            player.displayClientMessage(Component.literal("Læg mindst 3 ens ædelsten i det højre felt"), true);
            return false;
        }
        int tier = Gems.tier(gems);
        if (tier >= Gem.MAX_TIER) {
            player.displayClientMessage(Component.literal("Den er allerede perfekt"), true);
            return false;
        }
        if (!pay(player, Smithy.combineCost(tier))) {
            return false;
        }
        gems.shrink(3);
        ItemStack better = Gems.stack(gem, tier + 1);
        if (gems.isEmpty()) {
            input.setItem(1, better);
        } else {
            input.setChanged();
            player.getInventory().placeItemBackInInventory(better);
        }
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.0f + tier * 0.15f);
        return true;
    }

    // The item and gem go back to the player when the smithy is closed
    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, input);
    }

    // Shift-click: things in the smithy go to the inventory, and loot and gems from the inventory go into their slot
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < 2) {
            moved = moveItemStackTo(stack, 2, slots.size(), true);
        } else if (slots.get(0).mayPlace(stack)) {
            moved = moveItemStackTo(stack, 0, 1, false);
        } else if (slots.get(1).mayPlace(stack)) {
            moved = moveItemStackTo(stack, 1, 2, false);
        } else {
            moved = false;
        }
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
