package com.morkeriget.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.Text;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.loot.MythicItem;
import com.morkeriget.skill.Minions;
import com.morkeriget.skill.Skills;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import org.joml.Vector3f;

// What the mythic items do, see MythicItem. Server only
public final class MythicEffects {
    private static final int DOOMBRINGER_HITS = 4;
    private static final double DOOMBRINGER_RADIUS = 5.0;
    private static final float DOOMBRINGER_DAMAGE = 1.5f;
    private static final String DOOMBRINGER_HITS_TAG = "morkeriget_doombringer_hits";
    private static final double STARFALL_RANGE = 8.0;
    private static final int STARFALL_JUMPS = 2;
    private static final float STARFALL_DAMAGE = 0.75f;
    private static final float ARCHANGEL_DAMAGE_TAKEN = 0.7f;
    private static final float ARCHANGEL_HEAL = 0.01f;
    private static final float AMULET_DAMAGE = 1.4f;
    private static final ResourceLocation AMULET_HEALTH_ID = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "mythic_ancestors_amulet");
    private static final double WINTER_RADIUS = 4.0;
    private static final float WINTER_DAMAGE = 0.02f;
    private static final float WINTER_BOSS_DAMAGE = 0.005f;
    private static final double STRIDE_RANGE = 7.0;
    private static final double STRIDE_MIN_MOVE = 1.5;
    private static final float STRIDE_DAMAGE = 1.0f;
    private static final String STRIDE_X_TAG = "morkeriget_stride_x";
    private static final String STRIDE_Z_TAG = "morkeriget_stride_z";
    private static final int FURY_MAX = 10;
    private static final float FURY_DAMAGE = 0.06f;
    private static final int FURY_TICKS = 5 * 20;
    private static final String FURY_TAG = "morkeriget_fury";
    private static final String FURY_UNTIL_TAG = "morkeriget_fury_until";
    private static final double TEMPEST_RANGE = 12.0;
    private static final float TEMPEST_DAMAGE = 1.5f;
    public static final int GRAVECALLER_SKELETONS = 4;
    private static final float GRAVECALLER_DAMAGE = 1.5f;
    private static final float DAWNGUARD_DAMAGE = 2.0f;
    private static final float DAWNGUARD_HEAL = 0.05f;
    // At most once a second, so a pack of skeleton archers doesn't make it go off all the time
    private static final int DAWNGUARD_COOLDOWN = 20;
    private static final String DAWNGUARD_READY_TAG = "morkeriget_dawnguard_ready";
    private static final DustParticleOptions FURY_DUST = new DustParticleOptions(new Vector3f(0.9f, 0.12f, 0.08f), 1.2f);
    // True while a mythic power hurts monsters itself, so that damage doesn't set the power off again
    private static boolean striking;
    // What the player last swung their weapon at. Skills hurt monsters as the player too, so this is how Doombringer
    // tells a real swing from a skill. Set right before the swing's damage, on the server thread
    private static Entity swingTarget;

    private MythicEffects() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MythicEffects::onDamagePre);
        NeoForge.EVENT_BUS.addListener(MythicEffects::onDeath);
        NeoForge.EVENT_BUS.addListener(MythicEffects::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(MythicEffects::onShieldBlock);
        // Only the server's own swing, since in singleplayer the client runs in the same game and swings too
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                swingTarget = event.getTarget();
            }
        });
    }

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof ServerPlayer victim && attacker != null && (Bosses.isBoss(attacker) || Elites.isElite(attacker))
                && MythicItem.has(victim, MythicItem.ARCHANGELS_MAIL)) {
            event.setNewDamage(event.getNewDamage() * ARCHANGEL_DAMAGE_TAKEN);
        }
        // Like Bloodlust it works on all damage from the player, also from skills
        if (attacker instanceof ServerPlayer player && player != event.getEntity() && MythicItem.has(player, MythicItem.ANCESTORS_AMULET)) {
            event.setNewDamage(event.getNewDamage() * AMULET_DAMAGE);
        }
        // Bloodreaver: more damage for each stack of Fury, from every kind of hit
        if (attacker instanceof ServerPlayer player && player != event.getEntity() && MythicItem.has(player, MythicItem.BLOODREAVER)) {
            event.setNewDamage(event.getNewDamage() * (1 + FURY_DAMAGE * fury(player)));
        }
        // Gravecaller: the owner's skeletons, wolves and other minions hit harder
        if (attacker != null && Minions.isAlly(attacker) && attacker.level() instanceof ServerLevel level) {
            ServerPlayer owner = Minions.owner(level, attacker);
            if (owner != null && MythicItem.has(owner, MythicItem.GRAVECALLER)) {
                event.setNewDamage(event.getNewDamage() * GRAVECALLER_DAMAGE);
            }
        }
    }

    // Called by PlayerHits after a hit with a sword, axe or arrow, with the weapon used for it
    static void afterHit(ServerPlayer player, LivingEntity target, DamageSource source, ItemStack weapon, float damage) {
        if (striking) {
            return;
        }
        MythicItem mythic = MythicItem.of(weapon);
        boolean swing = source.getDirectEntity() == player && target == swingTarget;
        if (swing) {
            swingTarget = null;
        }
        if (mythic == MythicItem.DOOMBRINGER && swing) {
            int hits = player.getPersistentData().getInt(DOOMBRINGER_HITS_TAG) + 1;
            player.getPersistentData().putInt(DOOMBRINGER_HITS_TAG, hits % DOOMBRINGER_HITS);
            if (hits >= DOOMBRINGER_HITS) {
                shadowWave(player, target, damage * DOOMBRINGER_DAMAGE);
            }
        } else if (mythic == MythicItem.STARFALL && source.getDirectEntity() instanceof AbstractArrow) {
            starfall(player, target, damage * STARFALL_DAMAGE);
        }
    }

    // Doombringer: a ring of shadow around the player that hurts every monster in it except the one that was hit
    private static void shadowWave(ServerPlayer player, LivingEntity target, float damage) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI * 2 / 32;
            for (double distance = 1.5; distance <= DOOMBRINGER_RADIUS; distance += 1.5) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX() + Math.cos(angle) * distance, player.getY() + 0.3,
                        player.getZ() + Math.sin(angle) * distance, 1, 0, 0.05, 0, 0.01);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0f, 0.7f);
        hurtAll(player, level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DOOMBRINGER_RADIUS),
                entity -> entity != target && Minions.isEnemy(entity)), damage);
    }

    // Starfall: the arrow jumps on from the monster it hit to the nearest monsters, one after the other
    private static void starfall(ServerPlayer player, LivingEntity target, float damage) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> hit = new ArrayList<>(List.of(target));
        LivingEntity from = target;
        for (int jump = 0; jump < STARFALL_JUMPS; jump++) {
            LivingEntity start = from;
            LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, start.getBoundingBox().inflate(STARFALL_RANGE),
                            entity -> !hit.contains(entity) && Minions.isEnemy(entity)).stream()
                    .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(start)))
                    .orElse(null);
            if (next == null) {
                break;
            }
            Vec3 a = start.position().add(0, start.getBbHeight() / 2, 0);
            Vec3 b = next.position().add(0, next.getBbHeight() / 2, 0);
            for (double t = 0; t <= 1; t += 0.1) {
                Vec3 point = a.lerp(b, t);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
            hit.add(next);
            from = next;
        }
        if (hit.size() > 1) {
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.6f);
            hurtAll(player, hit.subList(1, hit.size()), damage);
        }
    }

    // damage comes from a hit that already had the Ancestors' Amulet on it, and these hits get it again in onDamagePre,
    // so it is taken out first
    private static void hurtAll(ServerPlayer player, List<LivingEntity> targets, float damage) {
        if (MythicItem.has(player, MythicItem.ANCESTORS_AMULET)) {
            damage /= AMULET_DAMAGE;
        }
        striking = true;
        try {
            for (LivingEntity entity : targets) {
                entity.hurt(player.damageSources().playerAttack(player), damage);
            }
        } finally {
            striking = false;
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        // Minions.isEnemy() wants it alive, and it isn't any more
        if (!(dead.getKillCredit() instanceof ServerPlayer player) || !(dead instanceof Enemy) || Minions.isAlly(dead)) {
            return;
        }
        if (MythicItem.has(player, MythicItem.BLOODREAVER)) {
            addFury(player);
        }
        // Starless Ring: all skills are ready again when an elite or a boss dies
        if (!(Bosses.isBoss(dead) || Elites.isElite(dead)) || !MythicItem.has(player, MythicItem.STARLESS_RING)) {
            return;
        }
        Skills.resetCooldowns(player);
        player.displayClientMessage(Text.tr("mythic.starless_ring.ready").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    // Archangel's Mail heals and the Ancestors' Amulet takes health. Once a second, like the set bonuses
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (MythicItem.has(player, MythicItem.WINTERS_GRASP)) {
            wintersGrasp(player);
        }
        if (MythicItem.has(player, MythicItem.STORMSTRIDERS)) {
            stormstriders(player);
        }
        if (MythicItem.has(player, MythicItem.ARCHANGELS_MAIL) && player.getHealth() < player.getMaxHealth()) {
            player.heal(player.getMaxHealth() * ARCHANGEL_HEAL);
        }
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            if (MythicItem.has(player, MythicItem.ANCESTORS_AMULET)) {
                health.addOrUpdateTransientModifier(new AttributeModifier(AMULET_HEALTH_ID, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            } else {
                health.removeModifier(AMULET_HEALTH_ID);
            }
        }
    }

    // Winter's Grasp: enemies close by are slowed and lose a little of their health every second. Bosses lose less
    private static void wintersGrasp(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WINTER_RADIUS),
                entity -> Minions.isEnemy(entity) && entity.distanceTo(player) <= WINTER_RADIUS);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12 + player.tickCount * 0.05;
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX() + Math.cos(angle) * WINTER_RADIUS, player.getY() + 0.3,
                    player.getZ() + Math.sin(angle) * WINTER_RADIUS, 1, 0, 0.05, 0, 0.01);
        }
        striking = true;
        try {
            for (LivingEntity enemy : enemies) {
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                float share = Bosses.isBoss(enemy) ? WINTER_BOSS_DAMAGE : WINTER_DAMAGE;
                enemy.hurt(player.damageSources().indirectMagic(player, player), enemy.getMaxHealth() * share);
            }
        } finally {
            striking = false;
        }
    }

    // Stormstriders: while you are on the move, lightning jumps from you to the nearest enemy every second
    private static void stormstriders(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        double moved = Math.hypot(player.getX() - data.getDouble(STRIDE_X_TAG), player.getZ() - data.getDouble(STRIDE_Z_TAG));
        data.putDouble(STRIDE_X_TAG, player.getX());
        data.putDouble(STRIDE_Z_TAG, player.getZ());
        if (moved < STRIDE_MIN_MOVE) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(STRIDE_RANGE),
                        entity -> Minions.isEnemy(entity) && entity.distanceTo(player) <= STRIDE_RANGE).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .orElse(null);
        if (target == null) {
            return;
        }
        Vec3 from = player.position().add(0, 1, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
        for (double t = 0; t <= 1; t += 0.08) {
            Vec3 point = from.lerp(to, t);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 2, 0.06, 0.06, 0.06, 0);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35f, 1.9f);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIDE_DAMAGE;
        striking = true;
        try {
            target.hurt(player.damageSources().indirectMagic(player, player), damage);
        } finally {
            striking = false;
        }
    }

    // How many stacks of Fury the player has right now. They all run out together, 5 seconds after the last kill
    public static int fury(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return player.serverLevel().getGameTime() < data.getLong(FURY_UNTIL_TAG) ? data.getInt(FURY_TAG) : 0;
    }

    private static void addFury(ServerPlayer player) {
        int stacks = Math.min(FURY_MAX, fury(player) + 1);
        CompoundTag data = player.getPersistentData();
        data.putInt(FURY_TAG, stacks);
        data.putLong(FURY_UNTIL_TAG, player.serverLevel().getGameTime() + FURY_TICKS);
        player.serverLevel().sendParticles(FURY_DUST, player.getX(), player.getY() + 1, player.getZ(), 4 + stacks, 0.35, 0.5, 0.35, 0);
        if (stacks == FURY_MAX) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.4f, 1.4f);
        }
    }

    // Called by Skills after a class skill was used. Eye of the Tempest: lightning strikes the nearest enemy
    public static void skillUsed(ServerPlayer player) {
        if (!MythicItem.has(player, MythicItem.EYE_OF_THE_TEMPEST)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TEMPEST_RANGE),
                        entity -> Minions.isEnemy(entity) && entity.distanceTo(player) <= TEMPEST_RANGE).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .orElse(null);
        if (target == null) {
            return;
        }
        // A bolt from the sky down to the target
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 from = to.add(0, 8, 0);
        for (double t = 0; t <= 1; t += 0.05) {
            Vec3 point = from.lerp(to, t);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x + (level.random.nextDouble() - 0.5) * 0.3, point.y,
                    point.z + (level.random.nextDouble() - 0.5) * 0.3, 2, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.4f);
        hurtAll(player, List.of(target), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * TEMPEST_DAMAGE);
    }

    // Dawnguard: blocking a hit sends holy light down on the one who made it and heals the player
    private static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getBlocked() || !MythicItem.has(player, MythicItem.DAWNGUARD)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now < player.getPersistentData().getLong(DAWNGUARD_READY_TAG)) {
            return;
        }
        player.getPersistentData().putLong(DAWNGUARD_READY_TAG, now + DAWNGUARD_COOLDOWN);
        player.heal(player.getMaxHealth() * DAWNGUARD_HEAL);
        if (!(event.getDamageSource().getEntity() instanceof LivingEntity attacker) || !Minions.isEnemy(attacker)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (double y = 0; y < 4; y += 0.25) {
            level.sendParticles(ParticleTypes.END_ROD, attacker.getX(), attacker.getY() + y, attacker.getZ(), 1, 0.15, 0, 0.15, 0);
        }
        level.playSound(null, attacker.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.6f);
        hurtAll(player, List.of(attacker), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAWNGUARD_DAMAGE);
    }
}
