package com.morkeriget.smithy;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

// Gem items and the sockets on loot. The number of sockets and the gems in them are saved on the item, like "RUBY:3,EMERALD:1".
// Every time they change, refresh() writes the gem bonuses and the socket lines in the tooltip again
public final class Gems {
    public static final String MANA_REGEN_TAG = "morkeriget_mana_regen";
    private static final String TIER_TAG = "morkeriget_gem_tier";
    private static final String SOCKETS_TAG = "morkeriget_sockets";
    private static final String GEMS_TAG = "morkeriget_gems";
    private static final String GEM_MODIFIER_PREFIX = "gem_";
    // The socket lines in the tooltip start with these, so they can be found and replaced
    private static final String FILLED = "◆ ";
    private static final String EMPTY = "◇ ";

    // A gem sitting in a socket
    public record Socketed(Gem gem, int tier) {
    }

    private Gems() {
    }

    // A gem item of this tier. Tier 1 has no tier saved, so it stacks with gems from before tiers existed
    public static ItemStack stack(Gem gem, int tier) {
        ItemStack stack = new ItemStack(Smithy.GEMS.get(gem).get());
        if (Gem.clamp(tier) > 1) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TIER_TAG, Gem.clamp(tier)));
        }
        return stack;
    }

    public static int tier(ItemStack gem) {
        return Gem.clamp(tag(gem).getInt(TIER_TAG));
    }

    // A random gem for a monster of this level: one of the two best tiers it can drop. A new tier every 20 levels,
    // plus bonus tiers, e.g. from high rifts
    public static ItemStack random(RandomSource random, int level, int bonusTiers) {
        Gem gem = Gem.values()[random.nextInt(Gem.values().length)];
        int best = Gem.clamp(1 + level / 20 + bonusTiers);
        int tier = Math.max(1, best - random.nextInt(2));
        return stack(gem, tier);
    }

    // How many sockets a new item of this rarity gets
    public static int rollSockets(RandomSource random, Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case MAGIC -> random.nextDouble() < 0.2 ? 1 : 0;
            case RARE -> random.nextDouble() < 0.35 ? 1 + random.nextInt(2) : 0;
            case LEGENDARY, SET -> random.nextDouble() < 0.5 ? 1 + random.nextInt(2) : 0;
            case MYTHIC -> 2;
        };
    }

    public static int sockets(ItemStack stack) {
        return tag(stack).getInt(SOCKETS_TAG);
    }

    // Gems saved before tiers existed have no ":tier" and count as tier 1
    public static List<Socketed> gems(ItemStack stack) {
        List<Socketed> gems = new ArrayList<>();
        for (String entry : tag(stack).getString(GEMS_TAG).split(",")) {
            String[] parts = entry.split(":");
            Gem gem = Gem.byName(parts[0]);
            if (gem != null) {
                int tier = parts.length > 1 ? parseTier(parts[1]) : 1;
                gems.add(new Socketed(gem, tier));
            }
        }
        return gems;
    }

    private static int parseTier(String text) {
        try {
            return Gem.clamp(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public static boolean hasGems(ItemStack stack) {
        return !gems(stack).isEmpty();
    }

    public static boolean hasFreeSocket(ItemStack stack) {
        return gems(stack).size() < sockets(stack);
    }

    public static void setSockets(ItemStack stack, int sockets) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SOCKETS_TAG, sockets));
        refresh(stack);
    }

    // Returns false if there is no free socket
    public static boolean insert(ItemStack stack, Gem gem, int tier) {
        if (!hasFreeSocket(stack)) {
            return false;
        }
        List<Socketed> gems = gems(stack);
        gems.add(new Socketed(gem, tier));
        setGems(stack, gems);
        return true;
    }

    // Takes all gems out and returns them as items
    public static List<ItemStack> removeAll(ItemStack stack) {
        List<ItemStack> items = new ArrayList<>();
        for (Socketed socketed : gems(stack)) {
            items.add(stack(socketed.gem, socketed.tier));
        }
        setGems(stack, List.of());
        return items;
    }

    // A rerolled item keeps its sockets and gems
    public static void copy(ItemStack from, ItemStack to) {
        CustomData.update(DataComponents.CUSTOM_DATA, to, tag -> tag.putInt(SOCKETS_TAG, sockets(from)));
        setGems(to, gems(from));
    }

    private static void setGems(ItemStack stack, List<Socketed> gems) {
        String saved = String.join(",", gems.stream().map(socketed -> socketed.gem.name() + ":" + socketed.tier).toList());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(GEMS_TAG, saved));
        refresh(stack);
    }

    private static void refresh(ItemStack stack) {
        List<Socketed> gems = gems(stack);
        int sockets = sockets(stack);

        // Attribute bonuses: take the old gem ones off and put the current ones on
        ItemAttributeModifiers old = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<ItemAttributeModifiers.Entry> kept = old.modifiers().stream()
                .filter(entry -> !entry.modifier().id().getPath().startsWith(GEM_MODIFIER_PREFIX))
                .toList();
        ItemAttributeModifiers modifiers = new ItemAttributeModifiers(kept, old.showInTooltip());
        int manaRegen = 0;
        for (int i = 0; i < gems.size(); i++) {
            Socketed socketed = gems.get(i);
            Gem gem = socketed.gem;
            if (gem.attribute == null) {
                manaRegen += (int) gem.amount(socketed.tier);
                continue;
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, GEM_MODIFIER_PREFIX + i);
            modifiers = modifiers.withModifierAdded(gem.attribute,
                    new AttributeModifier(id, gem.amount(socketed.tier), gem.operation()), Loot.slotGroup(stack));
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        int mana = manaRegen;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (mana > 0) {
                tag.putInt(MANA_REGEN_TAG, mana);
            } else {
                tag.remove(MANA_REGEN_TAG);
            }
        });

        // Tooltip: one line per socket at the end
        List<Component> lore = new ArrayList<>();
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            String text = line.getString();
            if (!text.startsWith(FILLED) && !text.startsWith(EMPTY)) {
                lore.add(line);
            }
        }
        for (int i = 0; i < sockets; i++) {
            if (i < gems.size()) {
                Socketed socketed = gems.get(i);
                lore.add(Component.literal(FILLED + socketed.gem.name(socketed.tier) + ": " + socketed.gem.bonus(socketed.tier))
                        .withColor(socketed.gem.color));
            } else {
                lore.add(Component.literal(EMPTY + "Tom sokkel").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
