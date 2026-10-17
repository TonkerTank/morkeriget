package com.morkeriget.smithy;

import java.util.function.Supplier;

import com.morkeriget.Morkeriget;
import com.morkeriget.loot.Rarity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The smithy, opened from the skill screen: loot you don't want is salvaged into essence,
// and essence is spent on rerolling the bonuses of loot you do want
public final class Smithy {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> ESSENCE = ITEMS.registerSimpleItem("essence", new Item.Properties());

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Morkeriget.MODID);
    public static final Supplier<MenuType<SmithyMenu>> MENU = MENUS.register("smithy",
            () -> new MenuType<>(SmithyMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private Smithy() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(Smithy::registerPayloads);
        modEventBus.addListener(Smithy::addToCreativeTab);
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
        }
    }

    // How much essence you get for salvaging an item
    public static int salvageValue(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 1;
            case MAGIC -> 2;
            case RARE -> 4;
            case LEGENDARY -> 10;
        };
    }

    // How much essence a reroll costs. Common items have no bonuses, so they can't be rerolled
    public static int rerollCost(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case MAGIC -> 3;
            case RARE -> 6;
            case LEGENDARY -> 15;
        };
    }
}
