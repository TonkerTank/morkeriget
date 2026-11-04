package com.morkeriget.skill;

import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

// The ranger's keystones: Sniper and Hunter's Instinct change what arrows do, Light Step dodges hits (its lower health
// is in Progression). Server only
final class RangerSkills {
    // Sniper: more arrow damage from far away, less up close
    static final double SNIPER_RANGE = 8.0;
    static final float SNIPER_FAR = 0.4f;
    static final float SNIPER_CLOSE = 0.2f;
    // Hunter's Instinct: every HUNTER_EVERY arrow that hits does twice the damage
    static final int HUNTER_EVERY = 4;
    private static final String HUNTER_TAG = "morkeriget_hunter_arrows";
    // Light Step: the chance to take no damage from a monster's hit
    static final float DODGE = 0.2f;

    private RangerSkills() {
    }

    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target instanceof ServerPlayer victim && Progression.has(victim, Upgrade.LIGHT_STEP)
                && event.getSource().getEntity() instanceof LivingEntity && victim.getRandom().nextFloat() < DODGE) {
            event.setNewDamage(0);
            victim.serverLevel().sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY() + 1, victim.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            victim.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.4f);
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow) || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || target == player) {
            return;
        }
        if (Progression.has(player, Upgrade.SNIPER)) {
            boolean far = player.distanceTo(target) > SNIPER_RANGE;
            event.setNewDamage(event.getNewDamage() * (far ? 1 + SNIPER_FAR : 1 - SNIPER_CLOSE));
        }
        if (Progression.has(player, Upgrade.HUNTER_INSTINCT)) {
            int count = player.getPersistentData().getInt(HUNTER_TAG) + 1;
            if (count >= HUNTER_EVERY) {
                count = 0;
                event.setNewDamage(event.getNewDamage() * 2);
                player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        15, 0.3, 0.3, 0.3, 0.3);
            }
            player.getPersistentData().putInt(HUNTER_TAG, count);
        }
    }
}
