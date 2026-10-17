package com.morkeriget.elite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// Rare named bosses: an elite with three modifiers that is bigger and much tougher, has a boss bar,
// slams the ground and calls in minions. Always drops a legendary item (see Loot)
public final class Bosses {
    private static final String BOSS_TAG = "morkeriget_boss";
    private static final String TITLE_TAG = "morkeriget_boss_title";
    private static final String MINION_TAG = "morkeriget_minion";
    private static final ResourceLocation BOSS_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "boss_boost");
    // Players closer than this see the boss bar
    private static final double BAR_RANGE = 32.0;
    private static final double SLAM_RADIUS = 5.0;
    private static final int MAX_MINIONS = 4;
    // 20 ticks is one second
    private static final int SLAM_INTERVAL = 8 * 20;
    private static final int SLAM_WARNING = 20;
    private static final int MINION_INTERVAL = 10 * 20;

    private static final List<String> NAMES = List.of(
            "Slagteren", "Gravkongen", "Blodmoderen", "Den Hule Ridder", "Askeherren",
            "Kragedronningen", "Rådne Ragnar", "Mørkets Hyrde", "Knogleknuseren", "Sjæleæderen");

    // Boss bars only live in memory. If the world is reloaded, the bar is made again the next time the boss ticks
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private Bosses() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(Bosses::onEntityTick);
        NeoForge.EVENT_BUS.addListener(Bosses::onDeath);
        NeoForge.EVENT_BUS.addListener(Bosses::onLeaveLevel);
    }

    public static boolean isBoss(Entity entity) {
        return entity.getPersistentData().getBoolean(BOSS_TAG);
    }

    // Creepers would blow themselves up, and the Wither and the Warden are already bosses
    static boolean canBeBoss(Monster monster) {
        return !(monster instanceof Creeper) && !(monster instanceof WitherBoss) && !(monster instanceof Warden);
    }

    static void makeBoss(Monster monster) {
        List<EliteModifier> modifiers = Elites.makeElite(monster, 3);
        monster.getPersistentData().putBoolean(BOSS_TAG, true);

        // The config has the total multiplier, so take away what the elite boost already gave.
        // With the default 4x health a boss zombie takes about 12 hits with a diamond sword
        boost(monster, Attributes.MAX_HEALTH, Config.BOSS_HEALTH.get() / Config.ELITE_HEALTH.get() - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        boost(monster, Attributes.ATTACK_DAMAGE, Config.BOSS_DAMAGE.get() / Config.ELITE_DAMAGE.get() - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        boost(monster, Attributes.SCALE, 0.5, AttributeModifier.Operation.ADD_VALUE);
        boost(monster, Attributes.KNOCKBACK_RESISTANCE, 0.5, AttributeModifier.Operation.ADD_VALUE);
        monster.setHealth(monster.getMaxHealth());
        // Bosses must not despawn when the player walks away
        monster.setPersistenceRequired();

        String name = NAMES.get(monster.getRandom().nextInt(NAMES.size()));
        monster.setCustomName(Component.literal(name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        List<String> modifierNames = new ArrayList<>();
        for (EliteModifier modifier : modifiers) {
            modifierNames.add(modifier.displayName);
        }
        monster.getPersistentData().putString(TITLE_TAG, name + " - " + String.join(", ", modifierNames));
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Monster boss) || !(boss.level() instanceof ServerLevel level)
                || boss.tickCount % 5 != 0 || !isBoss(boss) || !boss.isAlive()) {
            return;
        }
        updateBar(level, boss);

        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        int slamTime = boss.tickCount % SLAM_INTERVAL;
        if (slamTime == SLAM_INTERVAL - SLAM_WARNING && boss.distanceTo(target) < SLAM_RADIUS + 2) {
            // A roar and a ring of smoke a second before the slam, so the player can get away
            level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.8f);
            ring(level, boss, ParticleTypes.LARGE_SMOKE);
        } else if (slamTime == 0 && boss.distanceTo(target) < SLAM_RADIUS + 2) {
            slam(level, boss);
        }
        if (boss.tickCount % MINION_INTERVAL == 0) {
            summonMinions(level, boss, target);
        }
    }

    private static void updateBar(ServerLevel level, Monster boss) {
        ServerBossEvent bar = BARS.computeIfAbsent(boss.getUUID(), uuid -> new ServerBossEvent(
                Component.literal(boss.getPersistentData().getString(TITLE_TAG)).withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
        bar.setProgress(boss.getHealth() / boss.getMaxHealth());
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (player.level() != level || player.distanceTo(boss) > BAR_RANGE) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(boss) <= BAR_RANGE) {
                bar.addPlayer(player);
            }
        }
    }

    // Hits the ground and throws every player close by away from the boss
    private static void slam(ServerLevel level, Monster boss) {
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75f;
        for (Player player : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(SLAM_RADIUS),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative())) {
            player.hurt(boss.damageSources().mobAttack(boss), damage);
            Vec3 push = player.position().subtract(boss.position()).normalize();
            player.push(push.x * 1.2, 0.6, push.z * 1.2);
            // Tells the client the player was pushed, otherwise the push only happens on the server
            player.hurtMarked = true;
        }
        ring(level, boss, ParticleTypes.EXPLOSION);
        level.playSound(null, boss.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    private static void summonMinions(ServerLevel level, Monster boss, LivingEntity target) {
        int nearby = level.getEntitiesOfClass(Monster.class, boss.getBoundingBox().inflate(16),
                monster -> monster.getPersistentData().getBoolean(MINION_TAG)).size();
        for (int i = nearby; i < MAX_MINIONS && i < nearby + 2; i++) {
            if (!(boss.getType().create(level) instanceof Monster minion)) {
                return;
            }
            Elites.markChecked(minion);
            minion.getPersistentData().putBoolean(MINION_TAG, true);
            int bossLevel = MonsterLevels.get(boss);
            if (bossLevel > 0) {
                MonsterLevels.apply(minion, Math.max(1, bossLevel - 2));
            }
            double x = boss.getX() + (boss.getRandom().nextDouble() - 0.5) * 4;
            double z = boss.getZ() + (boss.getRandom().nextDouble() - 0.5) * 4;
            minion.moveTo(x, boss.getY(), z, boss.getRandom().nextFloat() * 360, 0);
            if (!level.noCollision(minion)) {
                continue;
            }
            // Gives it its normal gear, like a bow for skeletons
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            minion.setTarget(target);
            level.addFreshEntity(minion);
            level.sendParticles(ParticleTypes.SOUL, x, boss.getY() + 0.5, z, 15, 0.3, 0.5, 0.3, 0.02);
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 0.9f);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Monster boss) || !(boss.level() instanceof ServerLevel level) || !isBoss(boss)) {
            return;
        }
        Component message = Component.empty().append(boss.getDisplayName())
                .append(Component.literal(" er besejret!").withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(boss) <= BAR_RANGE) {
                player.sendSystemMessage(message);
                level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
        removeBar(boss);
    }

    // Also happens when the boss's chunk is unloaded, so the bar doesn't hang around
    private static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && isBoss(event.getEntity())) {
            removeBar(event.getEntity());
        }
    }

    private static void removeBar(Entity boss) {
        ServerBossEvent bar = BARS.remove(boss.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    private static void ring(ServerLevel level, Monster boss, ParticleOptions particle) {
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            level.sendParticles(particle, boss.getX() + Math.cos(angle) * SLAM_RADIUS * 0.6, boss.getY() + 0.2,
                    boss.getZ() + Math.sin(angle) * SLAM_RADIUS * 0.6, 1, 0, 0, 0, 0);
        }
    }

    private static void boost(Monster monster, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = monster.getAttribute(attribute);
        if (instance != null) {
            instance.addPermanentModifier(new AttributeModifier(BOSS_BOOST, amount, operation));
        }
    }
}
