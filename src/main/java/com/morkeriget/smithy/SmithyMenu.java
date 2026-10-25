package com.morkeriget.smithy;

import java.util.List;

import com.morkeriget.loot.Favorites;
import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.LootBag;
import com.morkeriget.loot.Masterwork;
import com.morkeriget.loot.Rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// A slot for the item and a slot for a gem at the top, the player's normal inventory below. The buttons in SmithyScreen
// are sent to the server as menu button clicks, so no extra packets are needed
public class SmithyMenu extends AbstractContainerMenu {
    public static final int SALVAGE = 0;
    public static final int REROLL = 1;
    public static final int SOCKET = 2;
    public static final int UNSOCKET = 3;
    public static final int EXTRACT = 4;
    public static final int MASTERWORK = 5;
    public static final int COMBINE = 6;
    // "Salvage all" sends this plus the best rarity to salvage, e.g. SALVAGE_ALL + RARE.ordinal()
    public static final int SALVAGE_ALL = 10;
    // Putting a power from the codex on the item sends this plus the power, e.g. IMPRINT + MAELSTROM.ordinal()
    public static final int IMPRINT = 40;
    // The codex is sent to the screen as bits, 8 in each data slot since they are sent as shorts
    private static final int CODEX_SLOTS = (LegendaryPower.values().length + 7) / 8;
    // What it costs to take the gems out of an item
    public static final int UNSOCKET_COST = 3;
    private static final String FAVORITE_MESSAGE = "Favoritter kan ikke smeltes - fjern stjernen med Alt+klik";
    // Where the slots are drawn, used by SmithyScreen too
    static final int ITEM_X = 62;
    static final int GEM_X = 98;
    static final int SLOT_Y = 20;
    static final int INVENTORY_Y = 156;

    private final SimpleContainer input = new SimpleContainer(2);
    private final ContainerData codex;

    public SmithyMenu(int id, Inventory inventory) {
        super(Smithy.MENU.get(), id);
        if (inventory.player instanceof ServerPlayer player) {
            codex = new ContainerData() {
                @Override
                public int get(int index) {
                    return (Codex.mask(player) >> (8 * index)) & 0xFF;
                }

                @Override
                public void set(int index, int value) {
                }

                @Override
                public int getCount() {
                    return CODEX_SLOTS;
                }
            };
        } else {
            codex = new SimpleContainerData(CODEX_SLOTS);
        }
        addDataSlots(codex);
        addSlot(new Slot(input, 0, ITEM_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Smithy.salvageRarity(stack) != null;
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

    // Whether the player has the power in their codex, for the screen
    boolean knows(LegendaryPower power) {
        return ((codex.get(power.ordinal() / 8) >> (power.ordinal() % 8)) & 1) == 1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id >= SALVAGE_ALL && id <= SALVAGE_ALL + Rarity.SET.ordinal()) {
            return salvageAll(serverPlayer, Rarity.values()[id - SALVAGE_ALL]);
        }
        if (id == COMBINE) {
            return combine(serverPlayer);
        }
        ItemStack stack = input.getItem(0);
        Rarity rarity = Smithy.salvageRarity(stack);
        if (rarity == null) {
            return false;
        }
        if (id >= IMPRINT && id < IMPRINT + LegendaryPower.values().length) {
            return imprint(serverPlayer, stack, rarity, LegendaryPower.values()[id - IMPRINT]);
        }
        return switch (id) {
            case SALVAGE -> salvage(serverPlayer, stack, rarity);
            case REROLL -> reroll(serverPlayer, stack, rarity);
            case SOCKET -> socket(serverPlayer, stack);
            case UNSOCKET -> unsocket(serverPlayer, stack);
            case EXTRACT -> extract(serverPlayer, stack);
            case MASTERWORK -> masterwork(serverPlayer, stack);
            default -> false;
        };
    }

    // Takes the power out of the item and into the codex. The item is used up, but its gems are given back
    private boolean extract(ServerPlayer player, ItemStack stack) {
        if (Favorites.is(stack)) {
            player.displayClientMessage(Component.literal(FAVORITE_MESSAGE), true);
            return false;
        }
        LegendaryPower power = LegendaryPower.of(stack);
        if (power == null) {
            player.displayClientMessage(Component.literal("Genstanden har ingen legendarisk kraft"), true);
            return false;
        }
        if (Codex.has(player, power)) {
            player.displayClientMessage(Component.literal(power.displayName + " er allerede i din kraftbog"), true);
            return false;
        }
        giveBack(player, Gems.removeAll(stack));
        input.setItem(0, ItemStack.EMPTY);
        Codex.add(player, power);
        player.displayClientMessage(Component.literal(power.displayName + " er nu i din kraftbog").withStyle(ChatFormatting.GOLD), true);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.8f);
        player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // Puts a power from the codex on a legendary, in place of the one it had
    private boolean imprint(ServerPlayer player, ItemStack stack, Rarity rarity, LegendaryPower power) {
        if (rarity != Rarity.LEGENDARY) {
            player.displayClientMessage(Component.literal("Kun legendariske genstande kan få en kraft"), true);
            return false;
        }
        if (!Codex.has(player, power) || LegendaryPower.of(stack) == power || !pay(player, Codex.IMPRINT_COST)) {
            return false;
        }
        Loot.setPower(stack, power);
        input.setChanged();
        player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    // Gems in the item are given back
    private boolean salvage(ServerPlayer player, ItemStack stack, Rarity rarity) {
        if (Favorites.is(stack)) {
            player.displayClientMessage(Component.literal(FAVORITE_MESSAGE), true);
            return false;
        }
        giveBack(player, Gems.removeAll(stack));
        input.setItem(0, ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(new ItemStack(Smithy.ESSENCE.get(), Smithy.salvageValue(rarity)));
        player.playNotifySound(SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    // Makes all the item's bonuses stronger, see Masterwork
    private boolean masterwork(ServerPlayer player, ItemStack stack) {
        if (!Masterwork.canUpgrade(stack)) {
            player.displayClientMessage(Component.literal(Masterwork.rank(stack) >= Masterwork.MAX_RANK
                    ? "Genstanden er allerede fuldt mesterværk" : "Genstanden har ingen bonusser at forbedre"), true);
            return false;
        }
        if (!pay(player, Masterwork.cost(stack))) {
            return false;
        }
        Component bigStep = Masterwork.upgrade(stack, player.getRandom());
        input.setChanged();
        String rank = "Mesterværk " + Masterwork.rank(stack) + "/" + Masterwork.MAX_RANK;
        if (bigStep != null) {
            player.displayClientMessage(Component.literal(rank + " - stort spring: ").append(bigStep.copy()).withStyle(ChatFormatting.GOLD), true);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        } else {
            player.displayClientMessage(Component.literal(rank).withStyle(ChatFormatting.GOLD), true);
        }
        player.playNotifySound(SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.0f + Masterwork.rank(stack) * 0.04f);
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

    // Salvages every item up to and including this rarity in the loot bag (see LootBag) and the inventory above the
    // hotbar. The hotbar and what you wear are never touched. Set items only go when "Sæt" is picked, mythic items never,
    // and favorites and items with gems in them are skipped too, so nothing you have put work into disappears
    private boolean salvageAll(ServerPlayer player, Rarity best) {
        Container bag = LootBag.get(player);
        int[] result = new int[2];
        Smithy.salvage(bag, 0, bag.getContainerSize(), best, result);
        Smithy.salvage(player.getInventory(), Inventory.getSelectionSize(), player.getInventory().items.size(), best, result);
        int count = result[0];
        int essence = result[1];
        if (count == 0) {
            player.displayClientMessage(Component.literal("Ingen genstande at smelte om i loot-tasken (I) eller inventaret"), true);
            return false;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(Smithy.ESSENCE.get(), essence));
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
