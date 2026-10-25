package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.morkeriget.Morkeriget;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

// Masterworking, like in Diablo 4: essence in the smithy makes an item's bonuses stronger, one rank at a time up to 12.
// Each rank adds 5% to every bonus, and ranks 4, 8 and 12 add 25% more to one bonus picked at random. Percent and special
// bonuses get half of that. The bonuses as they were rolled are saved on the item the first time, so the numbers are
// always worked out from those and never drift from rounding. A reroll keeps the rank, and the new bonuses get it too
public final class Masterwork {
    public static final int MAX_RANK = 12;
    private static final double PER_RANK = 0.05;
    private static final int BIG_STEP_EVERY = 4;
    private static final double BIG_STEP = 0.25;
    // Each rank also adds to the item power, so the comparison in the tooltip sees that the item got better
    private static final int POWER_PER_RANK = 5;
    private static final String RANK_TAG = "morkeriget_masterwork";
    private static final String BASE_TAG = "morkeriget_masterwork_base";
    // The bonus each big step went to, so one bonus can get more than one
    private static final String BIG_TAG = "morkeriget_masterwork_big";
    private static final String AFFIX_PREFIX = "affix_";
    private static final String RANK_LORE = "Mesterværk ";
    private static final List<String> SPECIAL_TAGS = List.of(
            Loot.LIFESTEAL_TAG, Loot.FIRE_TAG, Loot.CRUSHING_TAG, Loot.ARROW_DAMAGE_TAG, Loot.THORNS_TAG);

    private Masterwork() {
    }

    public static int rank(ItemStack stack) {
        return Loot.affix(stack, RANK_TAG);
    }

    // Common items have no bonuses, so there is nothing to make stronger
    public static boolean canUpgrade(ItemStack stack) {
        return Loot.getRarity(stack) != null && rank(stack) < MAX_RANK && !bonuses(stack).isEmpty();
    }

    // The next rank costs more than the last, and better items cost more
    public static int cost(ItemStack stack) {
        int perRank = switch (Loot.getRarity(stack)) {
            case COMMON, MAGIC -> 1;
            case RARE -> 2;
            case LEGENDARY, SET -> 3;
            case MYTHIC -> 5;
        };
        return (rank(stack) + 1) * perRank;
    }

    // Goes up one rank. Returns the bonus a big step went to, as it reads now, or null if this rank had none
    public static Component upgrade(ItemStack stack, RandomSource random) {
        CompoundTag data = tag(stack);
        if (!data.contains(BASE_TAG)) {
            data.put(BASE_TAG, toTag(bonuses(stack)));
        }
        int rank = data.getInt(RANK_TAG) + 1;
        data.putInt(RANK_TAG, rank);
        String bigKey = null;
        if (rank % BIG_STEP_EVERY == 0) {
            List<String> keys = new ArrayList<>(data.getCompound(BASE_TAG).getAllKeys());
            bigKey = keys.get(random.nextInt(keys.size()));
            ListTag big = data.getList(BIG_TAG, Tag.TAG_STRING);
            big.add(StringTag.valueOf(bigKey));
            data.put(BIG_TAG, big);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
        apply(stack);
        Loot.addItemPower(stack, POWER_PER_RANK);
        return bigKey != null ? line(stack, bigKey) : null;
    }

    // A rerolled item is a new stack with new bonuses. It keeps the rank, and a big step on a bonus it no longer has
    // goes to another one
    static void carryOver(ItemStack old, ItemStack rerolled, RandomSource random) {
        int rank = rank(old);
        Map<String, Double> bonuses = bonuses(rerolled);
        if (rank == 0 || bonuses.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(bonuses.keySet());
        ListTag big = new ListTag();
        for (Tag key : tag(old).getList(BIG_TAG, Tag.TAG_STRING)) {
            String name = key.getAsString();
            big.add(StringTag.valueOf(bonuses.containsKey(name) ? name : keys.get(random.nextInt(keys.size()))));
        }
        CustomData.update(DataComponents.CUSTOM_DATA, rerolled, tag -> {
            tag.putInt(RANK_TAG, rank);
            tag.put(BASE_TAG, toTag(bonuses));
            tag.put(BIG_TAG, big);
        });
        apply(rerolled);
        Loot.addItemPower(rerolled, rank * POWER_PER_RANK);
    }

    // The item's bonuses by key: "affix_0" and so on for attribute bonuses, the tag for special ones
    private static Map<String, Double> bonuses(ItemStack stack) {
        Map<String, Double> bonuses = new LinkedHashMap<>();
        for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            ResourceLocation id = entry.modifier().id();
            if (id.getNamespace().equals(Morkeriget.MODID) && id.getPath().startsWith(AFFIX_PREFIX)) {
                bonuses.put(id.getPath(), entry.modifier().amount());
            }
        }
        for (String special : SPECIAL_TAGS) {
            int value = Loot.affix(stack, special);
            if (value > 0) {
                bonuses.put(special, (double) value);
            }
        }
        return bonuses;
    }

    private static CompoundTag toTag(Map<String, Double> bonuses) {
        CompoundTag tag = new CompoundTag();
        bonuses.forEach(tag::putDouble);
        return tag;
    }

    // Writes every bonus again from its rolled value and the rank, and the lines in the lore that show them
    private static void apply(ItemStack stack) {
        CompoundTag data = tag(stack);
        CompoundTag base = data.getCompound(BASE_TAG);
        int rank = data.getInt(RANK_TAG);
        ListTag big = data.getList(BIG_TAG, Tag.TAG_STRING);
        List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());

        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            AttributeModifier modifier = entry.modifier();
            String key = modifier.id().getPath();
            if (!modifier.id().getNamespace().equals(Morkeriget.MODID) || !base.contains(key)) {
                entries.add(entry);
                continue;
            }
            double amount = base.getDouble(key) * factor(key, rank, big, Loot.growsSlowly(entry.attribute()));
            entries.add(new ItemAttributeModifiers.Entry(entry.attribute(), new AttributeModifier(modifier.id(), amount, modifier.operation()), entry.slot()));
            // Only jewelry has its attribute bonuses in the lore
            replaceLine(lore, Loot.statLine(entry.attribute(), modifier.operation(), modifier.amount()),
                    Loot.statLine(entry.attribute(), modifier.operation(), amount));
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries, modifiers.showInTooltip()));

        for (String special : SPECIAL_TAGS) {
            if (base.contains(special)) {
                int old = Loot.affix(stack, special);
                int value = (int) Math.round(base.getDouble(special) * factor(special, rank, big, true));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(special, value));
                String text = Loot.specialLore(special);
                replaceLine(lore, Component.literal(String.format(text, old)), Component.literal(String.format(text, value)).withStyle(ChatFormatting.BLUE));
            }
        }

        // "Mesterværk 5/12" right under the item power
        Component rankLine = Component.literal(RANK_LORE + rank + "/" + MAX_RANK).withStyle(ChatFormatting.GOLD);
        int existing = indexOf(lore, RANK_LORE);
        if (existing >= 0) {
            lore.set(existing, rankLine);
        } else {
            int power = indexOf(lore, Loot.ITEM_POWER_LORE);
            lore.add(power >= 0 ? power + 1 : Math.min(1, lore.size()), rankLine);
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    // Percent and special bonuses grow half as fast, since they are capped in a fight and add up quickly over many items
    private static double factor(String key, int rank, ListTag big, boolean slow) {
        int bigSteps = 0;
        for (Tag tag : big) {
            if (tag.getAsString().equals(key)) {
                bigSteps++;
            }
        }
        return 1 + (PER_RANK * rank + BIG_STEP * bigSteps) * (slow ? 0.5 : 1);
    }

    // The bonus as the tooltip shows it, for the message about a big step and the greater bonuses in LootTooltips
    static Component line(ItemStack stack, String key) {
        if (SPECIAL_TAGS.contains(key)) {
            return Component.literal(String.format(Loot.specialLore(key), Loot.affix(stack, key)));
        }
        for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (entry.modifier().id().getPath().equals(key)) {
                return Loot.statLine(entry.attribute(), entry.modifier().operation(), entry.modifier().amount());
            }
        }
        return Component.empty();
    }

    // The lore line that reads like old gets new in its place
    private static void replaceLine(List<Component> lore, Component old, Component replacement) {
        String text = old.getString();
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).getString().equals(text)) {
                lore.set(i, replacement);
                return;
            }
        }
    }

    private static int indexOf(List<Component> lore, String prefix) {
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).getString().startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
