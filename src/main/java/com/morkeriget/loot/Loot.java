package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.jewelry.Jewelry;

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

// Monsters you kill can drop weapons, armour, shields and jewelry with a rarity, an item power and random bonuses (affixes)
public final class Loot {
    public static final String RARITY_TAG = "morkeriget_rarity";
    // Special affixes. The value is stored on the item and used in PlayerHits
    public static final String LIFESTEAL_TAG = "morkeriget_lifesteal";
    public static final String FIRE_TAG = "morkeriget_fire";
    public static final String CRUSHING_TAG = "morkeriget_crushing";
    public static final String ARROW_DAMAGE_TAG = "morkeriget_arrow_damage";
    public static final String THORNS_TAG = "morkeriget_thorns";

    // An item that can drop, and the item levels it drops at. Better base items only drop from higher level monsters.
    // The item is a supplier because rings and amulets are our own items, which don't exist until they are registered
    private record LootItem(Supplier<Item> item, int minLevel, int maxLevel) {
        static LootItem of(Item item, int minLevel, int maxLevel) {
            return new LootItem(() -> item, minLevel, maxLevel);
        }
    }

    private static final int ANY_LEVEL = Integer.MAX_VALUE;

    private static final List<LootItem> LOOT_ITEMS = List.of(
            LootItem.of(Items.STONE_SWORD, 1, 9),
            LootItem.of(Items.GOLDEN_SWORD, 1, 14),
            LootItem.of(Items.IRON_SWORD, 3, ANY_LEVEL),
            LootItem.of(Items.IRON_AXE, 3, ANY_LEVEL),
            LootItem.of(Items.DIAMOND_SWORD, 10, ANY_LEVEL),
            LootItem.of(Items.DIAMOND_AXE, 10, ANY_LEVEL),
            LootItem.of(Items.NETHERITE_SWORD, 20, ANY_LEVEL),
            LootItem.of(Items.NETHERITE_AXE, 20, ANY_LEVEL),
            LootItem.of(Items.BOW, 1, ANY_LEVEL),
            LootItem.of(Items.CROSSBOW, 5, ANY_LEVEL),
            LootItem.of(Items.SHIELD, 1, ANY_LEVEL),
            LootItem.of(Items.CHAINMAIL_HELMET, 1, 19),
            LootItem.of(Items.CHAINMAIL_CHESTPLATE, 1, 19),
            LootItem.of(Items.CHAINMAIL_LEGGINGS, 1, 19),
            LootItem.of(Items.CHAINMAIL_BOOTS, 1, 19),
            LootItem.of(Items.IRON_HELMET, 3, ANY_LEVEL),
            LootItem.of(Items.IRON_CHESTPLATE, 3, ANY_LEVEL),
            LootItem.of(Items.IRON_LEGGINGS, 3, ANY_LEVEL),
            LootItem.of(Items.IRON_BOOTS, 3, ANY_LEVEL),
            LootItem.of(Items.DIAMOND_HELMET, 10, ANY_LEVEL),
            LootItem.of(Items.DIAMOND_CHESTPLATE, 10, ANY_LEVEL),
            LootItem.of(Items.DIAMOND_LEGGINGS, 10, ANY_LEVEL),
            LootItem.of(Items.DIAMOND_BOOTS, 10, ANY_LEVEL),
            LootItem.of(Items.NETHERITE_HELMET, 20, ANY_LEVEL),
            LootItem.of(Items.NETHERITE_CHESTPLATE, 20, ANY_LEVEL),
            LootItem.of(Items.NETHERITE_LEGGINGS, 20, ANY_LEVEL),
            LootItem.of(Items.NETHERITE_BOOTS, 20, ANY_LEVEL),
            new LootItem(Jewelry.RING::get, 1, ANY_LEVEL),
            new LootItem(Jewelry.AMULET::get, 1, ANY_LEVEL));

    private static final Set<ItemKind> ALL = EnumSet.allOf(ItemKind.class);
    private static final Set<ItemKind> MELEE = EnumSet.of(ItemKind.MELEE, ItemKind.JEWELRY);
    private static final Set<ItemKind> HITTING = EnumSet.of(ItemKind.MELEE, ItemKind.RANGED, ItemKind.JEWELRY);
    private static final Set<ItemKind> DEFENSIVE = EnumSet.of(ItemKind.ARMOR, ItemKind.SHIELD, ItemKind.JEWELRY);

    // A bonus that can roll on the given kinds of item. min and max are scaled by how good the roll is.
    // Stat affixes change an attribute. Special affixes are stored under tag, with lore as the text
    private record Affix(Set<ItemKind> kinds, Holder<Attribute> attribute, AttributeModifier.Operation operation,
                         double min, double max, String tag, String lore) {
        static Affix stat(Set<ItemKind> kinds, Holder<Attribute> attribute, AttributeModifier.Operation operation, double min, double max) {
            return new Affix(kinds, attribute, operation, min, max, null, null);
        }

        static Affix special(Set<ItemKind> kinds, String tag, int min, int max, String lore) {
            return new Affix(kinds, null, null, min, max, tag, lore);
        }
    }

    private static final List<Affix> AFFIXES = List.of(
            Affix.stat(MELEE, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 1, 4),
            Affix.stat(MELEE, Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05, 0.2),
            Affix.stat(ALL, Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 2, 6),
            Affix.stat(ALL, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.03, 0.12),
            Affix.stat(DEFENSIVE, Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1, 4),
            Affix.stat(EnumSet.of(ItemKind.ARMOR, ItemKind.SHIELD), Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, 1, 3),
            Affix.stat(ALL, Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, 1, 3),
            Affix.stat(EnumSet.of(ItemKind.MELEE), Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE, 0.5, 1.5),
            Affix.special(HITTING, LIFESTEAL_TAG, 3, 10, "+%d%% livsstjæl"),
            Affix.special(EnumSet.of(ItemKind.MELEE, ItemKind.RANGED), FIRE_TAG, 2, 5, "Sætter ild i fjender i %d sek."),
            Affix.special(HITTING, CRUSHING_TAG, 5, 15, "%d%% chance for knusende slag (dobbelt skade)"),
            Affix.special(EnumSet.of(ItemKind.RANGED, ItemKind.JEWELRY), ARROW_DAMAGE_TAG, 10, 30, "+%d%% pileskade"),
            Affix.special(DEFENSIVE, THORNS_TAG, 10, 30, "Torne: sender %d%% af skaden tilbage"));

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
        // Loot gets the level of the monster. Monsters from before monster levels existed count as level 1
        int itemLevel = Math.max(1, MonsterLevels.get(event.getEntity()));
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
                    event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), create(random, rarity, itemLevel)));
        }
    }

    private static Item randomItem(RandomSource random, int itemLevel) {
        List<LootItem> possible = LOOT_ITEMS.stream()
                .filter(loot -> itemLevel >= loot.minLevel && itemLevel <= loot.maxLevel)
                .toList();
        return possible.get(random.nextInt(possible.size())).item.get();
    }

    public static ItemStack create(RandomSource random, Rarity rarity, int itemLevel) {
        ItemStack stack = new ItemStack(randomItem(random, itemLevel));
        // Affixes get stronger with the item level. Percent affixes like lifesteal only grow half as fast,
        // so they don't get silly at high levels
        double statScale = 1 + Config.AFFIX_PER_LEVEL.get() * (itemLevel - 1);
        double specialScale = 1 + Config.AFFIX_PER_LEVEL.get() * (itemLevel - 1) / 2;
        ItemKind kind = ItemKind.of(stack);
        // Jewelry bonuses are put on the player by Jewelry, so the slot here is only a place to store them.
        // Players never wear anything in the body slot, so vanilla never uses them by itself
        EquipmentSlotGroup slot = switch (kind) {
            case ARMOR -> EquipmentSlotGroup.bySlot(((ArmorItem) stack.getItem()).getEquipmentSlot());
            case SHIELD -> EquipmentSlotGroup.OFFHAND;
            case JEWELRY -> EquipmentSlotGroup.BODY;
            default -> EquipmentSlotGroup.MAINHAND;
        };

        // Pick different affixes that fit this kind of item and roll how strong each one is
        List<Affix> available = new ArrayList<>(AFFIXES.stream().filter(affix -> affix.kinds.contains(kind)).toList());
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<Component> affixLore = new ArrayList<>();
        double totalQuality = 0;
        for (int i = 0; i < rarity.affixCount && !available.isEmpty(); i++) {
            Affix affix = available.remove(random.nextInt(available.size()));
            double quality = random.nextDouble();
            totalQuality += quality;
            double amount = (affix.min + (affix.max - affix.min) * quality) * (affix.tag != null ? specialScale : statScale);
            if (affix.tag != null) {
                int value = (int) Math.round(amount);
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(affix.tag, value));
                affixLore.add(Component.literal(String.format(affix.lore, value)).withStyle(ChatFormatting.BLUE));
            } else {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "affix_" + i);
                modifiers = modifiers.withModifierAdded(affix.attribute, new AttributeModifier(id, amount, affix.operation), slot);
                if (kind == ItemKind.JEWELRY) {
                    affixLore.add(statLine(affix.attribute, affix.operation, amount));
                }
            }
        }
        // Vanilla would write "When on body" over jewelry bonuses, so they are shown as lore instead
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, kind == ItemKind.JEWELRY ? modifiers.withTooltip(false) : modifiers);

        // Item power works like in Diablo: a quick number that tells you how good the item is
        int itemPower = 100 + rarity.ordinal() * 150 + itemLevel * 10 + (int) (totalQuality * 50) + random.nextInt(25);

        Component baseName = stack.getItem().getDescription();
        Component name = rarity == Rarity.LEGENDARY
                ? Component.literal(LEGENDARY_NAMES.get(random.nextInt(LEGENDARY_NAMES.size())) + " ").append(baseName)
                : baseName.copy();
        stack.set(DataComponents.ITEM_NAME, name.copy().withStyle(rarity.color));
        List<Component> lore = new ArrayList<>(List.of(
                Component.literal(rarity.displayName + " genstand").withStyle(rarity.color),
                Component.literal("Genstandslevel " + itemLevel).withStyle(ChatFormatting.GRAY),
                Component.literal("Genstandsstyrke " + itemPower).withStyle(ChatFormatting.GRAY)));
        lore.addAll(affixLore);
        if (kind == ItemKind.JEWELRY) {
            lore.add(Component.literal("Tag på i Smykker (K)").withStyle(ChatFormatting.DARK_GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        if (rarity == Rarity.LEGENDARY) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(RARITY_TAG, rarity.name()));
        return stack;
    }

    // The same text vanilla uses for attribute bonuses, e.g. "+3 Attack Damage" or "+12% Speed"
    private static Component statLine(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
        double shown = operation == AttributeModifier.Operation.ADD_VALUE ? amount : amount * 100;
        return Component.translatable("attribute.modifier.plus." + operation.id(),
                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(shown),
                Component.translatable(attribute.value().getDescriptionId())).withStyle(ChatFormatting.BLUE);
    }

    public static Rarity getRarity(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return Rarity.byName(data.copyTag().getString(RARITY_TAG));
    }

    // The value of a special affix on one item, or 0
    public static int affix(ItemStack stack, String tag) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(tag);
    }
}
