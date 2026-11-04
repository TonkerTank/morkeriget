package com.morkeriget.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Damage that comes once a second for a while, from the player, like Fireball's Burn and Whirlwind's Bleed. It stops
// if the monster dies, and when the player dies, logs out or changes dimension. Server only
final class DamageOverTime {
    private record Tick(LivingEntity target, float damage, int left, long next, ParticleOptions particle) {
    }

    private static final Map<UUID, List<Tick>> TICKS = new HashMap<>();

    private DamageOverTime() {
    }

    // damage every second for seconds, the first one a second from now
    static void add(ServerPlayer player, LivingEntity target, float damage, int seconds, ParticleOptions particle) {
        TICKS.computeIfAbsent(player.getUUID(), uuid -> new ArrayList<>())
                .add(new Tick(target, damage, seconds, player.serverLevel().getGameTime() + 20, particle));
    }

    static void forget(Player player) {
        TICKS.remove(player.getUUID());
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<Tick> ticks = TICKS.get(player.getUUID());
        if (ticks == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        List<Tick> next = new ArrayList<>();
        for (Iterator<Tick> it = ticks.iterator(); it.hasNext(); ) {
            Tick tick = it.next();
            if (now < tick.next) {
                continue;
            }
            it.remove();
            LivingEntity target = tick.target;
            if (!target.isAlive() || target.level() != level) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), tick.damage);
            level.sendParticles(tick.particle, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            if (tick.left > 1) {
                next.add(new Tick(target, tick.damage, tick.left - 1, now + 20, tick.particle));
            }
        }
        ticks.addAll(next);
        if (ticks.isEmpty()) {
            TICKS.remove(player.getUUID());
        }
    }
}
