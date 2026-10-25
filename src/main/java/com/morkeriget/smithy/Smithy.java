package com.morkeriget.smithy;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The smithy, opened from the skill screen: loot you don't want is salvaged into essence,
// and essence is spent on rerolling the bonuses of loot you do want
public final class Smithy {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> ESSENCE = ITEMS.registerSimpleItem("essence", new Item.Properties());
    public static final Map<Gem, DeferredItem<Item>> GEMS = new EnumMap<>(Gem.class);

    static {
        for (Gem gem : Gem.values()) {
            GEMS.put(gem, ITEMS.registerItem("gem_" + gem.name().toLowerCase(), properties -> new GemItem(gem, properties), new Item.Properties()));
        }
    }

    // Elites sometimes drop a gem, bosses always drop one or two
    private static final double ELITE_GEM_CHANCE = 0.12;

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Morkeriget.MODID);
    public static final Supplier<MenuType<SmithyMenu>> MENU = MENUS.register("smithy",
            () -> new MenuType<>(SmithyMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private Smithy() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        Codex.register(modEventBus);
        modEventBus.addListener(Smithy::registerPayloads);
        modEventBus.addListener(Smithy::addToCreativeTab);
        NeoForge.EVENT_BUS.addListener(Smithy::onDrops);
    }

    // Higher level monsters drop better gems
    private static void onDrops(LivingDropsEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || !(event.getEntity().getKillCredit() instanceof Player)) {
            return;
        }
        RandomSource random = entity.getRandom();
        int count = Bosses.isBoss(entity) ? 1 + random.nextInt(2) : Elites.isElite(entity) && random.nextDouble() < ELITE_GEM_CHANCE ? 1 : 0;
        int level = MonsterLevels.get(entity);
        for (int i = 0; i < count; i++) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), Gems.random(random, level, 0)));
        }
    }

    // What it costs to combine three gems of this tier into one of the next
    public static int combineCost(int tier) {
        return tier * 2;
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenSmithyPayload.TYPE, OpenSmithyPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                player.openMenu(new SimpleMenuProvider((id, inventory, menuPlayer) -> new SmithyMenu(id, inventory),
                        Component.literal("Smedje")));
            }
        });
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ESSENCE);
            for (DeferredItem<Item> gem : GEMS.values()) {
                event.accept(gem);
            }
        }
    }

    // How much essence you get for salvaging an item
    // The rarity the smithy treats the item as. Plain vanilla gear, like a zombie's helmet, counts as common, so it can
    // be salvaged too. Null for things the smithy can't use
    public static Rarity salvageRarity(ItemStack stack) {
        Rarity rarity = Loot.getRarity(stack);
        return rarity == null && Loot.isPlainGear(stack) ? Rarity.COMMON : rarity;
    }

    public static int salvageValue(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 1;
            case MAGIC -> 2;
            case RARE -> 4;
            case LEGENDARY -> 10;
            case SET -> 12;
            case MYTHIC -> 50;
        };
    }

    // How much essence a reroll costs. Common items have no bonuses, so they can't be rerolled
    public static int rerollCost(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case MAGIC -> 3;
            case RARE -> 6;
            case LEGENDARY, SET -> 15;
            case MYTHIC -> 40;
        };
    }
}
