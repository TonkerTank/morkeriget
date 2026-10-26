package com.morkeriget.loot;

import java.util.List;
import java.util.Locale;

import com.morkeriget.Text;
import com.morkeriget.jewelry.Jewelry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

// Set items: each set has four pieces, and wearing two or four of them gives extra bonuses (see SetBonuses).
// A set item is a normal piece of loot with the SET rarity and the set and piece saved on it
public enum ItemSet {
    // Names and bonuses are in the language files, under set.<name>
    GUARDIAN(List.of(Piece.HELMET, Piece.CHEST, Piece.LEGS, Piece.BOOTS)),
    // A caster's set, so its weapon is a staff. Swords from before that still count as the fourth piece
    STORMCALLER(List.of(Piece.HELMET, Piece.STAFF, Piece.RING, Piece.AMULET)),
    BLOODHUNTER(List.of(Piece.WEAPON, Piece.CHEST, Piece.BOOTS, Piece.RING));

    private static final String SET_TAG = "morkeriget_set";
    private static final String PIECE_TAG = "morkeriget_set_piece";

    // A place a set piece is worn. The base item gets better with the item level, like normal loot
    public enum Piece {
        HELMET, CHEST, LEGS, BOOTS, WEAPON, STAFF, RING, AMULET;

        public MutableComponent displayName() {
            return Text.tr("piece." + name().toLowerCase(Locale.ROOT));
        }

        Item baseItem(int itemLevel) {
            return switch (this) {
                case HELMET -> tier(itemLevel, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET);
                case CHEST -> tier(itemLevel, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE);
                case LEGS -> tier(itemLevel, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS);
                case BOOTS -> tier(itemLevel, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS);
                case WEAPON -> tier(itemLevel, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
                case STAFF -> tier(itemLevel, Staffs.OAK.get(), Staffs.BLAZE.get(), Staffs.STORM.get(), Staffs.STAR.get());
                case RING -> Jewelry.RING.get();
                case AMULET -> Jewelry.AMULET.get();
            };
        }

        // The same level steps as normal loot: iron from level 3, diamond from 10 and netherite from 20
        private static Item tier(int itemLevel, Item low, Item iron, Item diamond, Item netherite) {
            return itemLevel >= 20 ? netherite : itemLevel >= 10 ? diamond : itemLevel >= 3 ? iron : low;
        }
    }

    public final List<Piece> pieces;

    ItemSet(List<Piece> pieces) {
        this.pieces = pieces;
    }

    // E.g. "Guardian's", which goes in front of the piece's name
    public MutableComponent displayName() {
        return Text.tr("set." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent twoPieceBonus() {
        return Text.tr("set." + name().toLowerCase(Locale.ROOT) + ".two");
    }

    public MutableComponent fourPieceBonus() {
        return Text.tr("set." + name().toLowerCase(Locale.ROOT) + ".four");
    }

    // The set the item belongs to, or null
    public static ItemSet of(ItemStack stack) {
        String name = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(SET_TAG);
        for (ItemSet set : values()) {
            if (set.name().equals(name)) {
                return set;
            }
        }
        return null;
    }

    public static Piece piece(ItemStack stack) {
        String name = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(PIECE_TAG);
        for (Piece piece : Piece.values()) {
            if (piece.name().equals(name)) {
                return piece;
            }
        }
        return null;
    }

    void markOn(ItemStack stack, Piece piece) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(SET_TAG, name());
            tag.putString(PIECE_TAG, piece.name());
        });
    }
}
