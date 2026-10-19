package com.morkeriget.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.loot.MythicItem;
import com.morkeriget.skill.Minions;
import com.morkeriget.skill.Skills;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
    // True while a mythic power hurts monsters itself, so that damage doesn't set the power off again
    private static boolean striking;

    private MythicEffects() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MythicEffects::onDamagePre);
        NeoForge.EVENT_BUS.addListener(MythicEffects::onDeath);
        NeoForge.EVENT_BUS.addListener(MythicEffects::onPlayerTick);
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
    }

    // Called by PlayerHits after a hit with a sword, axe or arrow, with the weapon used for it
    static void afterHit(ServerPlayer player, LivingEntity target, DamageSource source, ItemStack weapon, float damage) {
        if (striking) {
            return;
        }
        MythicItem mythic = MythicItem.of(weapon);
        if (mythic == MythicItem.DOOMBRINGER && source.getDirectEntity() == player) {
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

    private static void hurtAll(ServerPlayer player, List<LivingEntity> targets, float damage) {
        striking = true;
        try {
            for (LivingEntity entity : targets) {
                entity.hurt(player.damageSources().playerAttack(player), damage);
            }
        } finally {
            striking = false;
        }
    }

    // Starless Ring: all skills are ready again when an elite or a boss dies
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(Bosses.isBoss(dead) || Elites.isElite(dead)) || !(dead.getKillCredit() instanceof ServerPlayer player)
                || !MythicItem.has(player, MythicItem.STARLESS_RING)) {
            return;
        }
        Skills.resetCooldowns(player);
        player.displayClientMessage(Component.literal("Den Stjerneløse Ring: alle skills er klar").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    // Archangel's Mail heals and the Ancestors' Amulet takes health. Once a second, like the set bonuses
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
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
}
