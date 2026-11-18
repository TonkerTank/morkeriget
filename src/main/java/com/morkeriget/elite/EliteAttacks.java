package com.morkeriget.elite;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import org.joml.Vector3f;

// What the Arcane, Mortar, Plagued and Electrified elites do, see EliteModifier. Called from Elites every 5 ticks while
// the elite is alive, and when it is hit. They only attack while the elite has a target close by, so an elite standing
// alone doesn't fill the world with beams and pools. Server only
final class EliteAttacks {
    // Arcane: two beams this long, on for BEAM_ON ticks out of every BEAM_CYCLE, sweeping around at BEAM_TURN per tick
    private static final double BEAM_LENGTH = 7;
    private static final int BEAM_CYCLE = 140;
    private static final int BEAM_ON = 80;
    private static final double BEAM_TURN = 0.045;
    private static final double BEAM_WIDTH = 0.8;
    private static final DustParticleOptions BEAM_DUST = new DustParticleOptions(new Vector3f(0.75f, 0.3f, 1f), 1.4f);
    // Mortar: a blast every few seconds at a player at least this far away, landing MORTAR_DELAY ticks after the
    // spot starts to glow
    private static final int MORTAR_INTERVAL = 80;
    private static final int MORTAR_DELAY = 30;
    private static final double MORTAR_MIN_DISTANCE = 5;
    private static final double MORTAR_RADIUS = 2.5;
    private static final String MORTAR_TAG = "morkeriget_mortar";
    // Plagued: a poison pool every few seconds
    private static final int PLAGUE_INTERVAL = 50;
    // Electrified: the chance that a hit sends a spark back, and how far it reaches
    private static final float SPARK_CHANCE = 0.4f;
    private static final double SPARK_RANGE = 8;
    // How close the target has to be for the elite to use these
    private static final double FIGHT_RANGE = 20;

    private EliteAttacks() {
    }

    static void tick(ServerLevel level, Monster monster) {
        LivingEntity target = monster.getTarget();
        boolean fighting = target instanceof Player && target.isAlive() && monster.distanceTo(target) < FIGHT_RANGE;
        if (EliteModifier.ARCANE.isOn(monster) && fighting) {
            arcane(level, monster);
        }
        if (EliteModifier.MORTAR.isOn(monster)) {
            mortar(level, monster, fighting ? target : null);
        }
        if (EliteModifier.PLAGUED.isOn(monster) && fighting && monster.tickCount % PLAGUE_INTERVAL < 5) {
            plague(level, monster);
        }
    }

    private static float attackDamage(Monster monster) {
        return (float) Math.max(2, monster.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    // Two beams pointing opposite ways from the elite's middle, sweeping slowly around it. Anyone standing in one is hurt
    private static void arcane(ServerLevel level, Monster monster) {
        int phase = monster.tickCount % BEAM_CYCLE;
        if (phase >= BEAM_ON) {
            return;
        }
        if (phase < 5) {
            level.playSound(null, monster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0f, 1.6f);
        }
        double angle = monster.tickCount * BEAM_TURN;
        Vec3 center = monster.position().add(0, 1, 0);
        float damage = attackDamage(monster) * 0.4f;
        for (int side = 0; side < 2; side++) {
            double a = angle + side * Math.PI;
            Vec3 direction = new Vec3(Math.cos(a), 0, Math.sin(a));
            for (double d = 0.8; d <= BEAM_LENGTH; d += 0.5) {
                Vec3 point = center.add(direction.scale(d));
                level.sendParticles(BEAM_DUST, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
            for (Player player : level.players()) {
                if (player instanceof ServerPlayer target && !target.isSpectator() && !target.isCreative() && inBeam(center, direction, target)) {
                    target.hurt(monster.damageSources().indirectMagic(monster, monster), damage);
                }
            }
        }
    }

    private static boolean inBeam(Vec3 center, Vec3 direction, Player player) {
        Vec3 offset = player.position().add(0, 1, 0).subtract(center);
        if (Math.abs(offset.y) > 1.5) {
            return false;
        }
        double along = offset.x * direction.x + offset.z * direction.z;
        if (along < 0 || along > BEAM_LENGTH) {
            return false;
        }
        double sideways = Math.abs(offset.x * direction.z - offset.z * direction.x);
        return sideways < BEAM_WIDTH;
    }

    // Marks a spot under a player who keeps away, makes it glow, and then blows it up. The spot is saved on the elite,
    // so it still goes off if the player runs
    private static void mortar(ServerLevel level, Monster monster, LivingEntity target) {
        CompoundTag data = monster.getPersistentData();
        if (data.contains(MORTAR_TAG)) {
            CompoundTag blast = data.getCompound(MORTAR_TAG);
            Vec3 spot = new Vec3(blast.getDouble("x"), blast.getDouble("y"), blast.getDouble("z"));
            if (monster.tickCount >= blast.getInt("due")) {
                data.remove(MORTAR_TAG);
                explode(level, monster, spot);
            } else {
                ring(level, spot);
            }
            return;
        }
        if (target != null && monster.tickCount % MORTAR_INTERVAL < 5 && monster.distanceTo(target) >= MORTAR_MIN_DISTANCE) {
            CompoundTag blast = new CompoundTag();
            blast.putDouble("x", target.getX());
            blast.putDouble("y", target.getY());
            blast.putDouble("z", target.getZ());
            blast.putInt("due", monster.tickCount + MORTAR_DELAY);
            data.put(MORTAR_TAG, blast);
            level.playSound(null, monster.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0f, 0.6f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, monster.getX(), monster.getY() + monster.getBbHeight(), monster.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            ring(level, new Vec3(target.getX(), target.getY(), target.getZ()));
        }
    }

    private static void ring(ServerLevel level, Vec3 spot) {
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            level.sendParticles(ParticleTypes.FLAME, spot.x + Math.cos(a) * MORTAR_RADIUS, spot.y + 0.1, spot.z + Math.sin(a) * MORTAR_RADIUS, 1, 0, 0, 0, 0);
        }
    }

    private static void explode(ServerLevel level, Monster monster, Vec3 spot) {
        level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.5, spot.z, 3, 0.8, 0.3, 0.8, 0);
        level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.3, spot.z, 10, 0.8, 0.2, 0.8, 0);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.3f);
        float damage = attackDamage(monster) * 1.2f;
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.position().distanceTo(spot) <= MORTAR_RADIUS + 0.5) {
                player.hurt(monster.damageSources().indirectMagic(monster, monster), damage);
            }
        }
    }

    // A pool of poison at the elite's feet that stays a few seconds
    private static void plague(ServerLevel level, Monster monster) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, monster.getX(), monster.getY(), monster.getZ());
        cloud.setOwner(monster);
        cloud.setRadius(2.2f);
        cloud.setRadiusOnUse(0);
        cloud.setRadiusPerTick(0);
        cloud.setDuration(6 * 20);
        cloud.setWaitTime(10);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 3 * 20, 1));
        level.addFreshEntity(cloud);
        level.playSound(null, monster.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.HOSTILE, 0.8f, 0.6f);
    }

    // Sometimes a hit on an Electrified elite sends lightning back at the player who did it
    static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Monster monster) || !(monster.level() instanceof ServerLevel level)
                || !EliteModifier.ELECTRIFIED.isOn(monster) || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || player.distanceTo(monster) > SPARK_RANGE || monster.getRandom().nextFloat() >= SPARK_CHANCE) {
            return;
        }
        Vec3 from = monster.position().add(0, monster.getBbHeight() / 2, 0);
        Vec3 to = player.position().add(0, 1, 0);
        int steps = (int) Math.ceil(from.distanceTo(to) * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 2, 0.08, 0.08, 0.08, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.4f, 1.8f);
        player.hurt(monster.damageSources().indirectMagic(monster, monster), attackDamage(monster) * 0.5f);
    }
}
