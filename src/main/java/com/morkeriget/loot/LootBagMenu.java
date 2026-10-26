package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.morkeriget.Text;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

// The loot bag as a chest, with two buttons in LootBagScreen: sort it, and salvage everything up to a rarity right here
// without going to the smithy. The buttons are sent as menu button clicks, so no extra packets are needed
public class LootBagMenu extends ChestMenu {
    public static final int SORT = 0;
    // Salvaging sends this plus the best rarity to take, e.g. SALVAGE + RARE.ordinal()
    public static final int SALVAGE = 10;

    private final Container bag;

    // On the client, which only needs room for what the server sends
    public LootBagMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(LootBag.ROWS * 9));
    }

    LootBagMenu(int id, Inventory inventory, Container bag) {
        super(LootBag.MENU.get(), id, inventory, bag, LootBag.ROWS);
        this.bag = bag;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == SORT) {
            sort();
            serverPlayer.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0f, 1.0f);
            return true;
        }
        if (id >= SALVAGE && id <= SALVAGE + Rarity.SET.ordinal()) {
            return salvage(serverPlayer, Rarity.values()[id - SALVAGE]);
        }
        return false;
    }

    // Favorites first, then the best rarity and the highest item power, so the good things are at the top
    private void sort() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            if (!bag.getItem(slot).isEmpty()) {
                items.add(bag.getItem(slot));
            }
        }
        items.sort(Comparator.<ItemStack, Boolean>comparing(Favorites::is).reversed()
                .thenComparing(stack -> Loot.getRarity(stack) != null ? Loot.getRarity(stack).ordinal() : -1, Comparator.reverseOrder())
                .thenComparing(Loot::itemPower, Comparator.reverseOrder())
                .thenComparing(stack -> stack.getHoverName().getString()));
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            bag.setItem(slot, slot < items.size() ? items.get(slot) : ItemStack.EMPTY);
        }
    }

    // Only the bag, never the inventory. See Smithy.salvage for what is skipped
    private boolean salvage(ServerPlayer player, Rarity best) {
        int[] result = new int[2];
        Smithy.salvage(bag, 0, bag.getContainerSize(), best, result);
        if (result[0] == 0) {
            player.displayClientMessage(Text.tr("bag.nothing_to_salvage"), true);
            return false;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(Smithy.ESSENCE.get(), result[1]));
        player.displayClientMessage(Text.tr("smithy.salvaged", result[0], result[1])
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.playNotifySound(SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }
}
