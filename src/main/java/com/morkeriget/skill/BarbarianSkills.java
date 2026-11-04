package com.morkeriget.skill;

import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// The barbarian's variants and keystones that change damage or what happens on a kill: Berserk (Frenzy), Berserker
// Rage, Iron Skin and Call of Blood. The rest are in the skills themselves. Server only
final class BarbarianSkills {
    // Bleed: Whirlwind's hit again this part every second
    static final float BLEED_PER_SECOND = 0.1f;
    static final int BLEED_SECONDS = 3;
    // Berserk: more damage while Frenzy lasts
    static final float BERSERK = 0.2f;
    // Berserker Rage: more damage below this part of your health
    static final float RAGE = 0.3f;
    static final float RAGE_BELOW = 0.5f;
    // Iron Skin: less damage taken (and slower, see Progression)
    static final float IRON_SKIN = 0.25f;
    // Call of Blood: this part of your health back for each kill
    static final float CALL_OF_BLOOD_HEAL = 0.05f;

    private BarbarianSkills() {
    }

    static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer victim && Progression.has(victim, Upgrade.IRON_SKIN)) {
            event.setNewDamage(event.getNewDamage() * (1 - IRON_SKIN));
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player) {
            return;
        }
        if (Progression.has(player, Upgrade.FRENZY_BERSERK)
                && player.getPersistentData().getLong(SkillVisuals.FRENZY_UNTIL_TAG) > player.serverLevel().getGameTime()) {
            event.setNewDamage(event.getNewDamage() * (1 + BERSERK));
        }
        if (Progression.has(player, Upgrade.BERSERKER_RAGE) && player.getHealth() < player.getMaxHealth() * RAGE_BELOW) {
            event.setNewDamage(event.getNewDamage() * (1 + RAGE));
        }
    }

    // Call of Blood: every monster you kill heals you a little
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Enemy && !Minions.isAlly(dead) && dead.getKillCredit() instanceof ServerPlayer player
                && Progression.has(player, Upgrade.CALL_OF_BLOOD)) {
            player.heal(player.getMaxHealth() * CALL_OF_BLOOD_HEAL);
            player.serverLevel().sendParticles(SkillVisuals.BLOOD, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0);
            player.serverLevel().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2, player.getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
    }
}
