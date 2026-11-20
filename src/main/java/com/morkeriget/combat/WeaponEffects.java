package com.morkeriget.combat;

import java.util.List;

import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Weapons;
import com.morkeriget.skill.Mana;
import com.morkeriget.skill.Minions;
import com.morkeriget.smithy.Durability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

// What the extra kinds of weapon do on their own, see Weapons. Only your own swings count, not skills. Server only
public final class WeaponEffects {
    private static final double REAP_RADIUS = 2.5;
    private static final int STUN_TICKS = 20;
    // True while a scythe hurts the enemies around the one it hit
    private static boolean reaping;

    private WeaponEffects() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(WeaponEffects::onDamagePre);
    }

    // Dagger: more damage to an enemy that isn't fighting you, so an opening strike or one busy with a minion or another player
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        int backstab = bonus(player.getMainHandItem(), Weapons.BACKSTAB_TAG);
        if (backstab > 0 && MythicEffects.isSwing(player, target, event.getSource()) && unaware(player, target)) {
            event.setNewDamage(event.getNewDamage() * (1 + backstab / 100f));
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                    5, 0.2, 0.2, 0.2, 0.1);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
    }

    // Monsters turn to face you at once, so "from behind" can't work. Instead: one at full health (not hit yet), or one
    // that is going after someone else
    static boolean unaware(ServerPlayer player, LivingEntity target) {
        if (target.getHealth() >= target.getMaxHealth()) {
            return true;
        }
        return target instanceof Mob mob && mob.getTarget() != null && mob.getTarget() != player;
    }

    // Called by MythicEffects after a hit with a weapon. swing is false for skills
    static void afterHit(ServerPlayer player, LivingEntity target, ItemStack weapon, float damage, boolean swing) {
        if (!swing || reaping) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // Scythe: the enemies around the one you hit get part of the damage
        int reap = bonus(weapon, Weapons.REAP_TAG);
        if (reap > 0) {
            List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(REAP_RADIUS),
                    entity -> entity != target && Minions.isEnemy(entity) && entity.distanceTo(target) <= REAP_RADIUS);
            if (!around.isEmpty()) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        3, 0.8, 0.2, 0.8, 0);
                level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.7f);
                reaping = true;
                try {
                    for (LivingEntity entity : around) {
                        entity.hurt(player.damageSources().playerAttack(player), damage * reap / 100f);
                    }
                } finally {
                    reaping = false;
                }
            }
        }
        // Battle staff: mana for every hit
        int mana = bonus(weapon, Weapons.ARCANE_STRIKE_TAG);
        if (mana > 0) {
            Mana.add(player, mana);
            level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    8, 0.3, 0.3, 0.3, 0.4);
        }
        // Mace: a chance to stun, so the monster stands still for a moment
        int stun = bonus(weapon, Weapons.STUN_TAG);
        if (stun > 0 && target.isAlive() && player.getRandom().nextInt(100) < stun) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 9));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 4));
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
                    10, 0.3, 0.1, 0.3, 0.05);
            level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 1.8f);
        }
    }

    private static int bonus(ItemStack weapon, String tag) {
        return Durability.isBroken(weapon) ? 0 : Loot.affix(weapon, tag);
    }
}
