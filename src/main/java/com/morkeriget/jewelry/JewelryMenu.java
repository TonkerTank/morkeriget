package com.morkeriget.jewelry;

import com.mojang.datafixers.util.Pair;
import com.morkeriget.loot.ItemKind;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Staffs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

// The equipment screen, like the character screen in Diablo: armor, weapon and shield, rings and amulets at the top,
// and the player's normal inventory below. The weapon slot is the first hotbar slot, since Minecraft always fights with
// what is in your hand, so it only takes weapons
public class JewelryMenu extends AbstractContainerMenu {
    // Where the slots are drawn, used by JewelryScreen too
    static final int RINGS_Y = 32;
    static final int AMULETS_Y = 64;
    static final int JEWELRY_X = 98;
    static final int ARMOR_X = 8;
    static final int HANDS_X = 30;
    static final int INVENTORY_Y = 106;
    static final int HOTBAR_Y = INVENTORY_Y + 58;
    static final int WEAPON_SLOT = 0;

    // The order of the slots in the menu
    private static final int ARMOR_START = Jewelry.SLOTS;
    private static final int OFFHAND = ARMOR_START + 4;
    private static final int WEAPON = OFFHAND + 1;
    private static final int EQUIPMENT_END = WEAPON + 1;
    private static final int INVENTORY_END = EQUIPMENT_END + 27;

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
    static final ResourceLocation WEAPON_ICON = ResourceLocation.withDefaultNamespace("item/empty_slot_sword");

    private final Player owner;

    // Used on the client. The real slots live on the server and the game keeps the two in sync
    public JewelryMenu(int id, Inventory inventory) {
        this(id, inventory, new JewelryHandler(null));
    }

    public JewelryMenu(int id, Inventory inventory, IItemHandler jewelry) {
        super(Jewelry.MENU.get(), id);
        Player player = inventory.player;
        owner = player;
        // Rings in a row, then the amulets in the row below
        for (int i = 0; i < Jewelry.RING_SLOTS; i++) {
            addSlot(new JewelrySlot(jewelry, i, JEWELRY_X + i * 18, RINGS_Y));
        }
        for (int i = 0; i < Jewelry.AMULET_SLOTS; i++) {
            addSlot(new JewelrySlot(jewelry, Jewelry.RING_SLOTS + i, JEWELRY_X + i * 18, AMULETS_Y));
        }
        // Armor down the left side, from the helmet to the boots, and the hands next to it
        for (int i = 0; i < ARMOR.length; i++) {
            addSlot(new ArmorSlot(inventory, player, ARMOR[i], ARMOR_ICONS[i], ARMOR_X, 18 + i * 18));
        }
        addSlot(new Slot(inventory, Inventory.SLOT_OFFHAND, HANDS_X, 54) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });
        addSlot(new Slot(inventory, WEAPON_SLOT, HANDS_X, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isWeapon(stack);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, WEAPON_ICON);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        // The first hotbar slot is the weapon slot above, so it isn't shown twice
        for (int column = 1; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    public static boolean isWeapon(ItemStack stack) {
        if (Loot.getRarity(stack) != null && !Staffs.isStaff(stack)) {
            ItemKind kind = ItemKind.of(stack);
            return kind == ItemKind.MELEE || kind == ItemKind.RANGED;
        }
        return Staffs.isStaff(stack) || stack.getItem() instanceof TieredItem || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem || stack.getItem() instanceof MaceItem;
    }

    // Shift-click: equipment goes to the inventory, and from the inventory gear goes to a free slot it fits in, while
    // everything else moves between the inventory and the hotbar like in the normal inventory
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < EQUIPMENT_END) {
            moved = moveItemStackTo(stack, EQUIPMENT_END, slots.size(), false);
        } else if (stack.getItem() instanceof ShieldItem && moveItemStackTo(stack, OFFHAND, OFFHAND + 1, false)) {
            moved = true;
        } else if (fitsEquipment(stack) && moveItemStackTo(stack, 0, OFFHAND, false)) {
            moved = true;
        } else if (isWeapon(stack) && moveItemStackTo(stack, WEAPON, WEAPON + 1, false)) {
            moved = true;
        } else if (index < INVENTORY_END) {
            moved = moveItemStackTo(stack, INVENTORY_END, slots.size(), false);
        } else {
            moved = moveItemStackTo(stack, EQUIPMENT_END, INVENTORY_END, false);
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

    // Jewelry or armor, which have slots of their own
    private boolean fitsEquipment(ItemStack stack) {
        for (int i = 0; i < OFFHAND; i++) {
            if (slots.get(i).mayPlace(stack)) {
                return true;
            }
        }
        return false;
    }

    // The stats next to the screen are sent again twice a second, so they change as soon as gear is swapped
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            EquipmentStats.send(player);
        }
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

    // Like the armor slots in the normal inventory: only armor for that part of the body, and Curse of Binding stays on
    private static class ArmorSlot extends Slot {
        private final Player player;
        private final EquipmentSlot equipmentSlot;
        private final ResourceLocation icon;

        ArmorSlot(Inventory inventory, Player player, EquipmentSlot equipmentSlot, ResourceLocation icon, int x, int y) {
            super(inventory, 36 + equipmentSlot.getIndex(), x, y);
            this.player = player;
            this.equipmentSlot = equipmentSlot;
            this.icon = icon;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.canEquip(equipmentSlot, player);
        }

        @Override
        public boolean mayPickup(Player picker) {
            ItemStack stack = getItem();
            return (stack.isEmpty() || picker.isCreative() || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                    && super.mayPickup(picker);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            player.onEquipItem(equipmentSlot, oldStack, newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }
    }
}
