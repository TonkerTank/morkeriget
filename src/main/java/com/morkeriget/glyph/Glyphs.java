package com.morkeriget.glyph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.progress.Progression;
import com.morkeriget.skill.Minions;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Glyphs for the paragon board, like in Diablo 4. They drop from dungeon bosses, and right-clicking one puts it in a free
// glyph slot. There is one slot from the first paragon level and more later, see Glyph.slots. Each socketed glyph goes up
// a level every time a dungeon is cleared, but never above twice the dungeon's tier, so the best glyphs take the highest
// tiers. They are taken out again in the paragon screen
public final class Glyphs {
    private static final String KIND_TAG = "morkeriget_glyph";
    private static final String LEVEL_TAG = "morkeriget_glyph_level";
    // How high a dungeon can raise a glyph: this many levels per tier
    private static final int LEVELS_PER_TIER = 2;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> ITEM = ITEMS.registerItem("glyph", GlyphItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    // The socketed glyphs as "KIND:level", one entry per slot, empty for a free one. Kept when the player dies
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<List<String>>> SOCKETED = ATTACHMENTS.register("glyphs",
            () -> AttachmentType.builder(() -> Collections.nCopies(Glyph.MAX_SLOTS, "")).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    // A glyph in a slot
    public record Socketed(Glyph glyph, int level) {
    }

    private Glyphs() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Glyphs::registerPayloads);
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                for (Glyph glyph : Glyph.values()) {
                    event.accept(stack(glyph, 1));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(Glyphs::onDamagePre);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sync(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(Glyphs::registerCommands);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(GlyphActionPayload.TYPE, GlyphActionPayload.STREAM_CODEC, Glyphs::handleAction)
                // The lambda keeps ClientGlyphs (client only) from being loaded on a dedicated server
                .playToClient(GlyphsPayload.TYPE, GlyphsPayload.STREAM_CODEC, (payload, context) -> ClientGlyphs.set(payload));
    }

    // "/morkeriget glyph 12" gives one of every glyph at that level, for testing. Needs cheats
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("glyph")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, Glyph.MAX_LEVEL)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    int level = IntegerArgumentType.getInteger(context, "level");
                    for (Glyph glyph : Glyph.values()) {
                        player.getInventory().placeItemBackInInventory(stack(glyph, level));
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Du fik alle glyffer på level " + level), false);
                    return 1;
                }))));
    }

    public static ItemStack stack(Glyph glyph, int level) {
        ItemStack stack = new ItemStack(ITEM.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(KIND_TAG, glyph.name());
            tag.putInt(LEVEL_TAG, Math.max(1, Math.min(Glyph.MAX_LEVEL, level)));
        });
        return stack;
    }

    // A random kind at level 1, as a dungeon boss drops it
    public static ItemStack random(RandomSource random) {
        return stack(Glyph.values()[random.nextInt(Glyph.values().length)], 1);
    }

    // The glyph an item is, or null
    public static Socketed of(ItemStack stack) {
        if (!stack.is(ITEM.get())) {
            return null;
        }
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Glyph glyph = Glyph.byName(tag.getString(KIND_TAG));
        return glyph != null ? new Socketed(glyph, Math.max(1, tag.getInt(LEVEL_TAG))) : null;
    }

    // One entry per slot the player has, null for a free one
    public static List<Socketed> socketed(ServerPlayer player) {
        List<String> saved = player.getData(SOCKETED);
        List<Socketed> glyphs = new ArrayList<>();
        for (int slot = 0; slot < Glyph.slots(Progression.paragonLevel(player)); slot++) {
            glyphs.add(slot < saved.size() ? parse(saved.get(slot)) : null);
        }
        return glyphs;
    }

    static Socketed parse(String saved) {
        String[] parts = saved.split(":");
        Glyph glyph = Glyph.byName(parts[0]);
        if (glyph == null || parts.length < 2) {
            return null;
        }
        try {
            return new Socketed(glyph, Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void save(ServerPlayer player, List<Socketed> glyphs) {
        List<String> saved = new ArrayList<>(player.getData(SOCKETED));
        while (saved.size() < Glyph.MAX_SLOTS) {
            saved.add("");
        }
        for (int slot = 0; slot < glyphs.size(); slot++) {
            Socketed socketed = glyphs.get(slot);
            saved.set(slot, socketed != null ? socketed.glyph.name() + ":" + socketed.level : "");
        }
        player.setData(SOCKETED, List.copyOf(saved));
        sync(player);
    }

    public static int level(ServerPlayer player, Glyph glyph) {
        for (Socketed socketed : socketed(player)) {
            if (socketed != null && socketed.glyph == glyph) {
                return socketed.level;
            }
        }
        return 0;
    }

    // Puts the glyph in the stack into a free slot. One of each kind: a better one takes the place of the one socketed,
    // which goes back to the inventory. Returns whether the stack was used
    static boolean socket(ServerPlayer player, ItemStack stack) {
        Socketed glyph = of(stack);
        if (glyph == null) {
            return false;
        }
        List<Socketed> glyphs = socketed(player);
        if (glyphs.isEmpty()) {
            player.displayClientMessage(Component.literal("Glyffer kræver paragon - nå level 100 først").withStyle(ChatFormatting.RED), true);
            return false;
        }
        int slot = -1;
        for (int i = 0; i < glyphs.size(); i++) {
            Socketed socketed = glyphs.get(i);
            if (socketed != null && socketed.glyph == glyph.glyph) {
                if (socketed.level >= glyph.level) {
                    player.displayClientMessage(Component.literal("Du har allerede " + glyph.glyph.displayName + " på level " + socketed.level), true);
                    return false;
                }
                player.getInventory().placeItemBackInInventory(stack(socketed.glyph, socketed.level));
                slot = i;
                break;
            }
            if (socketed == null && slot < 0) {
                slot = i;
            }
        }
        if (slot < 0) {
            player.displayClientMessage(Component.literal("Ingen ledig glyf-plads - flere ved paragon 10, 25 og 50").withStyle(ChatFormatting.RED), true);
            return false;
        }
        glyphs.set(slot, glyph);
        save(player, glyphs);
        player.displayClientMessage(Component.literal(glyph.glyph.displayName + " (level " + glyph.level + ") sidder nu på paragon-tavlen")
                .withStyle(glyph.glyph.color), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    // Takes the glyph in the slot out and gives it back as an item
    static void remove(ServerPlayer player, int slot) {
        List<Socketed> glyphs = socketed(player);
        if (slot < 0 || slot >= glyphs.size() || glyphs.get(slot) == null) {
            return;
        }
        Socketed socketed = glyphs.get(slot);
        glyphs.set(slot, null);
        save(player, glyphs);
        player.getInventory().placeItemBackInInventory(stack(socketed.glyph, socketed.level));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // Called when a dungeon of this tier is cleared: every socketed glyph goes up a level, up to twice the tier
    public static void dungeonCleared(ServerPlayer player, int tier) {
        List<Socketed> glyphs = socketed(player);
        int cap = Math.min(Glyph.MAX_LEVEL, tier * LEVELS_PER_TIER);
        boolean changed = false;
        for (int slot = 0; slot < glyphs.size(); slot++) {
            Socketed socketed = glyphs.get(slot);
            if (socketed != null && socketed.level < cap) {
                Socketed raised = new Socketed(socketed.glyph, socketed.level + 1);
                glyphs.set(slot, raised);
                changed = true;
                player.sendSystemMessage(Component.literal("Glyf: " + raised.glyph.displayName + " er nu level " + raised.level + " - ")
                        .withStyle(raised.glyph.color).append(Component.literal(raised.glyph.describe(raised.level)).withStyle(ChatFormatting.GRAY)));
            }
        }
        if (changed) {
            save(player, glyphs);
        }
    }

    // The glyphs' bonuses on damage the player or their minions do, and Bulwark on damage the player takes
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        float scale = 1;
        if (target instanceof ServerPlayer victim) {
            scale *= (float) (1 - Glyph.BULWARK.bonus(level(victim, Glyph.BULWARK)));
        }
        Entity attacker = event.getSource().getEntity();
        ServerPlayer player = attacker instanceof ServerPlayer direct ? direct : null;
        boolean minion = false;
        if (player == null && attacker != null) {
            player = Minions.isAlly(attacker) ? Minions.owner(level, attacker)
                    : attacker instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner ? owner : null;
            minion = player != null;
        }
        if (player != null && player != target) {
            double bonus = Glyph.WRATH.bonus(level(player, Glyph.WRATH));
            if (minion) {
                bonus += Glyph.LEGION.bonus(level(player, Glyph.LEGION));
            }
            if (target.isOnFire()) {
                bonus += Glyph.FLAME.bonus(level(player, Glyph.FLAME));
            }
            if (Bosses.isBoss(target) || Elites.isElite(target)) {
                bonus += Glyph.DOMINION.bonus(level(player, Glyph.DOMINION));
            }
            if (target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                bonus += Glyph.FROST.bonus(level(player, Glyph.FROST));
            }
            scale *= (float) (1 + bonus);
        }
        if (scale != 1) {
            event.setNewDamage(event.getNewDamage() * scale);
        }
    }

    private static void handleAction(GlyphActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            remove(player, payload.slot());
        }
    }

    static void sync(ServerPlayer player) {
        List<String> shown = new ArrayList<>();
        for (Socketed socketed : socketed(player)) {
            shown.add(socketed != null ? socketed.glyph.name() + ":" + socketed.level : "");
        }
        PacketDistributor.sendToPlayer(player, new GlyphsPayload(shown));
    }
}
