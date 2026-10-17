package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
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
    // Special weapon affixes. The value is stored on the item and used in PlayerHits
    public static final String LIFESTEAL_TAG = "morkeriget_lifesteal";
    public static final String FIRE_TAG = "morkeriget_fire";
    public static final String CRUSHING_TAG = "morkeriget_crushing";

    private static final List<Item> LOOT_ITEMS = List.of(
            Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD,
            Items.IRON_AXE, Items.DIAMOND_AXE,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS);

    // A bonus that can roll on an item. min and max are scaled by how good the roll is.
    // Stat affixes change an attribute. Special affixes only roll on weapons, and are stored under tag with lore as the text
    private record Affix(Holder<Attribute> attribute, AttributeModifier.Operation operation, double min, double max, String tag, String lore) {
        static Affix stat(Holder<Attribute> attribute, AttributeModifier.Operation operation, double min, double max) {
            return new Affix(attribute, operation, min, max, null, null);
        }

        static Affix special(String tag, int min, int max, String lore) {
            return new Affix(null, null, min, max, tag, lore);
        }
    }

    private static final List<Affix> AFFIXES = List.of(
            Affix.stat(Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 1, 4),
            Affix.stat(Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05, 0.2),
            Affix.stat(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 2, 6),
            Affix.stat(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.03, 0.12),
            Affix.stat(Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1, 4),
            Affix.stat(Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, 1, 3),
            Affix.stat(Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, 1, 3),
            Affix.stat(Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE, 0.5, 1.5),
            Affix.special(LIFESTEAL_TAG, 3, 10, "+%d%% livsstjæl"),
            Affix.special(FIRE_TAG, 2, 5, "Sætter ild i fjender i %d sek."),
            Affix.special(CRUSHING_TAG, 5, 15, "%d%% chance for knusende slag (dobbelt skade)"));

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
        List<Rarity> drops = new ArrayList<>();
        if (Bosses.isBoss(event.getEntity())) {
            drops.add(Rarity.LEGENDARY);
            drops.add(Rarity.roll(random, Rarity.RARE));
            drops.add(Rarity.roll(random, Rarity.RARE));
        } else if (Elites.isElite(event.getEntity())) {
            drops.add(Rarity.roll(random, Rarity.RARE));
        } else if (random.nextFloat() < Config.DROP_CHANCE.get()) {
            drops.add(Rarity.roll(random, Rarity.COMMON));
        }
        for (Rarity rarity : drops) {
            event.getDrops().add(new ItemEntity(event.getEntity().level(),
                    event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), create(random, rarity)));
        }
    }

    public static ItemStack create(RandomSource random, Rarity rarity) {
        ItemStack stack = new ItemStack(LOOT_ITEMS.get(random.nextInt(LOOT_ITEMS.size())));
        EquipmentSlotGroup slot = stack.getItem() instanceof ArmorItem armor
                ? EquipmentSlotGroup.bySlot(armor.getEquipmentSlot())
                : EquipmentSlotGroup.MAINHAND;

        // Pick different affixes and roll how strong each one is
        boolean weapon = slot == EquipmentSlotGroup.MAINHAND;
        List<Affix> available = new ArrayList<>(AFFIXES.stream().filter(affix -> weapon || affix.tag == null).toList());
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<Component> specialLore = new ArrayList<>();
        double totalQuality = 0;
        for (int i = 0; i < rarity.affixCount && !available.isEmpty(); i++) {
            Affix affix = available.remove(random.nextInt(available.size()));
            double quality = random.nextDouble();
            totalQuality += quality;
            double amount = affix.min + (affix.max - affix.min) * quality;
            if (affix.tag != null) {
                int value = (int) Math.round(amount);
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(affix.tag, value));
                specialLore.add(Component.literal(String.format(affix.lore, value)).withStyle(ChatFormatting.BLUE));
            } else {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "affix_" + i);
                modifiers = modifiers.withModifierAdded(affix.attribute, new AttributeModifier(id, amount, affix.operation), slot);
            }
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        // Item power works like in Diablo: a quick number that tells you how good the item is
        int itemPower = 100 + rarity.ordinal() * 150 + (int) (totalQuality * 50) + random.nextInt(25);

        Component baseName = stack.getItem().getDescription();
        Component name = rarity == Rarity.LEGENDARY
                ? Component.literal(LEGENDARY_NAMES.get(random.nextInt(LEGENDARY_NAMES.size())) + " ").append(baseName)
                : baseName.copy();
        stack.set(DataComponents.ITEM_NAME, name.copy().withStyle(rarity.color));
        List<Component> lore = new ArrayList<>(List.of(
                Component.literal(rarity.displayName + " genstand").withStyle(rarity.color),
                Component.literal("Genstandsstyrke " + itemPower).withStyle(ChatFormatting.GRAY)));
        lore.addAll(specialLore);
        stack.set(DataComponents.LORE, new ItemLore(lore));
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
