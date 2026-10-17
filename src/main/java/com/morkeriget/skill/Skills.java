package com.morkeriget.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;

// Skills the player can use. They run on the server so monsters really take the damage
public final class Skills {
    private static final String WHIRLWIND_READY_TAG = "morkeriget_whirlwind_ready";
    // 20 ticks is one second
    private static final int WHIRLWIND_COOLDOWN = 5 * 20;
    private static final double WHIRLWIND_RADIUS = 4.0;

    private Skills() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(WhirlwindPayload::register);
    }

    // Spins around and hits every monster close to you
    public static void whirlwind(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long ready = player.getPersistentData().getLong(WHIRLWIND_READY_TAG);
        if (now < ready) {
            long seconds = (ready - now + 19) / 20;
            player.displayClientMessage(Component.literal("Hvirvelvind er klar om " + seconds + " sek."), true);
            return;
        }
        player.getPersistentData().putLong(WHIRLWIND_READY_TAG, now + WHIRLWIND_COOLDOWN);

        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WHIRLWIND_RADIUS),
                entity -> entity instanceof Enemy && entity.isAlive())) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 push = target.position().subtract(player.position()).normalize();
            target.knockback(0.6, -push.x, -push.z);
        }

        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * 2.5, player.getY() + 1,
                    player.getZ() + Math.sin(angle) * 2.5, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}
