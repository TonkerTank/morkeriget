package com.morkeriget.merchant;

import java.util.Locale;
import java.util.function.Predicate;

import com.morkeriget.Text;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.loot.ItemKind;
import com.morkeriget.loot.Staffs;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// What the merchant sells: a random item of one kind, and what it costs in essence
public enum Ware {
    // Names are in the language files, under ware.<name>
    WEAPON(15),
    STAFF(15),
    RANGED(15),
    HELMET(12),
    CHEST(12),
    LEGS(12),
    BOOTS(12),
    SHIELD(12),
    RING(20),
    AMULET(20);

    public final int price;

    Ware(int price) {
        this.price = price;
    }

    public MutableComponent displayName() {
        return Text.tr("ware." + name().toLowerCase(Locale.ROOT));
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
