package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Elites;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// Monsters you kill can drop weapons and armour with a rarity, an item power and random bonuses (affixes)
public final class Loot {
    public static final String RARITY_TAG = "morkeriget_rarity";

    // Chance that a normal monster drops a piece of loot
    private static final float DROP_CHANCE = 0.08f;

    private static final List<Item> LOOT_ITEMS = List.of(
            Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD,
            Items.IRON_AXE, Items.DIAMOND_AXE,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS);

    // A bonus that can roll on an item. min and max are scaled by how good the roll is
    private record Affix(String name, Holder<Attribute> attribute, AttributeModifier.Operation operation, double min, double max) {
    }

    private static final List<Affix> AFFIXES = List.of(
            new Affix("Skade", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 1, 4),
            new Affix("Angrebshastighed", Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05, 0.2),
            new Affix("Liv", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 2, 6),
            new Affix("Bevægelseshastighed", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.03, 0.12),
            new Affix("Rustning", Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1, 4),
            new Affix("Sejhed", Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, 1, 3),
            new Affix("Held", Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, 1, 3),
            new Affix("Tilbageslag", Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE, 0.5, 1.5));

    private static final List<String> LEGENDARY_NAMES = List.of(
            "Dommedagens", "Lilith's", "Blodets", "Skyggernes", "Helvedes", "Ravnens", "Mørkets", "Sjælerøverens");

    private Loot() {
    }

    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(Loot::onLivingDrops);
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        RandomSource random = event.getEntity().getRandom();
        boolean elite = Elites.isElite(event.getEntity());
        if (!elite && random.nextFloat() >= DROP_CHANCE) {
            return;
        }
        Rarity rarity = Rarity.roll(random, elite ? Rarity.RARE : Rarity.COMMON);
        ItemStack stack = create(random, rarity);
        event.getDrops().add(new ItemEntity(event.getEntity().level(),
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack));
    }

    public static ItemStack create(RandomSource random, Rarity rarity) {
        ItemStack stack = new ItemStack(LOOT_ITEMS.get(random.nextInt(LOOT_ITEMS.size())));
        EquipmentSlotGroup slot = stack.getItem() instanceof ArmorItem armor
                ? EquipmentSlotGroup.bySlot(armor.getEquipmentSlot())
                : EquipmentSlotGroup.MAINHAND;

        // Pick different affixes and roll how strong each one is
        List<Affix> available = new ArrayList<>(AFFIXES);
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double totalQuality = 0;
        for (int i = 0; i < rarity.affixCount && !available.isEmpty(); i++) {
            Affix affix = available.remove(random.nextInt(available.size()));
            double quality = random.nextDouble();
            totalQuality += quality;
            double amount = affix.min + (affix.max - affix.min) * quality;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "affix_" + i);
            modifiers = modifiers.withModifierAdded(affix.attribute, new AttributeModifier(id, amount, affix.operation), slot);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        // Item power works like in Diablo: a quick number that tells you how good the item is
        int itemPower = 100 + rarity.ordinal() * 150 + (int) (totalQuality * 50) + random.nextInt(25);

        Component baseName = stack.getItem().getDescription();
        Component name = rarity == Rarity.LEGENDARY
                ? Component.literal(LEGENDARY_NAMES.get(random.nextInt(LEGENDARY_NAMES.size())) + " ").append(baseName)
                : baseName.copy();
        stack.set(DataComponents.ITEM_NAME, name.copy().withStyle(rarity.color));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(rarity.displayName + " genstand").withStyle(rarity.color),
                Component.literal("Genstandsstyrke " + itemPower).withStyle(ChatFormatting.GRAY))));
        if (rarity == Rarity.LEGENDARY) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(RARITY_TAG, rarity.name()));
        return stack;
    }

    public static Rarity getRarity(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return Rarity.byName(data.copyTag().getString(RARITY_TAG));
    }
}
