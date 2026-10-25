package com.morkeriget.stash;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.morkeriget.Morkeriget;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// The stash, opened from the skill screen: a big chest that is the same in every world, so loot can be taken from one
// world to another. It is saved as a file in the game folder (morkeriget/stash), one for each player, instead of in the
// world. It uses the normal chest screen, so nothing has to be drawn
public final class Stash {
    private static final int ROWS = 6;

    private Stash() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Stash::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenStashPayload.TYPE, OpenStashPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                open(player);
            }
        });
    }

    private static void open(ServerPlayer player) {
        HolderLookup.Provider registries = player.registryAccess();
        SimpleContainer container = new SimpleContainer(ROWS * 9);
        UUID uuid = player.getUUID();
        if (!load(uuid, container, registries)) {
            // Opening it empty would save over the broken file, and everything in it would be gone
            player.displayClientMessage(Component.literal("Kisten kunne ikke indlæses - se loggen").withStyle(ChatFormatting.RED), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, menuPlayer) -> new ChestMenu(MenuType.GENERIC_9x6, id, inventory, container, ROWS) {
            // Saved when it is closed, also when the player logs out, together with the player's own inventory. Then a
            // crash takes both back to the same moment, so it can't lose or copy items
            @Override
            public void removed(Player closing) {
                super.removed(closing);
                save(uuid, container, registries);
                player.server.getPlayerList().saveAll();
            }
        }, Component.literal("Kiste")));
        player.playNotifySound(SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    private static Path file(UUID player) {
        return FMLPaths.GAMEDIR.get().resolve(Morkeriget.MODID).resolve("stash").resolve(player + ".dat");
    }

    // False if the file is there but can't be read
    private static boolean load(UUID player, SimpleContainer container, HolderLookup.Provider registries) {
        Path file = file(player);
        if (!Files.exists(file)) {
            return true;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
            for (int slot = 0; slot < items.size(); slot++) {
                container.setItem(slot, items.get(slot));
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Morkeriget.LOGGER.error("Could not load the stash {}", file, e);
            return false;
        }
    }

    // Written to a new file first and then moved, so a crash while saving can't leave half a stash
    private static void save(UUID player, SimpleContainer container, HolderLookup.Provider registries) {
        Path file = file(player);
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, container.getItem(slot));
        }
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), items, true, registries);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(player + ".dat.tmp");
            NbtIo.writeCompressed(tag, temporary);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Morkeriget.LOGGER.error("Could not save the stash {}", file, e);
        }
    }
}
