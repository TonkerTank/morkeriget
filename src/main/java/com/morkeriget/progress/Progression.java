package com.morkeriget.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.skill.Minions;
import com.morkeriget.skill.Skill;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
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
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
    public static final int PARAGON_PER_MONSTER_LEVEL = 10;
    private static final ResourceLocation VITALITY_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "vitality");
    private static final ResourceLocation STRENGTH_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "strength");
    private static final ResourceLocation BARBARIAN_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "class_barbarian");
    private static final ResourceLocation RANGER_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "class_ranger");
    private static final ResourceLocation AGILE_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "passive_agile");
    private static final ResourceLocation GLASS_CANNON_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "passive_glass_cannon");
    private static final ResourceLocation PARAGON_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "paragon_damage");
    private static final ResourceLocation PARAGON_HEALTH_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "paragon_health");
    private static final ResourceLocation PARAGON_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "paragon_speed");

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
        NeoForge.EVENT_BUS.addListener(Progression::registerCommands);
    }

    // "/morkeriget level 60" jumps to that level with all its skill points, for testing. Points already spent stay spent.
    // Needs cheats
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("level")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, MAX_LEVEL)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    int level = IntegerArgumentType.getInteger(context, "level");
                    PlayerProgress progress = player.getData(PROGRESS);
                    int spent = progress.ranks().values().stream().mapToInt(Integer::intValue).sum();
                    player.setData(PROGRESS, progress.withLevel(level, 0, Math.max(0, PlayerProgress.pointsForLevel(level) - spent)));
                    sync(player);
                    context.getSource().sendSuccess(() -> Component.literal("Du er nu level " + level), false);
                    return 1;
                }))));
        // "/morkeriget paragon 20" gives 20 paragon levels with their points. Needs level 100
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("paragon")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("levels", IntegerArgumentType.integer(1, 1000)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    int levels = IntegerArgumentType.getInteger(context, "levels");
                    PlayerProgress progress = player.getData(PROGRESS);
                    if (progress.level() < MAX_LEVEL) {
                        context.getSource().sendFailure(Component.literal("Paragon starter ved level " + MAX_LEVEL + " - brug /morkeriget level " + MAX_LEVEL));
                        return 0;
                    }
                    player.setData(PROGRESS, progress.withParagonLevel(progress.paragonLevel() + levels, 0, progress.paragonPoints() + levels));
                    sync(player);
                    context.getSource().sendSuccess(() -> Component.literal("Du er nu paragon " + (progress.paragonLevel() + levels)), false);
                    return 1;
                }))));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(UpgradePayload.TYPE, UpgradePayload.STREAM_CODEC, Progression::handleUpgrade)
                .playToServer(ChooseClassPayload.TYPE, ChooseClassPayload.STREAM_CODEC, Progression::handleChooseClass)
                .playToServer(AssignSlotPayload.TYPE, AssignSlotPayload.STREAM_CODEC, Progression::handleAssignSlot)
                .playToServer(ParagonPayload.TYPE, ParagonPayload.STREAM_CODEC, Progression::handleParagon)
                // The lambda keeps ClientProgress (client only) from being loaded on a dedicated server
                .playToClient(ProgressPayload.TYPE, ProgressPayload.STREAM_CODEC, (payload, context) -> ClientProgress.set(payload));
    }

    public static int level(ServerPlayer player) {
        return player.getData(PROGRESS).level();
    }

    // The level monsters get around this player: their own level, plus one for every PARAGON_PER_MONSTER_LEVEL paragon
    // levels, so the game doesn't get easy once paragon makes you stronger and stronger
    public static int monsterLevel(ServerPlayer player) {
        PlayerProgress progress = player.getData(PROGRESS);
        return progress.level() + progress.paragonLevel() / PARAGON_PER_MONSTER_LEVEL;
    }

    public static int rank(ServerPlayer player, Upgrade upgrade) {
        return player.getData(PROGRESS).rank(upgrade);
    }

    // null until the player has picked a class
    public static PlayerClass playerClass(ServerPlayer player) {
        return PlayerClass.byName(player.getData(PROGRESS).playerClass());
    }

    // Whether the skill is in the player's tree and has at least one point. The potions are for everyone
    public static boolean hasUnlocked(ServerPlayer player, Skill skill) {
        Upgrade upgrade = Upgrade.forSkill(skill);
        return upgrade == null || (upgrade.playerClass == playerClass(player) && rank(player, upgrade) > 0);
    }

    // The skill on the key (0 to 3), or null
    public static Skill slot(ServerPlayer player, int index) {
        return player.getData(PROGRESS).slot(index);
    }

    // XP depends on how much health the monster has, so elites and bosses already give more. They get a bonus on top
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity monster = event.getEntity();
        // Kill credit instead of the damage source, so a monster that burns to death after your hit still counts
        if (!(monster instanceof Enemy) || Minions.isAlly(monster) || !(monster.getKillCredit() instanceof ServerPlayer player)) {
            return;
        }
        int bonus = Bosses.isBoss(monster) ? 5 : Elites.isElite(monster) ? 2 : 1;
        // Each paragon Wisdom point adds 1%
        double wisdom = 1 + 0.01 * rank(player, Paragon.WISDOM);
        int xp = (int) Math.max(1, Math.round(monster.getMaxHealth() / 2 * bonus * wisdom * Config.XP_MULTIPLIER.get()));
        addXp(player, xp);
        if (bonus > 1) {
            player.displayClientMessage(Component.literal("+" + xp + " XP").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    public static void addXp(ServerPlayer player, int amount) {
        PlayerProgress progress = player.getData(PROGRESS);
        if (progress.level() >= MAX_LEVEL) {
            addParagonXp(player, progress, amount);
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
        player.setData(PROGRESS, progress.withLevel(level, xp, points));
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

    // After level 100 XP goes towards paragon levels, which never end
    private static void addParagonXp(ServerPlayer player, PlayerProgress progress, int amount) {
        int paragonLevel = progress.paragonLevel();
        int xp = progress.xp() + amount;
        int points = progress.paragonPoints();
        while (xp >= Paragon.xpForNextLevel(paragonLevel)) {
            xp -= Paragon.xpForNextLevel(paragonLevel);
            paragonLevel++;
            points++;
        }
        player.setData(PROGRESS, progress.withParagonLevel(paragonLevel, xp, points));
        if (paragonLevel > progress.paragonLevel()) {
            player.sendSystemMessage(Component.literal("Paragon " + paragonLevel + "! ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal("Du har " + points + " paragon-point" + (points == 1 ? "" : "s") + " - tryk K")
                            .withStyle(ChatFormatting.YELLOW)));
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(),
                    40, 0.4, 0.8, 0.4, 0.1);
        }
        sync(player);
    }

    public static int rank(ServerPlayer player, Paragon stat) {
        return player.getData(PROGRESS).rank(stat);
    }

    private static void handleParagon(ParagonPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        PlayerProgress progress = player.getData(PROGRESS);
        // Never more than the player has, or than what is left up to the stat's max
        int amount = Math.min(Math.min(payload.amount(), progress.paragonPoints()), payload.stat().maxRank - progress.rank(payload.stat()));
        if (amount <= 0) {
            return;
        }
        player.setData(PROGRESS, progress.withParagon(payload.stat(), amount));
        applyPassives(player);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        sync(player);
    }

    private static void handleUpgrade(UpgradePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        PlayerProgress progress = player.getData(PROGRESS);
        PlayerClass playerClass = playerClass(player);
        if (progress.points() <= 0 || playerClass == null || !SkillTree.canRaise(playerClass, payload.upgrade(), progress::rank)) {
            return;
        }
        player.setData(PROGRESS, progress.withUpgrade(payload.upgrade()));
        applyPassives(player);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        sync(player);
    }

    // Picking a class, or changing to another one, gives back every skill point
    private static void handleChooseClass(ChooseClassPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        PlayerClass playerClass = payload.playerClass();
        boolean sameClass = playerClass == playerClass(player);
        player.setData(PROGRESS, player.getData(PROGRESS).withClass(playerClass));
        applyPassives(player);
        if (sameClass) {
            player.sendSystemMessage(Component.literal("Du har fået alle dine skill-points tilbage").withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.literal("Du er nu " + playerClass.displayName + "! ")
                    .withStyle(style -> style.withColor(playerClass.color).withBold(true))
                    .append(Component.literal("Vælg din første skill i K").withStyle(ChatFormatting.YELLOW)));
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        sync(player);
    }

    // Only skills the player has unlocked can go on a key
    private static void handleAssignSlot(AssignSlotPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload.slot() < 0 || payload.slot() >= SkillTree.SLOTS
                || Upgrade.forSkill(payload.skill()) == null || !hasUnlocked(player, payload.skill())) {
            return;
        }
        player.setData(PROGRESS, player.getData(PROGRESS).withSlot(payload.slot(), payload.skill()));
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        sync(player);
    }

    // The extra health, damage and speed aren't saved with the player, so they are put on again when they log in or respawn
    private static void applyPassives(ServerPlayer player) {
        PlayerClass playerClass = playerClass(player);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            int rank = rank(player, Upgrade.VITALITY);
            health.addOrUpdateTransientModifier(new AttributeModifier(VITALITY_ID, rank * 2, AttributeModifier.Operation.ADD_VALUE));
            // Barbarians have 20% more health
            health.addOrUpdateTransientModifier(new AttributeModifier(BARBARIAN_ID,
                    playerClass == PlayerClass.BARBARIAN ? 0.2 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            health.addOrUpdateTransientModifier(new AttributeModifier(PARAGON_HEALTH_ID,
                    rank(player, Paragon.HEALTH) * 0.01, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            // Rangers are 10% faster
            speed.addOrUpdateTransientModifier(new AttributeModifier(RANGER_ID,
                    playerClass == PlayerClass.RANGER ? 0.1 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            speed.addOrUpdateTransientModifier(new AttributeModifier(AGILE_ID,
                    rank(player, Upgrade.AGILE) * 0.03, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            speed.addOrUpdateTransientModifier(new AttributeModifier(PARAGON_SPEED_ID,
                    rank(player, Paragon.SPEED) * 0.005, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            int rank = rank(player, Upgrade.STRENGTH);
            damage.addOrUpdateTransientModifier(new AttributeModifier(STRENGTH_ID, rank * 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            damage.addOrUpdateTransientModifier(new AttributeModifier(GLASS_CANNON_ID,
                    rank(player, Upgrade.GLASS_CANNON) * 0.06, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            damage.addOrUpdateTransientModifier(new AttributeModifier(PARAGON_DAMAGE_ID,
                    rank(player, Paragon.DAMAGE) * 0.01, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getData(PROGRESS).isFromBeforeSkillTree()) {
                player.setData(PROGRESS, player.getData(PROGRESS).respec());
                if (playerClass(player) != null) {
                    player.sendSystemMessage(Component.literal("Skills har fået et skill tree! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("Du har fået alle dine skill-points tilbage - tryk K").withStyle(ChatFormatting.YELLOW)));
                }
            }
            applyPassives(player);
            sync(player);
            if (playerClass(player) == null) {
                player.sendSystemMessage(Component.literal("Vælg en klasse: tryk K").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
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
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < SkillTree.SLOTS; i++) {
            Skill skill = progress.slot(i);
            slots.add(skill != null ? skill.ordinal() : -1);
        }
        List<Integer> paragon = new ArrayList<>();
        for (Paragon stat : Paragon.values()) {
            paragon.add(progress.rank(stat));
        }
        int xpNeeded = progress.level() >= MAX_LEVEL ? Paragon.xpForNextLevel(progress.paragonLevel()) : PlayerProgress.xpForNextLevel(progress.level());
        PacketDistributor.sendToPlayer(player, new ProgressPayload(progress.level(), progress.xp(), xpNeeded, progress.points(), ranks,
                progress.playerClass(), slots, progress.paragonLevel(), progress.paragonPoints(), paragon));
    }
}
