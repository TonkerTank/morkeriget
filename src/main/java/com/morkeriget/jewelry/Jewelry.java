package com.morkeriget.jewelry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.morkeriget.Morkeriget;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Rings and amulets. The player has two ring slots and one amulet slot, opened from the skill screen.
// Jewelry stays on when the player dies
public final class Jewelry {
    public static final int RING_SLOTS = 2;
    public static final int SLOTS = RING_SLOTS + 1;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> RING = ITEMS.registerSimpleItem("ring", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> AMULET = ITEMS.registerSimpleItem("amulet", new Item.Properties().stacksTo(1));

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Morkeriget.MODID);
    public static final Supplier<MenuType<JewelryMenu>> MENU = MENUS.register("jewelry",
            () -> new MenuType<>(JewelryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<JewelryHandler>> EQUIPPED = ATTACHMENTS.register("jewelry",
            () -> AttachmentType.serializable(JewelryHandler::new).copyOnDeath().build());

    private Jewelry() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Jewelry::registerPayloads);
        modEventBus.addListener(Jewelry::addToCreativeTab);
        NeoForge.EVENT_BUS.addListener(Jewelry::onLogin);
        NeoForge.EVENT_BUS.addListener(Jewelry::onRespawn);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenJewelryPayload.TYPE, OpenJewelryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                open(player);
            }
        });
    }

    // Plain rings and amulets without bonuses, mostly for testing. The good ones drop from monsters
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(RING);
            event.accept(AMULET);
        }
    }

    public static boolean isJewelry(ItemStack stack) {
        return stack.is(RING.get()) || stack.is(AMULET.get());
    }

    // The jewelry the player is wearing
    public static List<ItemStack> worn(Player player) {
        JewelryHandler handler = player.getData(EQUIPPED);
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                stacks.add(handler.getStackInSlot(slot));
            }
        }
        return stacks;
    }

    private static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, menuPlayer) -> new JewelryMenu(id, inventory, player.getData(EQUIPPED)),
                Component.literal("Smykker")));
    }

    // Vanilla only uses an item's attribute bonuses in the normal equipment slots, so for jewelry the bonuses are
    // put on the player by hand. They aren't saved, so this runs again on login, respawn and when jewelry changes
    static void applyStats(ServerPlayer player) {
        JewelryHandler handler = player.getData(EQUIPPED);
        for (int slot = 0; slot < SLOTS; slot++) {
            for (int i = 0; i < JewelryHandler.MAX_BONUSES; i++) {
                ResourceLocation id = bonusId(slot, i);
                for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
                    AttributeInstance instance = player.getAttribute(attribute);
                    if (instance != null) {
                        instance.removeModifier(id);
                    }
                }
            }
            ItemStack stack = handler.getStackInSlot(slot);
            List<ItemAttributeModifiers.Entry> entries = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers();
            for (int i = 0; i < entries.size() && i < JewelryHandler.MAX_BONUSES; i++) {
                ItemAttributeModifiers.Entry entry = entries.get(i);
                AttributeInstance instance = player.getAttribute(entry.attribute());
                if (instance != null) {
                    instance.addOrUpdateTransientModifier(new AttributeModifier(bonusId(slot, i), entry.modifier().amount(), entry.modifier().operation()));
                }
            }
        }
        // Max health may have gone down
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static ResourceLocation bonusId(int slot, int index) {
        return ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "jewelry_" + slot + "_" + index);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyStats(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyStats(player);
        }
    }
}
