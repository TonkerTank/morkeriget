package com.morkeriget.bounty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.morkeriget.Morkeriget;
import com.morkeriget.journey.Achievement;
import com.morkeriget.journey.Journey;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.skill.Minions;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The bounty board, opened from the skill screen: three bounties a day, like killing elites or closing rifts. Each one
// gives essence, a good item and XP, and finishing all three gives a legendary on top. New bounties come at midnight
public final class Bounties {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<BountyBoard>> BOARD = ATTACHMENTS.register("bounties",
            () -> AttachmentType.builder(() -> BountyBoard.EMPTY).serialize(BountyBoard.CODEC).copyOnDeath().build());

    private Bounties() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Bounties::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Bounties::onDeath);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(BountyActionPayload.TYPE, BountyActionPayload.STREAM_CODEC, Bounties::handleAction)
                // The lambda keeps BountyScreen (client only) from being loaded on a dedicated server
                .playToClient(BountiesPayload.TYPE, BountiesPayload.STREAM_CODEC, (payload, context) -> BountyScreen.receive(payload));
    }

    // Today's board. A board from another day is swapped for new bounties
    private static BountyBoard board(ServerPlayer player) {
        String today = LocalDate.now().toString();
        BountyBoard board = player.getData(BOARD);
        if (!board.day().equals(today)) {
            board = BountyBoard.roll(today, player.getRandom());
            player.setData(BOARD, board);
        }
        return board;
    }

    private static void sync(ServerPlayer player, boolean open) {
        BountyBoard board = board(player);
        PacketDistributor.sendToPlayer(player, new BountiesPayload(board.bounties(), board.bonusClaimed(), open));
    }

    // Called by the rift when its guardian is killed
    public static void riftClosed(ServerPlayer player) {
        progress(player, BountyType.RIFTS);
    }

    // The same kills that give XP: monsters, not your own minions, with the kill credit so burning counts too.
    // An elite or boss also counts as a monster
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity monster = event.getEntity();
        if (!(monster instanceof Enemy) || Minions.isAlly(monster) || !(monster.getKillCredit() instanceof ServerPlayer player)) {
            return;
        }
        progress(player, BountyType.MONSTERS);
        if (Bosses.isBoss(monster)) {
            progress(player, BountyType.BOSSES);
        } else if (Elites.isElite(monster)) {
            progress(player, BountyType.ELITES);
        }
    }

    private static void progress(ServerPlayer player, BountyType type) {
        BountyBoard board = board(player);
        List<Bounty> bounties = board.bounties();
        for (int i = 0; i < bounties.size(); i++) {
            Bounty bounty = bounties.get(i);
            if (bounty.type() != type || bounty.done()) {
                continue;
            }
            Bounty updated = bounty.withProgress(1);
            board = board.with(i, updated);
            if (updated.done()) {
                player.sendSystemMessage(Component.literal("Opgave klaret: " + type.text(updated.goal()) + "! ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .append(Component.literal("Hent belønningen i Opgaver (K)").withStyle(ChatFormatting.YELLOW)));
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
            }
        }
        player.setData(BOARD, board);
        sync(player, false);
    }

    private static void handleAction(BountyActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        int action = payload.action();
        BountyBoard board = board(player);
        if (action == BountyActionPayload.BONUS) {
            if (board.allClaimed() && !board.bonusClaimed()) {
                player.setData(BOARD, board.withBonusClaimed());
                give(player, bonusReward(player));
                player.sendSystemMessage(Component.literal("Alle dagens opgaver er klaret! Du fik en legendarisk genstand, essens og en ædelsten.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
                Journey.add(player, Achievement.Stat.BOUNTY_DAYS);
            }
        } else if (action >= 0 && action < board.bounties().size()) {
            Bounty bounty = board.bounties().get(action);
            if (bounty.done() && !bounty.claimed()) {
                player.setData(BOARD, board.with(action, bounty.withClaimed()));
                give(player, reward(player));
                Progression.addXp(player, 100 + 20 * Progression.level(player));
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
            }
        }
        sync(player, action == BountyActionPayload.OPEN);
    }

    // Essence and an item that is at least rare, at the player's level
    private static List<ItemStack> reward(ServerPlayer player) {
        RandomSource random = player.getRandom();
        int level = Progression.level(player);
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(new ItemStack(Smithy.ESSENCE.get(), 8 + random.nextInt(5)));
        rewards.add(Loot.create(random, Rarity.roll(random, Rarity.RARE), level, Progression.playerClass(player)));
        return rewards;
    }

    // A legendary for the player's class, essence and a gem
    private static List<ItemStack> bonusReward(ServerPlayer player) {
        RandomSource random = player.getRandom();
        int level = Progression.level(player);
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(Loot.create(random, Rarity.LEGENDARY, level, Progression.playerClass(player)));
        rewards.add(new ItemStack(Smithy.ESSENCE.get(), 20 + random.nextInt(11)));
        rewards.add(Gems.random(random, level, 0));
        return rewards;
    }

    // Straight into the inventory, or dropped at the player's feet if it is full
    private static void give(ServerPlayer player, List<ItemStack> rewards) {
        for (ItemStack reward : rewards) {
            player.getInventory().placeItemBackInInventory(reward);
        }
    }
}
