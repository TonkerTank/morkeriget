package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.smithy.Durability;
import com.morkeriget.smithy.Gems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// When you hover over loot, the tooltip ends with how it compares to what you are wearing in the same place,
// with better numbers in green and worse in red. Rings and amulets are compared with the worst one you wear.
// Client only
public final class LootTooltips {
    private static final int GREATER_COLOR = 0xFF9F1C;

    private record Stat(Holder<Attribute> attribute, AttributeModifier.Operation operation) {
    }

    private record Special(String tag, String text) {
    }

    private static final List<Special> SPECIALS = List.of(
            new Special(Loot.LIFESTEAL_TAG, "%s%% livsstjæl"),
            new Special(Loot.FIRE_TAG, "%s sek. ild"),
            new Special(Loot.CRUSHING_TAG, "%s%% knusende slag"),
            new Special(Loot.ARROW_DAMAGE_TAG, "%s%% pileskade"),
            new Special(Loot.THORNS_TAG, "%s%% torne"),
            new Special(Gems.MANA_REGEN_TAG, "%s%% hurtigere mana"),
            new Special(Loot.STAFF_MANA_TAG, "%s%% hurtigere mana (stav)"));

    private LootTooltips() {
    }

    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player == null || Loot.getRarity(stack) == null) {
            return;
        }
        // Under the name, so it is seen before anything else
        int top = 1;
        if (Favorites.is(stack)) {
            event.getToolTip().add(Math.min(top++, event.getToolTip().size()),
                    Component.literal("★ Favorit - kan ikke smeltes (Alt+klik fjerner)").withStyle(ChatFormatting.GOLD));
        }
        if (Durability.isBroken(stack)) {
            event.getToolTip().add(Math.min(top++, event.getToolTip().size()),
                    Component.literal("⚠ Slidt op - giver ingen bonusser, før den er repareret i smedjen").withStyle(ChatFormatting.RED));
        }
        // The greater bonuses as they are now, so a masterwork shows. Vanilla already lists them among the others, but
        // without telling which ones are greater
        List<Component> greater = new ArrayList<>();
        for (String key : Loot.greater(stack)) {
            greater.add(Component.literal("✦ ").withColor(GREATER_COLOR).append(Masterwork.line(stack, key).copy().withColor(GREATER_COLOR)));
        }
        if (!greater.isEmpty()) {
            int at = Math.min(top, event.getToolTip().size());
            event.getToolTip().add(at, Component.literal("Større bonus" + (greater.size() > 1 ? "ser" : "")).withColor(GREATER_COLOR));
            event.getToolTip().addAll(at + 1, greater);
        }
        ItemStack worn;
        if (ItemKind.of(stack) == ItemKind.JEWELRY) {
            worn = weakestWorn(stack);
            // The ring or amulet is worn itself, e.g. in the equipment screen
            if (worn == null) {
                return;
            }
        } else {
            worn = player.getItemBySlot(slotFor(stack));
        }
        // Don't compare the item you are wearing with itself
        if (worn == stack) {
            return;
        }

        List<Component> lines = event.getToolTip();
        lines.add(Component.empty());
        if (worn.isEmpty()) {
            lines.add(Component.literal(ItemKind.of(stack) == ItemKind.JEWELRY ? "Du har en ledig plads til den (Udstyr)" : "Du har intet på her")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(Component.literal("Sammenlignet med ").withStyle(ChatFormatting.GRAY).append(worn.getHoverName()));

        List<Component> differences = new ArrayList<>();
        difference(differences, Loot.itemPower(stack) - Loot.itemPower(worn), Component.literal(" genstandsstyrke"), false);
        Map<Stat, double[]> stats = new LinkedHashMap<>();
        addStats(stats, worn, 0);
        addStats(stats, stack, 1);
        for (Map.Entry<Stat, double[]> entry : stats.entrySet()) {
            Stat stat = entry.getKey();
            boolean percent = stat.operation != AttributeModifier.Operation.ADD_VALUE;
            double change = (entry.getValue()[1] - entry.getValue()[0]) * (percent ? 100 : 1);
            difference(differences, change, Component.literal(percent ? "% " : " ")
                    .append(Component.translatable(stat.attribute.value().getDescriptionId())), false);
        }
        for (Special special : SPECIALS) {
            int change = Loot.affix(stack, special.tag) - Loot.affix(worn, special.tag);
            difference(differences, change, Component.literal(special.text), true);
        }
        if (differences.isEmpty()) {
            lines.add(Component.literal("Ingen forskel").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.addAll(differences);
    }

    // Where the item has to be worn. Weapons are compared with what is in your main hand. Not for jewelry, see weakestWorn
    private static EquipmentSlot slotFor(ItemStack stack) {
        return switch (ItemKind.of(stack)) {
            case ARMOR -> ((ArmorItem) stack.getItem()).getEquipmentSlot();
            case SHIELD -> EquipmentSlot.OFFHAND;
            case MELEE, RANGED, JEWELRY -> EquipmentSlot.MAINHAND;
        };
    }

    // The jewelry the player wears, from the server, see Jewelry.applyStats
    private static List<ItemStack> wornJewelry = List.of();

    public static void setWornJewelry(List<ItemStack> items) {
        wornJewelry = items;
    }

    // A new ring is compared with the worst ring you wear, the one it would replace, and an amulet with the worst amulet.
    // Empty if a slot is free, and null if the item is one of those you wear. The rings come first in the list, then the
    // amulets, so a free amulet slot doesn't count as room for a ring
    static ItemStack weakestWorn(ItemStack stack) {
        boolean ring = stack.is(Jewelry.RING.get());
        int from = ring ? 0 : Jewelry.RING_SLOTS;
        int to = Math.min(ring ? Jewelry.RING_SLOTS : Jewelry.SLOTS, wornJewelry.size());
        ItemStack weakest = null;
        for (ItemStack worn : wornJewelry.subList(Math.min(from, to), to)) {
            if (ItemStack.matches(worn, stack)) {
                return null;
            }
            if (worn.isEmpty()) {
                weakest = ItemStack.EMPTY;
            } else if (weakest == null || !weakest.isEmpty() && Loot.itemPower(worn) < Loot.itemPower(weakest)) {
                weakest = worn;
            }
        }
        return weakest != null ? weakest : ItemStack.EMPTY;
    }

    // Adds up the item's attribute bonuses, including the base ones like a sword's damage
    private static void addStats(Map<Stat, double[]> stats, ItemStack stack, int index) {
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            stats.computeIfAbsent(new Stat(entry.attribute(), entry.modifier().operation()), stat -> new double[2])[index] += entry.modifier().amount();
        }
    }

    // A line like "+3 Attack Damage" in green or "-12% Speed" in red. In the special texts the number goes into %s
    private static void difference(List<Component> lines, double change, Component text, boolean numberInText) {
        if (Math.abs(change) < 0.005) {
            return;
        }
        String number = (change > 0 ? "+" : "") + ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(change);
        MutableComponent line = numberInText
                ? Component.literal(String.format(text.getString(), number))
                : Component.literal(number).append(text);
        lines.add(line.withStyle(change > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
