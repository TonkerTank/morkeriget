package com.morkeriget.skill;

import java.util.List;

import com.morkeriget.Morkeriget;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

// Summons get better gear every RANKS_PER_TIER ranks in their skill, so you can see them grow: the necromancer's
// skeletons go from a stone sword to full netherite, and the ranger's wolves get wolf armour in better and better
// colours. Both get a little bigger with each tier too. Server only
final class SummonGear {
    private static final int RANKS_PER_TIER = 5;
    private static final int MAX_TIER = 4;
    private static final ResourceLocation SIZE = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "summon_size");

    private static final List<Item> SWORDS = List.of(Items.STONE_SWORD, Items.IRON_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
    // Helmet, chestplate, leggings and boots for each tier. Tier 0 has nothing, and tier 1 only the top half
    private static final List<List<Item>> ARMOR = List.of(
            List.of(),
            List.of(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE),
            List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
            List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
            List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS));
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    // The wolf armour colour for each tier from 1: leather brown, iron grey, diamond blue and netherite black
    private static final int[] WOLF_COLORS = {0x8B5A2B, 0xB8B8B8, 0x4AEDD9, 0x3A2E36};

    private SummonGear() {
    }

    // 0 to MAX_TIER
    static int tier(int rank) {
        return Math.min(MAX_TIER, rank / RANKS_PER_TIER);
    }

    static void equip(WitherSkeleton skeleton, int rank) {
        int tier = tier(rank);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, shiny(new ItemStack(SWORDS.get(tier)), tier));
        List<Item> armor = ARMOR.get(tier);
        for (int i = 0; i < armor.size(); i++) {
            skeleton.setItemSlot(ARMOR_SLOTS.get(i), shiny(new ItemStack(armor.get(i)), tier));
        }
        neverDrop(skeleton);
        grow(skeleton, tier);
    }

    static void equip(Wolf wolf, int rank) {
        int tier = tier(rank);
        if (tier > 0) {
            ItemStack armor = new ItemStack(Items.WOLF_ARMOR);
            armor.set(DataComponents.DYED_COLOR, new DyedItemColor(WOLF_COLORS[tier - 1], false));
            wolf.setBodyArmorItem(shiny(armor, tier));
        }
        neverDrop(wolf);
        grow(wolf, tier);
    }

    // The best tier glints like an enchanted item
    private static ItemStack shiny(ItemStack stack, int tier) {
        if (tier >= MAX_TIER) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    // Summons that die or go away must not leave their gear behind
    private static void neverDrop(Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0);
        }
    }

    // 8% bigger for each tier
    private static void grow(Mob mob, int tier) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null && tier > 0) {
            scale.addOrUpdateTransientModifier(new AttributeModifier(SIZE, 0.08 * tier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
