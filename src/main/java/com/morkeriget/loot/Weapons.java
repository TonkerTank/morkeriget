package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.morkeriget.Morkeriget;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The other kinds of melee weapon: daggers, scythes, battle staffs and Minecraft's mace. Each kind always has a bonus of
// its own (Innate), rolled in Loot like the staff's mana, and what it does is in WeaponEffects. Daggers, scythes and
// battle staffs come in the same four tiers as swords and drop at the same levels
public final class Weapons {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);

    public static final String BACKSTAB_TAG = "morkeriget_backstab";
    public static final String REAP_TAG = "morkeriget_reap";
    public static final String ARCANE_STRIKE_TAG = "morkeriget_arcane_strike";
    public static final String STUN_TAG = "morkeriget_stun";

    // tag is where the number is saved, and lore the key of its text (lore.<lore>) and of the comparison (compare.<lore>)
    public record Innate(String tag, int min, int max, String lore) {
    }

    public enum Kind {
        // Fast and light, and hits harder from behind
        DAGGER(1, -1.6f, new Innate(BACKSTAB_TAG, 30, 60, "backstab")),
        // Slow and heavy, and hits the enemies around the one you hit too
        SCYTHE(5, -3.0f, new Innate(REAP_TAG, 30, 60, "reap")),
        // A staff to fight with: every hit gives mana
        BATTLE_STAFF(3, -2.6f, new Innate(ARCANE_STRIKE_TAG, 2, 5, "arcane_strike")),
        // Minecraft's own, which can stun
        MACE(0, 0, new Innate(STUN_TAG, 10, 20, "stun"));

        final int damage;
        final float speed;
        public final Innate innate;
        // The four tiers, worst first. Empty for the mace
        final List<DeferredItem<Item>> tiers = new ArrayList<>();

        Kind(int damage, float speed, Innate innate) {
            this.damage = damage;
            this.speed = speed;
            this.innate = innate;
        }
    }

    private static final List<Tier> TIERS = List.of(Tiers.STONE, Tiers.IRON, Tiers.DIAMOND, Tiers.NETHERITE);
    private static final List<String> TIER_NAMES = List.of("stone", "iron", "diamond", "netherite");

    static {
        for (Kind kind : List.of(Kind.DAGGER, Kind.SCYTHE, Kind.BATTLE_STAFF)) {
            for (int i = 0; i < TIERS.size(); i++) {
                String name = kind.name().toLowerCase(Locale.ROOT) + "_" + TIER_NAMES.get(i);
                kind.tiers.add(ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(1)
                        .attributes(SwordItem.createAttributes(TIERS.get(i), kind.damage, kind.speed))));
            }
        }
    }

    private Weapons() {
    }

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(Weapons::addToCreativeTab);
    }

    // The kind of weapon, or null for swords, axes, staffs and everything else
    public static Kind kind(ItemStack stack) {
        if (stack.is(Items.MACE)) {
            return Kind.MACE;
        }
        for (Kind kind : Kind.values()) {
            if (kind.tiers.stream().anyMatch(item -> stack.is(item.get()))) {
                return kind;
            }
        }
        return null;
    }

    // The tier of a dagger, scythe or battle staff, from 0 (stone) to 3 (netherite)
    static int tier(Item item) {
        for (Kind kind : Kind.values()) {
            for (int i = 0; i < kind.tiers.size(); i++) {
                if (kind.tiers.get(i).get() == item) {
                    return i;
                }
            }
        }
        return -1;
    }

    // Through Weapons, so the items are registered (in the static block above) before anyone looks at the tiers
    static List<DeferredItem<Item>> tiers(Kind kind) {
        return kind.tiers;
    }

    // All the daggers, scythes and battle staffs, for the loot list
    static List<DeferredItem<Item>> all() {
        List<DeferredItem<Item>> all = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            all.addAll(kind.tiers);
        }
        return all;
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            all().forEach(event::accept);
        }
    }
}
