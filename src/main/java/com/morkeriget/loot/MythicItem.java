package com.morkeriget.loot;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.morkeriget.combat.PlayerHits;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.progress.PlayerClass;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

// The mythic items, like the uber uniques in Diablo: each one is a named item with its own strong power. They only drop
// from bosses and high rifts. The power is saved on the item and checked with has(), see MythicEffects
public enum MythicItem {
    DOOMBRINGER("Dommebringeren", () -> Items.NETHERITE_SWORD, null,
            "Hvert 4. slag sender en skyggebølge ud, der gør 150% af slagets skade på fjender omkring dig"),
    STARFALL("Stjernefaldet", () -> Items.BOW, PlayerClass.RANGER,
            "Dine pile springer videre til 2 fjender i nærheden og gør 75% skade på dem"),
    JESTERS_CROWN("Narrens Krone", () -> Items.NETHERITE_HELMET, null,
            "Alle skills har 25% kortere cooldown og koster 25% mindre mana"),
    ARCHANGELS_MAIL("Ærkeenglens Brynje", () -> Items.NETHERITE_CHESTPLATE, null,
            "Du tager 30% mindre skade fra bosser og elites og heler 1% liv hvert sekund"),
    STARLESS_RING("Den Stjerneløse Ring", Jewelry.RING::get, null,
            "Når du dræber en elite eller en boss, er alle dine skills klar igen med det samme"),
    ANCESTORS_AMULET("Forfædrenes Amulet", Jewelry.AMULET::get, null,
            "Du gør 40% mere skade, men har 20% mindre liv");

    private static final String MYTHIC_TAG = "morkeriget_mythic";
    // How often a mythic is one the finder's class can use, if there are any only for that class
    private static final double OWN_CLASS_CHANCE = 0.7;

    public final String displayName;
    // The rings and amulets are our own items, which don't exist until they are registered
    private final Supplier<Item> baseItem;
    // null for the ones everyone can use
    public final PlayerClass playerClass;
    public final String description;

    MythicItem(String displayName, Supplier<Item> baseItem, PlayerClass playerClass, String description) {
        this.displayName = displayName;
        this.baseItem = baseItem;
        this.playerClass = playerClass;
        this.description = description;
    }

    public Item baseItem() {
        return baseItem.get();
    }

    // Mostly one the finder can use. Items for another class only drop for them 30% of the time
    public static MythicItem roll(RandomSource random, PlayerClass forClass) {
        List<MythicItem> possible = Arrays.asList(values());
        if (forClass != null && random.nextDouble() < OWN_CLASS_CHANCE) {
            possible = possible.stream().filter(mythic -> mythic.playerClass == null || mythic.playerClass == forClass).toList();
        }
        return possible.get(random.nextInt(possible.size()));
    }

    // The mythic the item is, or null
    public static MythicItem of(ItemStack stack) {
        String name = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(MYTHIC_TAG);
        for (MythicItem mythic : values()) {
            if (mythic.name().equals(name)) {
                return mythic;
            }
        }
        return null;
    }

    // Whether the player wears it or has it in their hand
    public static boolean has(ServerPlayer player, MythicItem mythic) {
        for (ItemStack stack : PlayerHits.equippedItems(player)) {
            if (of(stack) == mythic) {
                return true;
            }
        }
        return false;
    }

    public void markOn(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(MYTHIC_TAG, name()));
    }
}
