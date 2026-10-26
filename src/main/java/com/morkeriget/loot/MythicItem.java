package com.morkeriget.loot;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.morkeriget.Text;
import com.morkeriget.combat.PlayerHits;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.progress.PlayerClass;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

// The mythic items, like the uber uniques in Diablo: each one is a named item with its own strong power. They only drop
// from bosses and high rifts. The power is saved on the item and checked with has(), see MythicEffects
public enum MythicItem {
    // Names and descriptions are in the language files, under mythic.<name>
    DOOMBRINGER(() -> Items.NETHERITE_SWORD, null),
    STARFALL(() -> Items.BOW, PlayerClass.RANGER),
    JESTERS_CROWN(() -> Items.NETHERITE_HELMET, null),
    ARCHANGELS_MAIL(() -> Items.NETHERITE_CHESTPLATE, null),
    STARLESS_RING(Jewelry.RING::get, null),
    ANCESTORS_AMULET(Jewelry.AMULET::get, null);

    private static final String MYTHIC_TAG = "morkeriget_mythic";
    // How often a mythic is one the finder's class can use, if there are any only for that class
    private static final double OWN_CLASS_CHANCE = 0.7;

    // The rings and amulets are our own items, which don't exist until they are registered
    private final Supplier<Item> baseItem;
    // null for the ones everyone can use
    public final PlayerClass playerClass;

    MythicItem(Supplier<Item> baseItem, PlayerClass playerClass) {
        this.baseItem = baseItem;
        this.playerClass = playerClass;
    }

    public MutableComponent displayName() {
        return Text.tr("mythic." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent description() {
        return Text.tr("mythic." + name().toLowerCase(Locale.ROOT) + ".desc");
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
