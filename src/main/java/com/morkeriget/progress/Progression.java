package com.morkeriget.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Levels: killing monsters gives XP, every level gives a skill point, and points are spent on upgrades
public final class Progression {
    public static final int MAX_LEVEL = 100;
    private static final ResourceLocation VITALITY_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "vitality");
    private static final ResourceLocation STRENGTH_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "strength");

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<PlayerProgress>> PROGRESS = ATTACHMENTS.register("progress",
            () -> AttachmentType.builder(() -> PlayerProgress.START).serialize(PlayerProgress.CODEC).copyOnDeath().build());

    private Progression() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Progression::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Progression::onDeath);
        NeoForge.EVENT_BUS.addListener(Progression::onLogin);
        NeoForge.EVENT_BUS.addListener(Progression::onRespawn);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(UpgradePayload.TYPE, UpgradePayload.STREAM_CODEC, Progression::handleUpgrade)
                // The lambda keeps ClientProgress (client only) from being loaded on a dedicated server
                .playToClient(ProgressPayload.TYPE, ProgressPayload.STREAM_CODEC, (payload, context) -> ClientProgress.set(payload));
    }

    public static int level(ServerPlayer player) {
        return player.getData(PROGRESS).level();
    }

    public static int rank(ServerPlayer player, Upgrade upgrade) {
        return player.getData(PROGRESS).rank(upgrade);
    }

    // XP depends on how much health the monster has, so elites and bosses already give more. They get a bonus on top
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity monster = event.getEntity();
        // Kill credit instead of the damage source, so a monster that burns to death after your hit still counts
        if (!(monster instanceof Enemy) || !(monster.getKillCredit() instanceof ServerPlayer player)) {
            return;
        }
        int bonus = Bosses.isBoss(monster) ? 5 : Elites.isElite(monster) ? 2 : 1;
        int xp = (int) Math.max(1, Math.round(monster.getMaxHealth() / 2 * bonus * Config.XP_MULTIPLIER.get()));
        addXp(player, xp);
        if (bonus > 1) {
            player.displayClientMessage(Component.literal("+" + xp + " XP").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    private static void addXp(ServerPlayer player, int amount) {
        PlayerProgress progress = player.getData(PROGRESS);
        if (progress.level() >= MAX_LEVEL) {
            return;
        }
        int level = progress.level();
        int xp = progress.xp() + amount;
        int points = progress.points();
        while (level < MAX_LEVEL && xp >= PlayerProgress.xpForNextLevel(level)) {
            xp -= PlayerProgress.xpForNextLevel(level);
            level++;
            points++;
        }
        if (level >= MAX_LEVEL) {
            xp = 0;
        }
        player.setData(PROGRESS, new PlayerProgress(level, xp, points, progress.ranks()));
        if (level > progress.level()) {
            player.sendSystemMessage(Component.literal("Level " + level + "! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Du har " + points + " skill-point" + (points == 1 ? "" : "s") + " - tryk K")
                            .withStyle(ChatFormatting.YELLOW)));
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
            player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                    40, 0.4, 0.8, 0.4, 0.3);
        }
        sync(player);
    }

    private static void handleUpgrade(UpgradePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        PlayerProgress progress = player.getData(PROGRESS);
        if (progress.points() <= 0 || progress.rank(payload.upgrade()) >= Upgrade.MAX_RANK) {
            return;
        }
        player.setData(PROGRESS, progress.withUpgrade(payload.upgrade()));
        applyPassives(player);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        sync(player);
    }

    // The extra health and damage aren't saved with the player, so they are put on again when they log in or respawn
    private static void applyPassives(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            int rank = rank(player, Upgrade.VITALITY);
            health.addOrUpdateTransientModifier(new AttributeModifier(VITALITY_ID, rank * 2, AttributeModifier.Operation.ADD_VALUE));
        }
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            int rank = rank(player, Upgrade.STRENGTH);
            damage.addOrUpdateTransientModifier(new AttributeModifier(STRENGTH_ID, rank * 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyPassives(player);
            sync(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyPassives(player);
            player.setHealth(player.getMaxHealth());
            sync(player);
        }
    }

    private static void sync(ServerPlayer player) {
        PlayerProgress progress = player.getData(PROGRESS);
        List<Integer> ranks = new ArrayList<>();
        for (Upgrade upgrade : Upgrade.values()) {
            ranks.add(progress.rank(upgrade));
        }
        PacketDistributor.sendToPlayer(player, new ProgressPayload(progress.level(), progress.xp(),
                PlayerProgress.xpForNextLevel(progress.level()), progress.points(), ranks));
    }
}
