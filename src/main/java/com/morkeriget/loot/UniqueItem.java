package com.morkeriget.loot;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.morkeriget.Text;
import com.morkeriget.combat.PlayerHits;
import com.morkeriget.jewelry.Jewelry;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

// The unique items: between legendary and mythic. Each one always has the same bonuses (only how strong they roll
// changes) and a power of its own, and it only drops from one place, so you know where to hunt for it.
// What the powers do is in UniqueEffects
public enum UniqueItem {
    // Names, descriptions and where they drop are in the language files, under unique.<name> and unique.source.<source>
    COLOSSUS_PLATE(() -> Items.NETHERITE_CHESTPLATE, Source.WORLD_BOSS,
            List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Loot.THORNS_TAG)),
    DARK_LORDS_CROWN(() -> Items.NETHERITE_HELMET, Source.LORD_OF_DARKNESS,
            List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.MOVEMENT_SPEED)),
    HELLFIRE_BAND(Jewelry.RING::get, Source.HELLTIDE,
            List.of(Attributes.ATTACK_DAMAGE, Loot.LIFESTEAL_TAG, Loot.CRUSHING_TAG, Attributes.MAX_HEALTH)),
    WARDENS_LANTERN(Jewelry.AMULET::get, Source.DUNGEON,
            List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED)),
    RIFTWALKERS(() -> Items.NETHERITE_BOOTS, Source.RIFT,
            List.of(Attributes.MOVEMENT_SPEED, Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS));

    // Where a unique drops. The chances are in Loot.uniqueChance
    public enum Source {
        WORLD_BOSS, LORD_OF_DARKNESS, HELLTIDE, DUNGEON, RIFT;

        public MutableComponent displayName() {
            return Text.tr("unique.source." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final String UNIQUE_TAG = "morkeriget_unique";

    private final Supplier<Item> baseItem;
    public final Source source;
    // The bonuses it always has, in this order: an attribute (Holder) or the tag of a special bonus (String)
    final List<Object> bonuses;

    UniqueItem(Supplier<Item> baseItem, Source source, List<Object> bonuses) {
        this.baseItem = baseItem;
        this.source = source;
        this.bonuses = bonuses;
    }

    public MutableComponent displayName() {
        return Text.tr("unique." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public MutableComponent description() {
        return Text.tr("unique." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    public Item baseItem() {
        return baseItem.get();
    }

    // Whether a bonus is one of the fixed ones, see Loot.roll
    boolean hasBonus(Holder<Attribute> attribute, String tag) {
        return bonuses.contains(tag != null ? tag : attribute);
    }

    // The uniques that drop from a source. There is one of each for now, but there could be more
    public static List<UniqueItem> from(Source source) {
        return java.util.Arrays.stream(values()).filter(unique -> unique.source == source).toList();
    }

    // The unique the item is, or null
    public static UniqueItem of(ItemStack stack) {
        String name = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(UNIQUE_TAG);
        for (UniqueItem unique : values()) {
            if (unique.name().equals(name)) {
                return unique;
            }
        }
        return null;
    }

    // Whether the player wears it or has it in their hand
    public static boolean has(ServerPlayer player, UniqueItem unique) {
        for (ItemStack stack : PlayerHits.equippedItems(player)) {
            if (of(stack) == unique) {
                return true;
            }
        }
        return false;
    }

    public void markOn(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(UNIQUE_TAG, name()));
    }
}
