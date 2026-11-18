package com.morkeriget.guide;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.Text;
import com.morkeriget.feedback.Feedback;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.skill.Minions;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// A short guide for new players: one step at a time in a box in the top left (see GuideHud), from picking a class to
// salvaging in the smithy. Each step gives a little essence, and the last one a rare item. Then the feedback window
// comes, since the player now knows enough of the mod to say something about it. Other parts of the mod call done()
// when something happens. Saved on the player and kept on death
public final class Guide {
    // The step the player is on (a GuideStep ordinal), DONE when the guide is over, and NOT_STARTED before the first join
    public static final int DONE = GuideStep.values().length;
    private static final int NOT_STARTED = -1;
    private static final int ESSENCE_PER_STEP = 5;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<Integer>> STEP = ATTACHMENTS.register("guide_step",
            () -> AttachmentType.builder(() -> NOT_STARTED).serialize(Codec.INT).copyOnDeath().build());
    // How many times the current step has happened, e.g. kills
    private static final Supplier<AttachmentType<Integer>> COUNT = ATTACHMENTS.register("guide_count",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    // Set when the rewards have been given, so starting the guide again doesn't give them twice
    private static final Supplier<AttachmentType<Boolean>> REWARDED = ATTACHMENTS.register("guide_rewarded",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private Guide() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Guide::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Guide::onLogin);
        NeoForge.EVENT_BUS.addListener(Guide::onDeath);
        NeoForge.EVENT_BUS.addListener(Guide::onPickup);
        NeoForge.EVENT_BUS.addListener(Guide::registerCommands);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The lambda keeps GuideHud (client only) from being loaded on a dedicated server
        event.registrar("1").playToClient(GuidePayload.TYPE, GuidePayload.STREAM_CODEC, (payload, context) -> GuideHud.set(payload));
    }

    // "/morkeriget guide skip" ends the guide, and "/morkeriget guide restart" starts it again. Anyone can use them
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("guide")
                .then(Commands.literal("skip").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (step(player) < DONE) {
                        setStep(player, DONE);
                        player.sendSystemMessage(Text.tr("guide.skipped").withStyle(ChatFormatting.GRAY));
                        Feedback.offer(player);
                    }
                    return 1;
                }))
                .then(Commands.literal("restart").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    setStep(player, 0);
                    skipFinished(player);
                    return 1;
                }))));
    }

    // Players from before the guide, who have already started playing, don't get it. New players do, unless it is
    // turned off in the config. The feedback window waits until the guide is over
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (step(player) == NOT_STARTED) {
            boolean started = Progression.playerClass(player) != null || Progression.level(player) > 1;
            if (started || !Config.GUIDE.get()) {
                setStep(player, DONE);
            } else {
                setStep(player, 0);
                player.sendSystemMessage(Text.tr("guide.welcome").withStyle(ChatFormatting.GOLD)
                        .append(Text.tr("guide.skip_button").withStyle(style -> style.withColor(ChatFormatting.GRAY).withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + Morkeriget.MODID + " guide skip"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.tr("guide.skip_hover"))))));
            }
        }
        sync(player);
        if (step(player) >= DONE) {
            Feedback.offer(player);
        }
    }

    public static int step(ServerPlayer player) {
        return player.getData(STEP);
    }

    public static int count(ServerPlayer player) {
        return player.getData(COUNT);
    }

    private static void setStep(ServerPlayer player, int step) {
        player.setData(STEP, step);
        player.setData(COUNT, 0);
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GuidePayload(step(player), count(player)));
    }

    // Something happened that a step asks for. It only counts when the player is on that step
    public static void done(ServerPlayer player, GuideStep step) {
        if (step(player) != step.ordinal()) {
            return;
        }
        int count = count(player) + 1;
        if (count < step.goal) {
            player.setData(COUNT, count);
            sync(player);
            return;
        }
        boolean rewards = !player.getData(REWARDED);
        MutableComponent message = Text.tr("guide.step_done", step.ordinal() + 1, DONE);
        if (rewards) {
            message.append(Text.tr("guide.essence", ESSENCE_PER_STEP));
            give(player, Smithy.essence(ESSENCE_PER_STEP));
        }
        player.sendSystemMessage(message.withStyle(ChatFormatting.GREEN));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
        setStep(player, step.ordinal() + 1);
        skipFinished(player);
        if (step(player) >= DONE) {
            finish(player);
        }
    }

    // The steps that are about how things are, not about something happening, count at once if they are already true,
    // e.g. a class picked before the guide asked for it
    private static void skipFinished(ServerPlayer player) {
        int step = step(player);
        if (step == GuideStep.CHOOSE_CLASS.ordinal() && Progression.playerClass(player) != null) {
            done(player, GuideStep.CHOOSE_CLASS);
        } else if (step == GuideStep.PICK_SKILL.ordinal() && Progression.slot(player, 0) != null) {
            done(player, GuideStep.PICK_SKILL);
        }
    }

    private static void finish(ServerPlayer player) {
        if (!player.getData(REWARDED)) {
            player.setData(REWARDED, true);
            give(player, List.of(Loot.create(player.getRandom(), Rarity.RARE, Progression.level(player), Progression.playerClass(player))));
            player.sendSystemMessage(Text.tr("guide.finished_reward").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            player.sendSystemMessage(Text.tr("guide.finished").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        player.sendSystemMessage(Text.tr("guide.next", Component.keybind("key.morkeriget.skills"))
                .withStyle(ChatFormatting.YELLOW));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
        Feedback.offer(player);
    }

    private static void give(ServerPlayer player, List<ItemStack> items) {
        for (ItemStack item : items) {
            player.getInventory().placeItemBackInInventory(item);
        }
    }

    // The same kills that give XP: monsters, not your own minions, with the kill credit so burning counts too
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity monster = event.getEntity();
        if (monster instanceof Enemy && !Minions.isAlly(monster) && monster.getKillCredit() instanceof ServerPlayer player) {
            done(player, GuideStep.KILL);
        }
    }

    // Loot normally goes right into the loot bag, which calls found(). When the bag is full it goes into the inventory
    // instead, and is counted here if it was really picked up (not walked over with a full inventory)
    private static void onPickup(ItemEntityPickupEvent.Post event) {
        ItemStack original = event.getOriginalStack();
        if (event.getPlayer() instanceof ServerPlayer player && event.getCurrentStack().getCount() < original.getCount()) {
            found(player, original);
        }
    }

    // Magic or better loot that was picked up
    public static void found(ServerPlayer player, ItemStack stack) {
        Rarity rarity = Loot.getRarity(stack);
        if (rarity != null && rarity != Rarity.COMMON) {
            done(player, GuideStep.LOOT);
        }
    }
}
