package com.morkeriget.merchant;

import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Only the player's inventory, so you can see what you bought. The ware buttons in MerchantScreen are sent to the
// server as menu button clicks with the ware's number, so no extra packets are needed
public class MerchantMenu extends AbstractContainerMenu {
    // Where the inventory is drawn, used by MerchantScreen too
    static final int INVENTORY_Y = 136;

    public MerchantMenu(int id, Inventory inventory) {
        super(Merchant.MENU.get(), id);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || id < 0 || id >= Ware.values().length) {
            return false;
        }
        return buy(serverPlayer, Ware.values()[id]);
    }

    private static boolean buy(ServerPlayer player, Ware ware) {
        if (player.getInventory().countItem(Smithy.ESSENCE.get()) < ware.price) {
            player.displayClientMessage(Component.literal("Du har brug for " + ware.price + " essens"), true);
            return false;
        }
        RandomSource random = player.getRandom();
        double roll = random.nextDouble();
        Rarity rarity = roll < Merchant.LEGENDARY_CHANCE ? Rarity.LEGENDARY
                : roll < Merchant.LEGENDARY_CHANCE + Merchant.RARE_CHANCE ? Rarity.RARE : Rarity.MAGIC;
        ItemStack bought = Loot.createOfKind(random, rarity, Progression.level(player), Progression.playerClass(player), ware.kind());
        if (bought.isEmpty()) {
            return false;
        }
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), item -> item.is(Smithy.ESSENCE.get()), ware.price, false);
        player.displayClientMessage(Component.literal("Du købte ").append(bought.getHoverName()), true);
        player.getInventory().placeItemBackInInventory(bought);
        if (rarity == Rarity.LEGENDARY) {
            player.sendSystemMessage(Component.literal("Købmanden havde en legendarisk genstand! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(bought.getHoverName()));
            player.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        } else {
            player.playNotifySound(SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return true;
    }

    // Nothing to shift-click things into
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
