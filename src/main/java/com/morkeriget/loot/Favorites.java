package com.morkeriget.loot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Favorites, like marking an item in Diablo 4: Alt+click loot in any inventory screen to give it a star (see
// FavoriteClicks). A favorite can't be salvaged or have its power extracted in the smithy, neither on its own nor with
// "Smelt alle". Alt+click again takes the star off
public final class Favorites {
    private static final String TAG = "morkeriget_favorite";

    private Favorites() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Favorites::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FavoritePayload.TYPE, FavoritePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                toggle(player, payload);
            }
        });
    }

    public static boolean is(ItemStack stack) {
        return Loot.affix(stack, TAG) > 0;
    }

    // A rerolled item is a new stack, so the star is moved over
    static void copy(ItemStack from, ItemStack to) {
        if (is(from)) {
            CustomData.update(DataComponents.CUSTOM_DATA, to, tag -> tag.putBoolean(TAG, true));
        }
    }

    // The slot is checked against the menu the player really has open, so a late packet can't change the wrong item
    private static void toggle(ServerPlayer player, FavoritePayload payload) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || payload.slot() < 0 || payload.slot() >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.slots.get(payload.slot());
        ItemStack stack = slot.getItem();
        if (Loot.getRarity(stack) == null) {
            return;
        }
        boolean favorite = !is(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (favorite) {
                tag.putBoolean(TAG, true);
            } else {
                tag.remove(TAG);
            }
        });
        slot.setChanged();
        player.playNotifySound(favorite ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f,
                favorite ? 1.4f : 0.8f);
    }
}
