package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.ElitePacks;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.journey.Journey;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.skill.Minions;
import com.morkeriget.smithy.Durability;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// Monsters you kill can drop weapons, armour, shields and jewelry with a rarity, an item power and random bonuses (affixes)
public final class Loot {
    public static final String RARITY_TAG = "morkeriget_rarity";
    private static final String ITEM_LEVEL_TAG = "morkeriget_item_level";
    private static final String ITEM_POWER_TAG = "morkeriget_item_power";
    private static final String ITEM_LEVEL_LORE = "Genstandslevel ";
    static final String ITEM_POWER_LORE = "Genstandsstyrke ";
    // Special affixes. The value is stored on the item and used in PlayerHits
    public static final String LIFESTEAL_TAG = "morkeriget_lifesteal";
    public static final String FIRE_TAG = "morkeriget_fire";
    public static final String CRUSHING_TAG = "morkeriget_crushing";
    public static final String ARROW_DAMAGE_TAG = "morkeriget_arrow_damage";
    public static final String THORNS_TAG = "morkeriget_thorns";
    // Every staff has this, on top of its affixes. Not the same tag as sapphires, since Gems writes that one over
    public static final String STAFF_MANA_TAG = "morkeriget_staff_mana";
    private static final int STAFF_MANA_MIN = 15;
    private static final int STAFF_MANA_MAX = 35;
    // How often a monster still drops its rotten flesh, see lessRottenFlesh
    private static final double ROTTEN_FLESH_CHANCE = 0.25;
    // Greater bonuses, see Quality
    private static final String GREATER_TAG = "morkeriget_greater";
    private static final double GREATER_BONUS = 2.0;
    private static final double NORMAL_GREATER_CHANCE = 0.03;
    private static final int GREATER_POWER = 25;
    // How much stronger a dungeon made the bonuses, in percent, so a reroll keeps it. See Quality
    private static final String STRENGTH_TAG = "morkeriget_strength";
    // The worst a bonus could roll, in percent, so a reroll keeps it too. See Quality
    private static final String FLOOR_TAG = "morkeriget_floor";
    private static final double PERCENT_PER_LEVEL = 0.005;
    private static final double PERCENT_CAP = 2.0;

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
            // Staffs drop at the same levels as the swords of their tier
            new LootItem(Staffs.OAK::get, 1, 9),
            new LootItem(Staffs.BLAZE::get, 3, ANY_LEVEL),
            new LootItem(Staffs.STORM::get, 10, ANY_LEVEL),
            new LootItem(Staffs.STAR::get, 20, ANY_LEVEL),
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

    // How much an affix grows with the item level. Flat stats grow the most. Percent stats like speed and the special
    // affixes only grow a little, up to half as much again at level 100, or they get silly when they add up over many items
    private enum Scaling {
        FULL, HALF, NONE
    }

    // A bonus that can roll on the given kinds of item. min and max are scaled by how good the roll is.
    // Stat affixes change an attribute. Special affixes are stored under tag, with lore as the text
    private record Affix(Set<ItemKind> kinds, Holder<Attribute> attribute, AttributeModifier.Operation operation,
                         double min, double max, String tag, String lore, Scaling scaling) {
        static Affix stat(Set<ItemKind> kinds, Holder<Attribute> attribute, AttributeModifier.Operation operation, double min, double max,
                          Scaling scaling) {
            return new Affix(kinds, attribute, operation, min, max, null, null, scaling);
        }

        static Affix special(Set<ItemKind> kinds, String tag, int min, int max, String lore) {
            return new Affix(kinds, null, null, min, max, tag, lore, Scaling.NONE);
        }
    }

    private static final List<Affix> AFFIXES = List.of(
            Affix.stat(MELEE, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 1, 4, Scaling.FULL),
            Affix.stat(MELEE, Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05, 0.2, Scaling.NONE),
            // Health only grows half as fast, since armour already makes you much tougher at high levels
            Affix.stat(ALL, Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 2, 6, Scaling.HALF),
            Affix.stat(ALL, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.03, 0.12, Scaling.NONE),
            Affix.stat(DEFENSIVE, Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1, 4, Scaling.FULL),
            Affix.stat(EnumSet.of(ItemKind.ARMOR, ItemKind.SHIELD), Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, 1, 3, Scaling.FULL),
            Affix.stat(ALL, Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, 1, 3, Scaling.NONE),
            Affix.stat(EnumSet.of(ItemKind.MELEE), Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE, 0.5, 1.5, Scaling.NONE),
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
        Staffs.register(modEventBus);
        LootFilter.register(modEventBus);
        LootBag.register(modEventBus);
        Favorites.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(Loot::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(Loot::lessRottenFlesh);
        NeoForge.EVENT_BUS.addListener(Loot::onItemModifiers);
        SetBonuses.register();
    }

    // The item's own attributes before any bonuses. Armor keeps its base armor outside the component, so writing
    // bonuses into an empty component would lose it
    public static ItemAttributeModifiers baseModifiers(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.modifiers().isEmpty() ? stack.getItem().getDefaultAttributeModifiers(stack) : modifiers;
    }

    // The bonuses on every item have the same ids (affix_0, gem_0 and so on), but the game takes a bonus off by its id
    // when gear changes, so the same id on a helmet and boots would cancel each other out. Here each one gets its slot
    // in the id. Armor rolled before baseModifiers existed also gets its base armor back. A worn-out item gives none of
    // them, see Durability
    private static void onItemModifiers(ItemAttributeModifierEvent event) {
        List<ItemAttributeModifiers.Entry> bonuses = event.getModifiers().stream()
                .filter(entry -> entry.modifier().id().getNamespace().equals(Morkeriget.MODID)
                        && (entry.modifier().id().getPath().startsWith("affix_") || entry.modifier().id().getPath().startsWith("gem_")))
                .toList();
        if (bonuses.isEmpty()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        boolean broken = Durability.isBroken(stack);
        for (ItemAttributeModifiers.Entry entry : bonuses) {
            AttributeModifier modifier = entry.modifier();
            event.removeModifier(entry.attribute(), modifier.id());
            if (!broken) {
                event.addModifier(entry.attribute(), new AttributeModifier(modifier.id().withSuffix("_" + entry.slot().getSerializedName()),
                        modifier.amount(), modifier.operation()), entry.slot());
            }
        }
        for (ItemAttributeModifiers.Entry entry : stack.getItem().getDefaultAttributeModifiers(stack).modifiers()) {
            event.addModifier(entry.attribute(), entry.modifier(), entry.slot());
        }
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        // Kill credit instead of the damage source, so a monster that burns to death after your hit still drops loot
        if (!(event.getEntity() instanceof Enemy) || Minions.isAlly(event.getEntity())) {
            return;
        }
        // Music discs, spider eyes and plain vanilla gear are thrown away, also when no player got the kill
        int replaced = removeJunk(event);
        if (!(event.getEntity().getKillCredit() instanceof Player)) {
            return;
        }
        RandomSource random = event.getEntity().getRandom();
        // Loot gets the level of the monster. Monsters from before monster levels existed count as level 1
        int itemLevel = Math.max(1, MonsterLevels.get(event.getEntity()));
        // Legendary powers are mostly for the class of the one who got the kill
        ServerPlayer killer = event.getEntity().getKillCredit() instanceof ServerPlayer player ? player : null;
        PlayerClass killerClass = killer != null ? Progression.playerClass(killer) : null;
        List<Rarity> drops = new ArrayList<>();
        if (Bosses.isBoss(event.getEntity())) {
            // The rarest drop in the game takes the place of the boss' legendary
            drops.add(random.nextDouble() < Config.MYTHIC_CHANCE_BOSS.get() ? Rarity.MYTHIC
                    : random.nextDouble() < Config.SET_CHANCE_BOSS.get() ? Rarity.SET : Rarity.LEGENDARY);
            drops.add(Rarity.roll(random, Rarity.RARE));
            drops.add(Rarity.roll(random, Rarity.RARE));
        } else if (Elites.isElite(event.getEntity())) {
            drops.add(random.nextDouble() < Config.SET_CHANCE_ELITE.get() ? Rarity.SET : Rarity.roll(random, Rarity.RARE));
        } else if (ElitePacks.isMember(event.getEntity())) {
            // The monsters in an elite's pack drop more often than normal ones, and at least magic
            if (random.nextDouble() < ElitePacks.memberLootChance()) {
                drops.add(Rarity.roll(random, Rarity.MAGIC));
            }
        } else if (random.nextFloat() < Config.DROP_CHANCE.get()) {
            drops.add(Rarity.roll(random, Rarity.COMMON));
        }
        // Each piece of junk that was thrown away becomes a real piece of loot instead
        for (int i = 0; i < replaced; i++) {
            drops.add(Rarity.roll(random, Rarity.COMMON));
        }
        Rarity best = null;
        // What the killer's loot filter salvaged, dropped as one stack of essence
        int essence = 0;
        for (Rarity rarity : drops) {
            if (killer != null && LootFilter.hides(killer, rarity)) {
                essence += Smithy.salvageValue(rarity);
                continue;
            }
            ItemStack stack = switch (rarity) {
                case SET -> createSet(random, itemLevel);
                case MYTHIC -> createMythic(random, itemLevel, killerClass);
                default -> create(random, rarity, itemLevel, killerClass);
            };
            if (rarity == Rarity.MYTHIC && killer != null) {
                announceMythic(killer, stack);
            }
            event.getDrops().add(new ItemEntity(event.getEntity().level(),
                    event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack));
            if (best == null || rarity.ordinal() > best.ordinal()) {
                best = rarity;
            }
        }
        if (essence > 0) {
            event.getDrops().add(new ItemEntity(event.getEntity().level(),
                    event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), new ItemStack(Smithy.ESSENCE.get(), essence)));
        }
        if (best != null && event.getEntity().level() instanceof ServerLevel level) {
            celebrate(level, event.getEntity().position(), best);
        }
    }

    // Zombies, husks and drowned fill your inventory with rotten flesh when you kill lots of them, so they only drop
    // a single piece now and then. Only zombies of every kind, so a player who dies keeps all of theirs
    // Takes the drops nobody wants out, and says how many should be loot instead. Plain gear, like the helmets zombies
    // spawn with, is only taken when the monster can't pick things up, since then it can't be something a player lost
    private static int removeJunk(LivingDropsEvent event) {
        boolean mayCarryPlayerItems = event.getEntity() instanceof Mob mob && mob.canPickUpLoot();
        int[] replaced = {0};
        event.getDrops().removeIf(drop -> {
            ItemStack stack = drop.getItem();
            boolean junk = stack.has(DataComponents.JUKEBOX_PLAYABLE) || stack.is(Items.SPIDER_EYE)
                    || !mayCarryPlayerItems && isPlainGear(stack);
            if (junk) {
                replaced[0]++;
            }
            return junk;
        });
        return replaced[0];
    }

    // A vanilla weapon or piece of armor without a rarity or enchantments
    public static boolean isPlainGear(ItemStack stack) {
        Item item = stack.getItem();
        return getRarity(stack) == null && !stack.isEnchanted() && (item instanceof ArmorItem || item instanceof SwordItem
                || item instanceof ProjectileWeaponItem || item instanceof ShieldItem || item instanceof TridentItem);
    }

    private static void lessRottenFlesh(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Zombie)) {
            return;
        }
        RandomSource random = event.getEntity().getRandom();
        boolean keepOne = random.nextDouble() < ROTTEN_FLESH_CHANCE;
        event.getDrops().removeIf(drop -> {
            if (!drop.getItem().is(Items.ROTTEN_FLESH)) {
                return false;
            }
            drop.getItem().setCount(1);
            return !keepOne;
        });
    }

    // A sound and a burst of particles when something good drops, so you notice it even in a big fight
    private static void celebrate(ServerLevel level, Vec3 position, Rarity rarity) {
        double x = position.x;
        double y = position.y + 0.5;
        double z = position.z;
        BlockPos pos = BlockPos.containing(position);
        if (rarity == Rarity.MYTHIC) {
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0f, 1.2f);
            level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 80, 0.6, 0.8, 0.6, 0.05);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 100, 0.4, 1.0, 0.4, 0.3);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.1, 3.0, 0.1, 0.02);
        } else if (rarity == Rarity.SET) {
            level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.5f);
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.8f);
            level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 50, 0.5, 0.8, 0.5, 0.1);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.1, 1.5, 0.1, 0.02);
        } else if (rarity == Rarity.LEGENDARY) {
            // Volume above 1 only makes a sound reach further, not louder, so these are sounds that are loud on their own
            level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
            level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 60, 0.3, 0.6, 0.3, 0.4);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.1, 1.5, 0.1, 0.02);
        } else if (rarity == Rarity.RARE) {
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.5f);
            level.sendParticles(ParticleTypes.WAX_ON, x, y, z, 15, 0.3, 0.4, 0.3, 0.5);
        } else if (rarity == Rarity.MAGIC) {
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.6f);
            level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 20, 0.3, 0.4, 0.3, 0.5);
        }
    }

    // Everyone on the server hears about it when someone finds a mythic item. It counts for the season journey when
    // it is picked up, see Journey, since someone else could get there first
    public static void announceMythic(ServerPlayer finder, ItemStack stack) {
        Component message = Component.literal("✦ ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(finder.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" fandt en mytisk genstand: ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(stack.getHoverName());
        finder.server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static Item randomItem(RandomSource random, int itemLevel) {
        return randomItem(random, itemLevel, item -> true);
    }

    // A random base item that can drop at this level and is the kind asked for, e.g. only helmets.
    // null if there is none
    private static Item randomItem(RandomSource random, int itemLevel, Predicate<Item> kind) {
        List<Item> possible = LOOT_ITEMS.stream()
                .filter(loot -> itemLevel >= loot.minLevel && itemLevel <= loot.maxLevel)
                .map(loot -> loot.item.get())
                .filter(kind)
                .toList();
        return possible.isEmpty() ? null : possible.get(random.nextInt(possible.size()));
    }

    public static ItemStack create(RandomSource random, Rarity rarity, int itemLevel) {
        return create(random, rarity, itemLevel, (PlayerClass) null);
    }

    // forClass is the class of the one who found it, so a legendary mostly gets a power they can use. It may be null
    public static ItemStack create(RandomSource random, Rarity rarity, int itemLevel, PlayerClass forClass) {
        return roll(new ItemStack(randomItem(random, itemLevel)), random, rarity, itemLevel, null, null, power(random, rarity, forClass), null);
    }

    // Loot for this player: rolled better than normal (see Quality), and a weapon is always the same kind as the one
    // they fight with, so a ranger with a bow doesn't get a staff. Used for dungeon rewards
    public static ItemStack createFor(ServerPlayer player, RandomSource random, Rarity rarity, int itemLevel, Quality quality) {
        WeaponType type = weaponType(player);
        Item item = randomItem(random, itemLevel, candidate -> !outclassed(candidate, itemLevel) && (type == null
                || weaponType(new ItemStack(candidate)) == null || weaponType(new ItemStack(candidate)) == type));
        if (item == null) {
            item = randomItem(random, itemLevel);
        }
        PlayerClass playerClass = Progression.playerClass(player);
        return roll(new ItemStack(item), random, rarity, itemLevel, null, null, power(random, rarity, playerClass), null, quality);
    }

    // From item level 20, when netherite and the star staff drop, the worse materials are left out of rewards, since
    // their base damage and armor are much lower
    static boolean outclassed(Item item, int itemLevel) {
        if (itemLevel < 20) {
            return false;
        }
        return List.of(Items.STONE_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.IRON_AXE, Items.DIAMOND_SWORD, Items.DIAMOND_AXE,
                        Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
                        Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                        Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                        Staffs.OAK.get(), Staffs.BLAZE.get(), Staffs.STORM.get())
                .contains(item);
    }

    private enum WeaponType {
        MELEE, STAFF, BOW, CROSSBOW
    }

    // null for armor, shields and jewelry
    private static WeaponType weaponType(ItemStack stack) {
        if (Staffs.isStaff(stack)) {
            return WeaponType.STAFF;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return WeaponType.CROSSBOW;
        }
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return WeaponType.BOW;
        }
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem ? WeaponType.MELEE : null;
    }

    // What the player fights with: the weapon slot, then the hand, then what fits their class. null if nothing does
    private static WeaponType weaponType(ServerPlayer player) {
        for (ItemStack stack : List.of(player.getInventory().getItem(0), player.getMainHandItem())) {
            WeaponType type = weaponType(stack);
            if (type != null) {
                return type;
            }
        }
        PlayerClass playerClass = Progression.playerClass(player);
        if (playerClass == null) {
            return null;
        }
        return switch (playerClass) {
            case BARBARIAN -> WeaponType.MELEE;
            case RANGER -> WeaponType.BOW;
            case SORCERER, NECROMANCER -> WeaponType.STAFF;
        };
    }

    // The same, but rolled better than normal, see Quality. Used for dungeon rewards
    public static ItemStack create(RandomSource random, Rarity rarity, int itemLevel, PlayerClass forClass, Quality quality) {
        // Dungeon loot only comes in the best materials, see outclassed
        Item item = quality.noFiller() ? randomItem(random, itemLevel, candidate -> !outclassed(candidate, itemLevel)) : null;
        return roll(new ItemStack(item != null ? item : randomItem(random, itemLevel)), random, rarity, itemLevel, null, null, power(random, rarity, forClass), null, quality);
    }

    // How well an item's bonuses roll. floor is the worst a bonus can roll, from 0 (any roll) to 1 (always the best).
    // Each bonus has greaterChance of being a greater bonus, like in Diablo 4: twice as strong and marked with ✦ in the
    // tooltip. guaranteed is how many greater bonuses the item has at least, and extraAffixes how many bonuses it has
    // on top of what its rarity gives. Every bonus is strength times as strong, and noFiller leaves out the weak ones
    // (luck and knockback)
    public record Quality(double floor, double greaterChance, int guaranteed, int extraAffixes, double strength, boolean noFiller) {
        public Quality(double floor, double greaterChance, int guaranteed) {
            this(floor, greaterChance, guaranteed, 0, 1, false);
        }

        // Dungeon loot: rolled in the top half and better the higher the tier (from 50% at tier I to the top 5% at
        // tier X), more greater bonuses, no filler, and 4% stronger bonuses per tier
        public static Quality dungeon(int tier, int guaranteed, int extraAffixes) {
            return new Quality(0.5 + 0.05 * (tier - 1), 0.08 + 0.02 * (tier - 1), guaranteed, extraAffixes, 1 + 0.04 * tier, true);
        }

        // Legendary, set and mythic loot sometimes has a greater bonus. Worse loot never does
        static Quality normal(Rarity rarity) {
            return new Quality(0, rarity.ordinal() >= Rarity.LEGENDARY.ordinal() ? NORMAL_GREATER_CHANCE : 0, 0);
        }
    }

    // Loot made from a chosen base item, e.g. for the /morkeriget loot command
    public static ItemStack create(RandomSource random, Rarity rarity, int itemLevel, Item item) {
        return roll(new ItemStack(item), random, rarity, itemLevel, null, null, power(random, rarity, null), null);
    }

    // Loot of one kind of base item, e.g. a random ring, for the merchant. Empty if no such item drops at this level
    public static ItemStack createOfKind(RandomSource random, Rarity rarity, int itemLevel, PlayerClass forClass, Predicate<Item> kind) {
        Item item = randomItem(random, itemLevel, kind);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return roll(new ItemStack(item), random, rarity, itemLevel, null, null, power(random, rarity, forClass), null);
    }

    // A legendary with a chosen power, for the /morkeriget legendary command
    public static ItemStack createLegendary(RandomSource random, int itemLevel, LegendaryPower power) {
        return roll(new ItemStack(randomItem(random, itemLevel)), random, Rarity.LEGENDARY, itemLevel, null, null, power, null);
    }

    private static LegendaryPower power(RandomSource random, Rarity rarity, PlayerClass forClass) {
        return rarity == Rarity.LEGENDARY ? LegendaryPower.roll(random, forClass) : null;
    }

    // A random piece from a random set
    public static ItemStack createSet(RandomSource random, int itemLevel) {
        ItemSet set = ItemSet.values()[random.nextInt(ItemSet.values().length)];
        return createSet(random, itemLevel, set, set.pieces.get(random.nextInt(set.pieces.size())));
    }

    public static ItemStack createSet(RandomSource random, int itemLevel, ItemSet set, ItemSet.Piece piece) {
        return roll(new ItemStack(piece.baseItem(itemLevel)), random, Rarity.SET, itemLevel, set, piece, null, null);
    }

    // A mythic item, mostly one the class of the one who found it can use. forClass may be null
    public static ItemStack createMythic(RandomSource random, int itemLevel, PlayerClass forClass) {
        return createMythic(random, itemLevel, MythicItem.roll(random, forClass));
    }

    public static ItemStack createMythic(RandomSource random, int itemLevel, MythicItem mythic) {
        return roll(new ItemStack(mythic.baseItem()), random, Rarity.MYTHIC, itemLevel, null, null, null, mythic);
    }

    // The same base item with the same rarity, level, set, legendary power and mythic, but new bonuses. Used by the smithy.
    // Legendaries from before powers existed get one
    public static ItemStack reroll(ItemStack old, RandomSource random) {
        ItemStack stack = new ItemStack(old.getItem());
        if (old.has(DataComponents.DAMAGE)) {
            stack.set(DataComponents.DAMAGE, old.get(DataComponents.DAMAGE));
        }
        Rarity rarity = getRarity(old) != null ? getRarity(old) : Rarity.COMMON;
        LegendaryPower power = LegendaryPower.of(old) != null ? LegendaryPower.of(old) : power(random, rarity, null);
        // It keeps as many bonuses and greater bonuses as it had, how strong a dungeon made them and how well they roll,
        // but which bonuses they are is rolled again
        double strength = affix(old, STRENGTH_TAG) / 100.0;
        Quality quality = new Quality(floor(old), Quality.normal(rarity).greaterChance(), greater(old).size(),
                Math.max(0, affixCount(old) - rarity.affixCount), Math.max(1, strength), strength > 1);
        ItemStack rerolled = roll(stack, random, rarity, itemLevel(old), ItemSet.of(old), ItemSet.piece(old), power, MythicItem.of(old), quality);
        // The sockets and gems stay as they were, and it is still the same find for the season journey
        Gems.copy(old, rerolled);
        Journey.copyFound(old, rerolled);
        // What the player put on it themselves stays too
        rerolled.copyFrom(old, DataComponents.ENCHANTMENTS, DataComponents.TRIM, DataComponents.DYED_COLOR, DataComponents.REPAIR_COST);
        Favorites.copy(old, rerolled);
        Masterwork.carryOver(old, rerolled, random);
        return rerolled;
    }

    // The worst a bonus on the item can roll, from 0 to 1. Dungeon items from before it was saved have it worked out from
    // their strength, since both come from the tier, see Quality.dungeon
    static double floor(ItemStack stack) {
        if (affix(stack, FLOOR_TAG) > 0) {
            return affix(stack, FLOOR_TAG) / 100.0;
        }
        double strength = affix(stack, STRENGTH_TAG) / 100.0;
        if (strength <= 1) {
            return 0;
        }
        int tier = Math.max(1, (int) Math.round((strength - 1) / 0.04));
        return Quality.dungeon(tier, 0, 0).floor();
    }

    // Swaps the legendary power on the item for another one, both what it does and the two lines in the lore.
    // Used by the codex in the smithy
    public static void setPower(ItemStack stack, LegendaryPower power) {
        List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        List<Component> powerLines = powerLore(power);
        int line = 0;
        while (line < lore.size() && !lore.get(line).getString().startsWith("★ ")) {
            line++;
        }
        if (line + 1 < lore.size()) {
            lore.set(line, powerLines.get(0));
            lore.set(line + 1, powerLines.get(1));
        } else {
            lore.add(Component.empty());
            lore.addAll(powerLines);
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        power.markOn(stack);
    }

    private static List<Component> powerLore(LegendaryPower power) {
        String classText = power.playerClass != null ? " (" + power.playerClass.displayName + ")" : "";
        return List.of(Component.literal("★ " + power.displayName + classText).withStyle(ChatFormatting.GOLD),
                Component.literal(power.description).withStyle(ChatFormatting.YELLOW));
    }

    // Gives a fresh item its affixes, name and lore. set and piece are null for items that aren't part of a set,
    // power is null for items that aren't legendary, and mythic for items that aren't mythic
    private static ItemStack roll(ItemStack stack, RandomSource random, Rarity rarity, int itemLevel, ItemSet set, ItemSet.Piece piece,
                                  LegendaryPower power, MythicItem mythic) {
        return roll(stack, random, rarity, itemLevel, set, piece, power, mythic, Quality.normal(rarity));
    }

    private static ItemStack roll(ItemStack stack, RandomSource random, Rarity rarity, int itemLevel, ItemSet set, ItemSet.Piece piece,
                                  LegendaryPower power, MythicItem mythic, Quality rollQuality) {
        // Flat affixes get stronger with the item level, see Scaling
        double fullScale = 1 + Config.AFFIX_PER_LEVEL.get() * (itemLevel - 1);
        double halfScale = 1 + Config.AFFIX_PER_LEVEL.get() * (itemLevel - 1) / 2;
        // Percent and special bonuses grow slowly, up to half as much again at level 100
        double percentScale = 1 + Math.min(0.5, PERCENT_PER_LEVEL * (itemLevel - 1));
        ItemKind kind = ItemKind.of(stack);
        EquipmentSlotGroup slot = slotGroup(stack);

        // Pick different affixes that fit this kind of item and roll how strong each one is
        List<Affix> available = new ArrayList<>(AFFIXES.stream().filter(affix -> affix.kinds.contains(kind))
                .filter(affix -> !rollQuality.noFiller() || affix.attribute != Attributes.LUCK && affix.attribute != Attributes.ATTACK_KNOCKBACK).toList());
        ItemAttributeModifiers modifiers = baseModifiers(stack);
        List<Component> affixLore = new ArrayList<>();
        if (Staffs.isStaff(stack)) {
            int mana = STAFF_MANA_MIN + random.nextInt(STAFF_MANA_MAX - STAFF_MANA_MIN + 1);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(STAFF_MANA_TAG, mana));
            affixLore.add(Component.literal("+" + mana + "% hurtigere mana").withStyle(ChatFormatting.AQUA));
        }
        double totalQuality = 0;
        // Which of the bonuses are sure to be greater ones
        int affixCount = Math.min(rarity.affixCount + rollQuality.extraAffixes(), available.size());
        List<Integer> sureGreater = new ArrayList<>();
        for (int i = 0; i < affixCount; i++) {
            sureGreater.add(i);
        }
        while (sureGreater.size() > rollQuality.guaranteed()) {
            sureGreater.remove(random.nextInt(sureGreater.size()));
        }
        ListTag greater = new ListTag();
        for (int i = 0; i < affixCount; i++) {
            Affix affix = available.remove(random.nextInt(available.size()));
            // Mythic bonuses always roll in the top 40%, and some rewards have a floor of their own
            double floor = Math.max(rollQuality.floor(), mythic != null ? 0.6 : 0);
            double quality = floor + (1 - floor) * random.nextDouble();
            totalQuality += quality;
            double scale = switch (affix.scaling) {
                case FULL -> fullScale;
                case HALF -> halfScale;
                case NONE -> percentScale;
            };
            boolean isGreater = sureGreater.contains(i) || random.nextDouble() < rollQuality.greaterChance();
            double boost = scale * rollQuality.strength() * (isGreater ? GREATER_BONUS : 1);
            // Percent and special bonuses can at most be twice their normal best, or a few items would add up to silly numbers
            if (affix.scaling == Scaling.NONE) {
                boost = Math.min(PERCENT_CAP, boost);
            }
            double amount = (affix.min + (affix.max - affix.min) * quality) * boost;
            if (isGreater) {
                greater.add(StringTag.valueOf(affix.tag != null ? affix.tag : "affix_" + i));
            }
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
        int itemPower = 100 + rarity.ordinal() * 150 + itemLevel * 10 + (int) (totalQuality * 50) + random.nextInt(25)
                + (int) Math.round((rollQuality.strength() - 1) * 250)
                + greater.size() * GREATER_POWER;

        Component baseName = stack.getItem().getDescription();
        Component name;
        if (mythic != null) {
            name = Component.literal(mythic.displayName);
        } else if (set != null) {
            name = Component.literal(set.displayName + " ").append(baseName);
        } else if (rarity == Rarity.LEGENDARY) {
            name = Component.literal(LEGENDARY_NAMES.get(random.nextInt(LEGENDARY_NAMES.size())) + " ").append(baseName);
        } else {
            name = baseName.copy();
        }
        stack.set(DataComponents.ITEM_NAME, name.copy().withStyle(rarity.color));
        String kindLine = set != null ? "Sæt-genstand: " + set.displayName + " sæt" : rarity.displayName + " genstand";
        List<Component> lore = new ArrayList<>(List.of(
                Component.literal(kindLine).withStyle(rarity.color),
                Component.literal(ITEM_LEVEL_LORE + itemLevel).withStyle(ChatFormatting.GRAY),
                Component.literal(ITEM_POWER_LORE + itemPower).withStyle(ChatFormatting.GRAY)));
        lore.addAll(affixLore);
        if (power != null) {
            lore.add(Component.empty());
            lore.addAll(powerLore(power));
            power.markOn(stack);
        }
        if (mythic != null) {
            lore.add(Component.empty());
            lore.add(Component.literal("✦ " + mythic.displayName).withStyle(ChatFormatting.LIGHT_PURPLE));
            lore.add(Component.literal(mythic.description).withStyle(ChatFormatting.DARK_PURPLE));
            mythic.markOn(stack);
        }
        if (set != null) {
            List<String> pieceNames = set.pieces.stream().map(p -> p.displayName).toList();
            lore.add(Component.empty());
            lore.add(Component.literal(set.displayName + " sæt: " + String.join(", ", pieceNames)).withStyle(ChatFormatting.DARK_GREEN));
            lore.add(Component.literal("(2) " + set.twoPieceBonus).withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("(4) " + set.fourPieceBonus).withStyle(ChatFormatting.GRAY));
            set.markOn(stack, piece);
        }
        if (kind == ItemKind.JEWELRY) {
            lore.add(Component.literal("Tag på i Smykker (K)").withStyle(ChatFormatting.DARK_GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        if (rarity == Rarity.LEGENDARY || rarity == Rarity.SET || rarity == Rarity.MYTHIC) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(RARITY_TAG, rarity.name());
            tag.putInt(ITEM_LEVEL_TAG, itemLevel);
            tag.putInt(ITEM_POWER_TAG, itemPower);
            if (!greater.isEmpty()) {
                tag.put(GREATER_TAG, greater);
            }
            if (rollQuality.strength() > 1) {
                tag.putInt(STRENGTH_TAG, (int) Math.round(rollQuality.strength() * 100));
            }
            if (rollQuality.floor() > 0) {
                tag.putInt(FLOOR_TAG, (int) Math.round(rollQuality.floor() * 100));
            }
        });
        int sockets = Gems.rollSockets(random, rarity);
        if (sockets > 0) {
            Gems.setSockets(stack, sockets);
        }
        return stack;
    }

    // Where the item's attribute bonuses count. Jewelry bonuses are put on the player by Jewelry, so the slot there is
    // only a place to store them. Players never wear anything in the body slot, so vanilla never uses them by itself
    public static EquipmentSlotGroup slotGroup(ItemStack stack) {
        return switch (ItemKind.of(stack)) {
            case ARMOR -> EquipmentSlotGroup.bySlot(((ArmorItem) stack.getItem()).getEquipmentSlot());
            case SHIELD -> EquipmentSlotGroup.OFFHAND;
            case JEWELRY -> EquipmentSlotGroup.BODY;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }

    public static int itemLevel(ItemStack stack) {
        return Math.max(1, storedNumber(stack, ITEM_LEVEL_TAG, ITEM_LEVEL_LORE));
    }

    public static int itemPower(ItemStack stack) {
        return storedNumber(stack, ITEM_POWER_TAG, ITEM_POWER_LORE);
    }

    // Raises the item power, both the number and the line in the lore. Used by Masterwork
    static void addItemPower(ItemStack stack, int amount) {
        int power = itemPower(stack) + amount;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(ITEM_POWER_TAG, power));
        List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).getString().startsWith(ITEM_POWER_LORE)) {
                lore.set(i, Component.literal(ITEM_POWER_LORE + power).withStyle(ChatFormatting.GRAY));
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    // Items from before the level and power were saved as numbers only have them in the lore, so read them from there
    private static int storedNumber(ItemStack stack, String tag, String lorePrefix) {
        int stored = affix(stack, tag);
        if (stored > 0) {
            return stored;
        }
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            String text = line.getString();
            if (text.startsWith(lorePrefix)) {
                try {
                    return Integer.parseInt(text.substring(lorePrefix.length()).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    // The same text vanilla uses for attribute bonuses, e.g. "+3 Attack Damage" or "+12% Speed"
    static Component statLine(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
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

    // Whether the bonus is a percent or special one, which grows slowly with level and masterwork (see Scaling). Special
    // ones have no attribute
    static boolean growsSlowly(Holder<Attribute> attribute) {
        return AFFIXES.stream().anyMatch(affix -> affix.attribute == attribute && affix.scaling == Scaling.NONE);
    }

    // The text a special affix has in the lore, with %d for the value
    static String specialLore(String tag) {
        return AFFIXES.stream().filter(affix -> tag.equals(affix.tag)).map(Affix::lore).findFirst().orElse("%d");
    }

    // How many bonuses the item has, not counting a staff's mana or gems
    private static int affixCount(ItemStack stack) {
        int count = (int) stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.modifier().id().getNamespace().equals(Morkeriget.MODID) && entry.modifier().id().getPath().startsWith("affix_"))
                .count();
        for (Affix affix : AFFIXES) {
            if (affix.tag != null && affix(stack, affix.tag) > 0) {
                count++;
            }
        }
        return count;
    }

    // The keys of the item's greater bonuses: "affix_0" and so on for attribute bonuses, the tag for special ones
    static List<String> greater(ItemStack stack) {
        ListTag list = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getList(GREATER_TAG, Tag.TAG_STRING);
        return list.stream().map(Tag::getAsString).toList();
    }

    // The value of a special affix on one item, or 0
    public static int affix(ItemStack stack, String tag) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(tag);
    }
}
