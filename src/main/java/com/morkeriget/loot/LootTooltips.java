package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
// with better numbers in green and worse in red. Jewelry isn't compared, since the client doesn't know what you wear there.
// Client only
public final class LootTooltips {
    private record Stat(Holder<Attribute> attribute, AttributeModifier.Operation operation) {
    }

    private record Special(String tag, String text) {
    }

    private static final List<Special> SPECIALS = List.of(
            new Special(Loot.LIFESTEAL_TAG, "%s%% livsstjæl"),
            new Special(Loot.FIRE_TAG, "%s sek. ild"),
            new Special(Loot.CRUSHING_TAG, "%s%% knusende slag"),
            new Special(Loot.ARROW_DAMAGE_TAG, "%s%% pileskade"),
            new Special(Loot.THORNS_TAG, "%s%% torne"));

    private LootTooltips() {
    }

    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player == null || Loot.getRarity(stack) == null) {
            return;
        }
        EquipmentSlot slot = slotFor(stack);
        if (slot == null) {
            return;
        }
        ItemStack worn = player.getItemBySlot(slot);
        // Don't compare the item you are wearing with itself
        if (worn == stack) {
            return;
        }

        List<Component> lines = event.getToolTip();
        lines.add(Component.empty());
        if (worn.isEmpty()) {
            lines.add(Component.literal("Du har intet på her").withStyle(ChatFormatting.DARK_GRAY));
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

    // Where the item has to be worn. Weapons are compared with what is in your main hand
    private static EquipmentSlot slotFor(ItemStack stack) {
        return switch (ItemKind.of(stack)) {
            case ARMOR -> ((ArmorItem) stack.getItem()).getEquipmentSlot();
            case SHIELD -> EquipmentSlot.OFFHAND;
            case MELEE, RANGED -> EquipmentSlot.MAINHAND;
            case JEWELRY -> null;
        };
    }

    // Adds up the item's attribute bonuses, including the base ones like a sword's damage
    private static void addStats(Map<Stat, double[]> stats, ItemStack stack, int index) {
        for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
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
