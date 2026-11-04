package com.morkeriget.skill;

import java.util.List;

import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

// The sorcerer's skill variants that need more than a line in the skill itself: Fireball's Burn and Splinter, Frost
// Nova's Deep Freeze and Ice Mantle, and Blink's Arcane Blast and Phase Shift. Server only
final class SorcererSkills {
    // 20 ticks is one second
    // Burn: this part of the Fireball's hit again every second, for BURN_SECONDS
    static final float BURN_PER_SECOND = 0.15f;
    static final int BURN_SECONDS = 4;
    // Splinter: smaller fireballs a few blocks from where it hit, each with this part of the damage
    static final int SPLINTERS = 3;
    static final float SPLINTER_DAMAGE = 0.5f;
    private static final double SPLINTER_DISTANCE = 2.5;
    private static final double SPLINTER_RADIUS = 2.0;
    // Deep Freeze: frozen monsters take this much more damage
    static final float DEEP_FREEZE_BONUS = 0.25f;
    private static final String DEEP_FREEZE_TAG = "morkeriget_deep_freeze_until";
    // Ice Mantle: absorption for each monster the nova hits, at most this many levels (each level is two hearts)
    private static final int ICE_MANTLE_MAX = 4;
    private static final int ICE_MANTLE_DURATION = 6 * 20;
    // Arcane Blast where Blink lands
    private static final double ARCANE_BLAST_RADIUS = 3.0;
    private static final double ARCANE_BLAST_DAMAGE = 1.5;
    // Phase Shift: Resistance III (60% less damage) for a moment after Blink
    private static final int PHASE_SHIFT_DURATION = 2 * 20;

    private SorcererSkills() {
    }

    // What the Fireball variants do to the monsters it hit
    static void afterFireball(ServerPlayer player, Vec3 impact, List<LivingEntity> hit, float damage) {
        ServerLevel level = player.serverLevel();
        if (Progression.has(player, Upgrade.FIREBALL_BURN)) {
            for (LivingEntity target : hit) {
                DamageOverTime.add(player, target, damage * BURN_PER_SECOND, BURN_SECONDS, ParticleTypes.FLAME);
            }
        }
        if (Progression.has(player, Upgrade.FIREBALL_SPLINTER)) {
            double start = player.getRandom().nextDouble() * Math.PI * 2;
            for (int i = 0; i < SPLINTERS; i++) {
                double angle = start + Math.PI * 2 * i / SPLINTERS;
                Vec3 spot = impact.add(Math.cos(angle) * SPLINTER_DISTANCE, 0, Math.sin(angle) * SPLINTER_DISTANCE);
                for (LivingEntity target : ClassSkills.enemies(level, spot, SPLINTER_RADIUS)) {
                    target.invulnerableTime = 0;
                    target.hurt(player.damageSources().playerAttack(player), damage * SPLINTER_DAMAGE);
                }
                SkillVisuals.line(level, impact, spot, 0.4, ParticleTypes.FLAME);
                level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y, spot.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y, spot.z, 15, 0.5, 0.5, 0.5, 0.06);
                SkillVisuals.ring(level, spot, 0, SPLINTER_RADIUS, 12, SkillVisuals.FIRE_FADE);
            }
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
    }

    // What the Frost Nova variants do to the monsters it hit and froze for duration
    static void afterFrostNova(ServerPlayer player, List<LivingEntity> hit, int duration) {
        if (Progression.has(player, Upgrade.FROST_NOVA_DEEP_FREEZE)) {
            long until = player.serverLevel().getGameTime() + duration;
            for (LivingEntity target : hit) {
                target.getPersistentData().putLong(DEEP_FREEZE_TAG, until);
            }
        }
        if (Progression.has(player, Upgrade.FROST_NOVA_ICE_MANTLE) && !hit.isEmpty()) {
            int levels = Math.min(hit.size(), ICE_MANTLE_MAX);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ICE_MANTLE_DURATION, levels - 1));
            SkillVisuals.spiral(player.serverLevel(), player.position(), 0.8, 2.0, 1.5, 16, SkillVisuals.FROST_FADE);
        }
    }

    // What the Blink variants do once you have landed
    static void afterBlink(ServerPlayer player, Vec3 landing) {
        ServerLevel level = player.serverLevel();
        if (Progression.has(player, Upgrade.BLINK_ARCANE_BLAST)) {
            float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * ARCANE_BLAST_DAMAGE);
            for (LivingEntity target : ClassSkills.enemies(level, landing, ARCANE_BLAST_RADIUS)) {
                target.hurt(player.damageSources().playerAttack(player), damage);
                Vec3 push = target.position().subtract(landing).normalize();
                target.knockback(0.8, -push.x, -push.z);
            }
            SkillVisuals.shockwave(level, landing, 0.1, ARCANE_BLAST_RADIUS, 2, SkillVisuals.ARCANE);
            SkillVisuals.burst(level, landing, 0.5, 24, 0.3, ParticleTypes.WITCH);
            level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.4f);
        }
        if (Progression.has(player, Upgrade.BLINK_PHASE_SHIFT)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, PHASE_SHIFT_DURATION, 2));
            level.sendParticles(ParticleTypes.PORTAL, landing.x, landing.y + 1, landing.z, 30, 0.4, 0.7, 0.4, 0.3);
        }
    }

    // Deep Freeze: more damage to frozen monsters, from anyone
    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.getPersistentData().getLong(DEEP_FREEZE_TAG) > target.level().getGameTime()) {
            event.setNewDamage(event.getNewDamage() * (1 + DEEP_FREEZE_BONUS));
        }
    }
}
