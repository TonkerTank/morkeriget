package com.morkeriget.merchant;

import java.util.function.Supplier;

import com.morkeriget.Morkeriget;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

// The merchant, opened from the skill screen like the smithy. You pick a kind of item and pay essence for a random one
// at your level, like gambling with Kadala in Diablo. It is never common, and sometimes legendary
public final class Merchant {
    // The chances for what you get. The rest is magic
    static final double LEGENDARY_CHANCE = 0.10;
    static final double RARE_CHANCE = 0.35;

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Morkeriget.MODID);
    public static final Supplier<MenuType<MerchantMenu>> MENU = MENUS.register("merchant",
            () -> new MenuType<>(MerchantMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private Merchant() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
        modEventBus.addListener(Merchant::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenMerchantPayload.TYPE, OpenMerchantPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                player.openMenu(new SimpleMenuProvider((id, inventory, menuPlayer) -> new MerchantMenu(id, inventory),
                        Component.literal("Købmand")));
            }
        });
    }
}
