package com.morkeriget.journey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.morkeriget.Morkeriget;
import com.morkeriget.Text;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.skill.Minions;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The season journey: goals in four chapters that give rewards when they are reached, and a mythic item for reaching
// all of them. Other parts of the mod call add() and riftClosed() when something happens. Saved on the player and kept on death
public final class Journey {
    // Set on legendaries the first time someone picks them up, so dropping and picking one up again doesn't count twice
    private static final String FOUND_TAG = "morkeriget_found";

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    // The counted stats by name
    private static final Supplier<AttachmentType<Map<String, Integer>>> STATS = ATTACHMENTS.register("journey_stats",
            () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().build());
    // The achievements that have given their reward, by name. "ALL" when the reward for all of them is given
    private static final Supplier<AttachmentType<List<String>>> REWARDED = ATTACHMENTS.register("journey_rewarded",
            () -> AttachmentType.builder(() -> List.<String>of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());
    private static final String ALL = "ALL";

    private Journey() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Journey::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Journey::onDeath);
        NeoForge.EVENT_BUS.addListener(Journey::onPickup);
        NeoForge.EVENT_BUS.addListener(Journey::onPlayerTick);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(JourneyPayload.Open.TYPE, JourneyPayload.Open.STREAM_CODEC, Journey::handleOpen)
                // The lambda keeps JourneyScreen (client only) from being loaded on a dedicated server
                .playToClient(JourneyPayload.TYPE, JourneyPayload.STREAM_CODEC, (payload, context) -> JourneyScreen.receive(payload));
    }

    private static void handleOpen(JourneyPayload.Open payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            sync(player, true);
        }
    }

    private static void sync(ServerPlayer player, boolean open) {
        List<Integer> stats = new ArrayList<>();
        for (Achievement.Stat stat : Achievement.Stat.values()) {
            stats.add(value(player, stat));
        }
        PacketDistributor.sendToPlayer(player, new JourneyPayload(stats, open));
    }

    private static int value(ServerPlayer player, Achievement.Stat stat) {
        if (stat == Achievement.Stat.LEVEL) {
            return Progression.level(player);
        }
        return player.getData(STATS).getOrDefault(stat.name(), 0);
    }

    public static void add(ServerPlayer player, Achievement.Stat stat) {
        set(player, stat, value(player, stat) + 1);
    }

    // A rift counts, and so does its tier if it is the highest so far
    public static void riftClosed(ServerPlayer player, int tier) {
        add(player, Achievement.Stat.RIFTS);
        if (tier > value(player, Achievement.Stat.RIFT_TIER)) {
            set(player, Achievement.Stat.RIFT_TIER, tier);
        }
    }

    private static void set(ServerPlayer player, Achievement.Stat stat, int value) {
        Map<String, Integer> stats = new HashMap<>(player.getData(STATS));
        stats.put(stat.name(), value);
        player.setData(STATS, Map.copyOf(stats));
        check(player);
    }

    // The same kills that give XP: monsters, not your own minions, with the kill credit so burning counts too
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity monster = event.getEntity();
        if (!(monster instanceof Enemy) || Minions.isAlly(monster) || !(monster.getKillCredit() instanceof ServerPlayer player)) {
            return;
        }
        add(player, Achievement.Stat.KILLS);
        if (Bosses.isBoss(monster)) {
            add(player, Achievement.Stat.BOSSES);
        } else if (Elites.isElite(monster)) {
            add(player, Achievement.Stat.ELITES);
        }
    }

    // Legendaries, set items and mythics count when someone really picks them up, after it happened, so walking over
    // one with a full inventory doesn't count. Ones given right into the inventory are counted with found()
    private static void onPickup(ItemEntityPickupEvent.Post event) {
        ItemStack original = event.getOriginalStack();
        Achievement.Stat stat = statFor(original);
        if (!(event.getPlayer() instanceof ServerPlayer player) || stat == null || isFound(original)
                || event.getCurrentStack().getCount() >= original.getCount()) {
            return;
        }
        // The stack on the ground is copied into the inventory, so the mark goes on the one there. Loot like this has
        // its own random bonuses, so the same item and components means the same piece
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, original)) {
                markFound(stack);
                break;
            }
        }
        add(player, stat);
    }

    // For loot given right into the inventory, e.g. by the world boss. Marks it, so picking it up later doesn't count
    public static void found(ServerPlayer player, ItemStack stack) {
        Achievement.Stat stat = statFor(stack);
        if (stat != null && !isFound(stack)) {
            markFound(stack);
            add(player, stat);
        }
    }

    // A rerolled item is a new stack, but the same piece, so it keeps the mark
    public static void copyFound(ItemStack from, ItemStack to) {
        if (isFound(from)) {
            markFound(to);
        }
    }

    // What finding the item counts towards, or null
    private static Achievement.Stat statFor(ItemStack stack) {
        Rarity rarity = Loot.getRarity(stack);
        if (rarity == Rarity.LEGENDARY || rarity == Rarity.SET) {
            return Achievement.Stat.LEGENDARIES;
        }
        return rarity == Rarity.MYTHIC ? Achievement.Stat.MYTHICS : null;
    }

    private static boolean isFound(ItemStack stack) {
        return Loot.affix(stack, FOUND_TAG) > 0;
    }

    private static void markFound(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(FOUND_TAG, 1));
    }

    // The level isn't counted, so it is checked every few seconds
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 0) {
            check(player);
        }
    }

    // Gives the reward for every achievement that is reached but hasn't given it yet. What is rewarded is saved before
    // anything is given, since the mythic for the whole journey counts as a stat itself and comes back in here
    private static void check(ServerPlayer player) {
        List<String> rewarded = new ArrayList<>(player.getData(REWARDED));
        List<Achievement> reached = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            if (!rewarded.contains(achievement.name()) && value(player, achievement.stat) >= achievement.goal) {
                reached.add(achievement);
                rewarded.add(achievement.name());
            }
        }
        boolean all = !rewarded.contains(ALL) && rewarded.size() >= Achievement.values().length;
        if (all) {
            rewarded.add(ALL);
        }
        if (reached.isEmpty() && !all) {
            return;
        }
        player.setData(REWARDED, List.copyOf(rewarded));
        for (Achievement achievement : reached) {
            give(player, reward(player, achievement.chapter));
            player.sendSystemMessage(Text.tr("journey.reached", achievement.text()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Text.tr("journey.reward_given").withStyle(ChatFormatting.YELLOW)));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        if (all) {
            ItemStack mythic = Loot.createMythic(player.getRandom(), Progression.level(player), Progression.playerClass(player));
            give(player, List.of(mythic));
            player.sendSystemMessage(Text.tr("journey.all_done").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            Loot.announceMythic(player, mythic);
        }
        sync(player, false);
    }

    // See Achievement.chapterReward
    private static List<ItemStack> reward(ServerPlayer player, int chapter) {
        RandomSource random = player.getRandom();
        int level = Progression.level(player);
        List<ItemStack> rewards = new ArrayList<>();
        rewards.addAll(Smithy.essence(switch (chapter) {
            case 1 -> 15;
            case 2 -> 30;
            case 3 -> 60;
            default -> 100;
        }));
        rewards.add(Loot.create(random, chapter == 1 ? Rarity.RARE : Rarity.LEGENDARY, level, Progression.playerClass(player)));
        if (chapter >= 3) {
            rewards.add(Gems.random(random, level, chapter - 2));
        }
        return rewards;
    }

    private static void give(ServerPlayer player, List<ItemStack> rewards) {
        for (ItemStack reward : rewards) {
            // The journey's own rewards never count towards it, also not if they drop because the inventory is full
            // (only items that count, so the essence still stacks with other essence)
            if (statFor(reward) != null) {
                markFound(reward);
            }
            player.getInventory().placeItemBackInInventory(reward);
        }
    }
}
