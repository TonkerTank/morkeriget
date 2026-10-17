package com.morkeriget.loot;

import com.morkeriget.jewelry.Jewelry;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;

// What kind of loot an item is. Decides which affixes it can roll and where it has to be worn for them to count
public enum ItemKind {
    MELEE, RANGED, ARMOR, SHIELD, JEWELRY;

    public static ItemKind of(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem) {
            return ARMOR;
        }
        if (stack.getItem() instanceof ShieldItem) {
            return SHIELD;
        }
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return RANGED;
        }
        if (Jewelry.isJewelry(stack)) {
            return JEWELRY;
        }
        return MELEE;
    }
}
