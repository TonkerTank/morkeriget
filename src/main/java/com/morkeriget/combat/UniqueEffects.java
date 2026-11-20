package com.morkeriget.combat;

import java.util.List;

import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.loot.UniqueItem;
import com.morkeriget.skill.Mana;
import com.morkeriget.skill.Minions;
import com.morkeriget.skill.Skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// What the unique items do, see UniqueItem. Server only
public final class UniqueEffects {
    private static final double COLOSSUS_RADIUS = 4.0;
    private static final float COLOSSUS_DAMAGE = 1.0f;
    private static final int COLOSSUS_COOLDOWN = 3 * 20;
    private static final String COLOSSUS_READY_TAG = "morkeriget_colossus_ready";
    private static final float CROWN_DAMAGE = 1.25f;
    private static final double HELLFIRE_RADIUS = 3.0;
    private static final float HELLFIRE_DAMAGE = 0.5f;
    private static final int HELLFIRE_BURN_TICKS = 4 * 20;
    private static final float LANTERN_MANA = 0.03f;
    private static final float LANTERN_HEAL = 0.01f;
    private static final int RIFTWALKER_TICKS = 10;
    // True while a unique hurts monsters itself, so a Hellfire Band kill doesn't set off another and another
    private static boolean striking;

    private UniqueEffects() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(UniqueEffects::onDamagePre);
        NeoForge.EVENT_BUS.addListener(UniqueEffects::onDamagePost);
        NeoForge.EVENT_BUS.addListener(UniqueEffects::onDeath);
    }

    // The Dark Lord's Crown: more damage to elites and bosses, from every kind of hit
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer player && (Bosses.isBoss(victim) || Elites.isElite(victim))
                && UniqueItem.has(player, UniqueItem.DARK_LORDS_CROWN)) {
            event.setNewDamage(event.getNewDamage() * CROWN_DAMAGE);
        }
    }

    // The Colossus Plate: being hit by a monster sends a shockwave out that hurts and throws back everything around you
    private static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !Minions.isEnemy(attacker) || event.getNewDamage() <= 0 || !UniqueItem.has(player, UniqueItem.COLOSSUS_PLATE)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now < player.getPersistentData().getLong(COLOSSUS_READY_TAG)) {
            return;
        }
        player.getPersistentData().putLong(COLOSSUS_READY_TAG, now + COLOSSUS_COOLDOWN);
        for (int ring = 1; ring <= 3; ring++) {
            double radius = COLOSSUS_RADIUS * ring / 3;
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24;
                level.sendParticles(ParticleTypes.CLOUD, player.getX() + Math.cos(angle) * radius, player.getY() + 0.2,
                        player.getZ() + Math.sin(angle) * radius, 1, 0, 0.02, 0, 0.02);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.3f);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 0.6f);
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(COLOSSUS_RADIUS),
                entity -> Minions.isEnemy(entity) && entity.distanceTo(player) <= COLOSSUS_RADIUS);
        hurtAll(player, enemies, weaponDamage(player) * COLOSSUS_DAMAGE);
        for (LivingEntity enemy : enemies) {
            enemy.knockback(1.2, player.getX() - enemy.getX(), player.getZ() - enemy.getZ());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        // Minions.isEnemy() wants it alive, and it isn't any more
        if (!(dead.getKillCredit() instanceof ServerPlayer player) || !(dead instanceof Enemy) || Minions.isAlly(dead)) {
            return;
        }
        // Warden's Lantern: a little mana and health back for every kill
        if (UniqueItem.has(player, UniqueItem.WARDENS_LANTERN)) {
            Mana.add(player, Mana.MAX * LANTERN_MANA);
            player.heal(player.getMaxHealth() * LANTERN_HEAL);
        }
        // Riftwalkers: every kill brings the skills closer to ready
        if (UniqueItem.has(player, UniqueItem.RIFTWALKERS)) {
            Skills.reduceCooldowns(player, RIFTWALKER_TICKS);
        }
        if (!striking && UniqueItem.has(player, UniqueItem.HELLFIRE_BAND)) {
            hellfire(player, dead);
        }
    }

    // Hellfire Band: the monster bursts into flames that set the ones around it on fire
    private static void hellfire(ServerPlayer player, LivingEntity dead) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.FLAME, dead.getX(), dead.getY() + dead.getBbHeight() / 2, dead.getZ(), 30, 0.4, 0.4, 0.4, 0.12);
        level.sendParticles(ParticleTypes.LAVA, dead.getX(), dead.getY() + 0.5, dead.getZ(), 6, 0.3, 0.2, 0.3, 0);
        level.playSound(null, dead.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 0.7f);
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(HELLFIRE_RADIUS),
                entity -> entity != dead && Minions.isEnemy(entity) && entity.distanceTo(dead) <= HELLFIRE_RADIUS);
        for (LivingEntity enemy : enemies) {
            enemy.igniteForTicks(HELLFIRE_BURN_TICKS);
        }
        hurtAll(player, enemies, weaponDamage(player) * HELLFIRE_DAMAGE);
    }

    private static float weaponDamage(ServerPlayer player) {
        return (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    private static void hurtAll(ServerPlayer player, List<? extends Entity> targets, float damage) {
        striking = true;
        try {
            for (Entity entity : targets) {
                entity.hurt(player.damageSources().playerAttack(player), damage);
            }
        } finally {
            striking = false;
        }
    }
}
