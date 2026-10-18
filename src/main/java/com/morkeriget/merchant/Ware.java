package com.morkeriget.merchant;

import java.util.function.Predicate;

import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.loot.ItemKind;
import com.morkeriget.loot.Staffs;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// What the merchant sells: a random item of one kind, and what it costs in essence
public enum Ware {
    WEAPON("Våben", 15),
    STAFF("Stav", 15),
    RANGED("Bue", 15),
    HELMET("Hjelm", 12),
    CHEST("Brystplade", 12),
    LEGS("Bukser", 12),
    BOOTS("Støvler", 12),
    SHIELD("Skjold", 12),
    RING("Ring", 20),
    AMULET("Amulet", 20);

    public final String displayName;
    public final int price;

    Ware(String displayName, int price) {
        this.displayName = displayName;
        this.price = price;
    }

    // Which base items count as this ware
    Predicate<Item> kind() {
        return item -> switch (this) {
            case WEAPON -> ItemKind.of(new ItemStack(item)) == ItemKind.MELEE && !Staffs.isStaff(new ItemStack(item));
            case STAFF -> Staffs.isStaff(new ItemStack(item));
            case RANGED -> ItemKind.of(new ItemStack(item)) == ItemKind.RANGED;
            case HELMET -> isArmor(item, EquipmentSlot.HEAD);
            case CHEST -> isArmor(item, EquipmentSlot.CHEST);
            case LEGS -> isArmor(item, EquipmentSlot.LEGS);
            case BOOTS -> isArmor(item, EquipmentSlot.FEET);
            case SHIELD -> ItemKind.of(new ItemStack(item)) == ItemKind.SHIELD;
            case RING -> item == Jewelry.RING.get();
            case AMULET -> item == Jewelry.AMULET.get();
        };
    }

    private static boolean isArmor(Item item, EquipmentSlot slot) {
        return item instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
    }
}
