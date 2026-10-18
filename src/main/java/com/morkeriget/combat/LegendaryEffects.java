package com.morkeriget.combat;

import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.skill.Mana;
import com.morkeriget.skill.Minions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// The legendary powers everyone can use: Cheat Death, Soul Eater, Thunderstrike and Bloodlust. The class powers
// are in the skills they change. Server only
public final class LegendaryEffects {
    // 20 ticks is one second
    private static final int CHEAT_DEATH_DURATION = 2 * 20;
    private static final int CHEAT_DEATH_COOLDOWN = 60 * 20;
    private static final String CHEAT_DEATH_READY_TAG = "morkeriget_cheat_death_ready";
    private static final float SOUL_EATER_MANA = 5;
    private static final int BLOODLUST_TIME = 5 * 20;
    private static final int BLOODLUST_MAX_STACKS = 10;
    private static final String BLOODLUST_STACKS_TAG = "morkeriget_bloodlust_stacks";
    private static final String BLOODLUST_UNTIL_TAG = "morkeriget_bloodlust_until";

    private LegendaryEffects() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(LegendaryEffects::onDamagePre);
        NeoForge.EVENT_BUS.addListener(LegendaryEffects::onDeath);
    }

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            cheatDeath(victim, event);
        }
        // Bloodlust works on all damage from the player, also from skills, but not thorns or what minions do
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity()) {
            int stacks = bloodlustStacks(player);
            if (stacks > 0 && LegendaryPower.has(player, LegendaryPower.BLOODLUST)) {
                event.setNewDamage(event.getNewDamage() * (1 + 0.03f * stacks));
            }
        }
    }

    // A hit that would take you under 20% health can't kill you, and then nothing hurts you for two seconds
    private static void cheatDeath(ServerPlayer player, LivingDamageEvent.Pre event) {
        float healthAfter = player.getHealth() - event.getNewDamage();
        if (healthAfter >= player.getMaxHealth() * 0.2f || event.getNewDamage() <= 0) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now < player.getPersistentData().getLong(CHEAT_DEATH_READY_TAG) || !LegendaryPower.has(player, LegendaryPower.CHEAT_DEATH)) {
            return;
        }
        player.getPersistentData().putLong(CHEAT_DEATH_READY_TAG, now + CHEAT_DEATH_COOLDOWN);
        if (healthAfter < 1) {
            event.setNewDamage(Math.max(0, player.getHealth() - 1));
        }
        // Resistance V takes away all damage
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, CHEAT_DEATH_DURATION, 4));
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    // Soul Eater and Bloodlust count kills, also the ones your minions make for you
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || Minions.isAlly(dead) || !(dead.getKillCredit() instanceof ServerPlayer player)) {
            return;
        }
        if (LegendaryPower.has(player, LegendaryPower.SOUL_EATER)) {
            Mana.add(player, SOUL_EATER_MANA);
            player.serverLevel().sendParticles(ParticleTypes.SOUL, dead.getX(), dead.getY() + 1, dead.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
        }
        if (LegendaryPower.has(player, LegendaryPower.BLOODLUST)) {
            CompoundTag data = player.getPersistentData();
            data.putInt(BLOODLUST_STACKS_TAG, Math.min(BLOODLUST_MAX_STACKS, bloodlustStacks(player) + 1));
            data.putLong(BLOODLUST_UNTIL_TAG, player.serverLevel().getGameTime() + BLOODLUST_TIME);
        }
    }

    // How many kills count for Bloodlust right now. They all run out together 5 seconds after the last kill
    private static int bloodlustStacks(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return player.serverLevel().getGameTime() <= data.getLong(BLOODLUST_UNTIL_TAG) ? data.getInt(BLOODLUST_STACKS_TAG) : 0;
    }

    // Thunderstrike: a bolt of sparks coming down on the target of a crushing blow. The extra damage is put on
    // the hit in PlayerHits. A real lightning bolt would be heard all over the world every few hits
    static void thunder(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        for (double y = 0; y < 6; y += 0.3) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + y, target.getZ(), 2, 0.1, 0.05, 0.1, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.4f);
    }
}
