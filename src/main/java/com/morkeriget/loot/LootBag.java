package com.morkeriget.loot;

import java.util.function.Supplier;

import com.morkeriget.Morkeriget;
import com.morkeriget.Text;
import com.morkeriget.journey.Journey;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The loot bag, like the inventory in Diablo 4: loot you pick up goes in here instead of the normal inventory, so your
// hotbar and the things you carry stay as they are. "Salvage all" in the smithy never touches the hotbar. Opened with I.
// It is saved with the player and kept when you die. When it is full, loot goes into the normal inventory again
public final class LootBag {
    public static final int ROWS = 6;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<Contents>> BAG = ATTACHMENTS.register("loot_bag",
            () -> AttachmentType.serializable(Contents::new).copyOnDeath().build());

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Morkeriget.MODID);
    public static final Supplier<MenuType<LootBagMenu>> MENU = MENUS.register("loot_bag",
            () -> new MenuType<>(LootBagMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // 20 ticks is one second. How often "the bag is full" is said while you stand on loot
    private static final int FULL_MESSAGE_INTERVAL = 3 * 20;

    private LootBag() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(LootBag::registerPayloads);
        NeoForge.EVENT_BUS.addListener(LootBag::onPickup);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenLootBagPayload.TYPE, OpenLootBagPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                open(player);
            }
        });
    }

    // The player's own bag. The menu works right on it, so loot picked up while it is open shows up at once
    public static Container get(ServerPlayer player) {
        return player.getData(BAG);
    }

    private static void open(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        Container bag = get(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, menuPlayer) -> new LootBagMenu(id, inventory, bag),
                Text.tr("loot_bag.title")));
        player.playNotifySound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    // Takes loot off the ground and into the bag, the same way the game would put it in the inventory
    private static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        if (Loot.getRarity(stack) == null || entity.hasPickUpDelay()
                || entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())) {
            return;
        }
        Container bag = get(player);
        int free = -1;
        for (int slot = 0; slot < bag.getContainerSize() && free < 0; slot++) {
            if (bag.getItem(slot).isEmpty()) {
                free = slot;
            }
        }
        if (free < 0) {
            if (player.tickCount % FULL_MESSAGE_INTERVAL == 0) {
                player.displayClientMessage(Text.tr("loot_bag.full").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        // It counts for the season journey like any other find
        Journey.found(player, stack);
        int count = stack.getCount();
        bag.setItem(free, stack.copy());
        player.take(entity, count);
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), count);
        entity.discard();
        event.setCanPickup(TriState.FALSE);
    }

    // The items, saved with the player. It is also a Container, so a normal chest menu can show it
    private static final class Contents extends ItemStackHandler implements Container {
        Contents() {
            super(ROWS * 9);
        }

        // A bag saved when it was smaller gets the new size when it is loaded, with its items where they were
        @Override
        public void setSize(int size) {
            super.setSize(Math.max(size, ROWS * 9));
        }

        @Override
        public int getContainerSize() {
            return stacks.size();
        }

        @Override
        public boolean isEmpty() {
            return stacks.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return stacks.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ContainerHelper.removeItem(stacks, slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(stacks, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            stacks.set(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive();
        }

        @Override
        public void clearContent() {
            stacks.clear();
        }
    }
}
