package com.morkeriget.elite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Every boss has its own ability, picked from its name, that it uses every INTERVAL ticks.
// The ones that hit a moment later (fire rain, the end of a charge) are remembered in PENDING until then
enum BossAbility {
    FIRE_RAIN("Ildregn", "kalder ild ned!"),
    RAISE_DEAD("Genopliv de døde", "vækker de døde!"),
    CROWS("Kragesværm", "sender sine krager!"),
    BLOOD_DRAIN("Livsdræn", "drikker dit liv!"),
    CHARGE("Angreb", "styrter frem!"),
    DARKNESS("Mørke", "slukker lyset!"),
    // Every boss uses this one too in its second phase, taking turns with its own, see Bosses
    DOOM_CIRCLES("Dødscirkler", "mærker jer til døden - kom væk fra cirklen!");

    // 20 ticks is one second
    static final int INTERVAL = 12 * 20;
    private static final int FIRE_RAIN_DELAY = 20;
    private static final int CHARGE_LANDING = 15;
    private static final double FIRE_RADIUS = 1.8;
    private static final double DRAIN_RADIUS = 8.0;
    private static final double DARKNESS_RADIUS = 16.0;
    private static final double CHARGE_HIT_RADIUS = 3.0;
    private static final double MESSAGE_RANGE = 32.0;
    // A red circle under each player that blows up a moment and a half later, so there is just time to step out
    private static final int DOOM_DELAY = 30;
    private static final double DOOM_RADIUS = 2.5;
    private static final DustParticleOptions DOOM_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 2.0f);

    private static final Map<String, BossAbility> BY_BOSS = Map.of(
            "Slagteren", CHARGE, "Gravkongen", RAISE_DEAD, "Blodmoderen", BLOOD_DRAIN, "Den Hule Ridder", CHARGE,
            "Askeherren", FIRE_RAIN, "Kragedronningen", CROWS, "Rådne Ragnar", RAISE_DEAD, "Mørkets Hyrde", DARKNESS,
            "Knogleknuseren", CHARGE, "Sjæleæderen", BLOOD_DRAIN);

    // Something that happens a little after the ability is used. Only in memory, so it is lost if the world is reloaded
    private record Pending(BossAbility ability, List<Vec3> spots, long time) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    final String displayName;
    // Shown to the players after the boss's name, e.g. "Askeherren kalder ild ned!"
    private final String warning;

    BossAbility(String displayName, String warning) {
        this.displayName = displayName;
        this.warning = warning;
    }

    static BossAbility forBoss(String name) {
        return BY_BOSS.getOrDefault(name, CHARGE);
    }

    static BossAbility byName(String name) {
        for (BossAbility ability : values()) {
            if (ability.name().equals(name)) {
                return ability;
            }
        }
        return null;
    }

    void use(ServerLevel level, Monster boss, LivingEntity target) {
        warn(level, boss);
        switch (this) {
            case FIRE_RAIN -> fireRain(level, boss, target);
            case RAISE_DEAD -> raiseDead(level, boss, target);
            case CROWS -> crows(level, boss, target);
            case BLOOD_DRAIN -> bloodDrain(level, boss);
            case CHARGE -> charge(level, boss, target);
            case DARKNESS -> darkness(level, boss);
            case DOOM_CIRCLES -> doomCircles(level, boss);
        }
    }

    // Runs every time the boss ticks, and does what is waiting once its time has come
    static void tickPending(ServerLevel level, Monster boss) {
        Pending pending = PENDING.get(boss.getUUID());
        if (pending == null) {
            return;
        }
        if (level.getGameTime() < pending.time) {
            // The circles are drawn again until they go off, so they can be seen the whole time
            if (pending.ability == DOOM_CIRCLES) {
                for (Vec3 spot : pending.spots) {
                    doomRing(level, spot);
                }
            }
            return;
        }
        PENDING.remove(boss.getUUID());
        switch (pending.ability) {
            case FIRE_RAIN -> {
                for (Vec3 spot : pending.spots) {
                    fireStrike(level, boss, spot);
                }
            }
            case CHARGE -> chargeLanding(level, boss);
            case DOOM_CIRCLES -> {
                for (Vec3 spot : pending.spots) {
                    doomStrike(level, boss, spot);
                }
            }
            default -> {
            }
        }
    }

    static void forget(UUID boss) {
        PENDING.remove(boss);
    }

    static void forgetAll() {
        PENDING.clear();
    }

    private void warn(ServerLevel level, Monster boss) {
        Component message = Component.empty().append(boss.getDisplayName())
                .append(Component.literal(" " + warning).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(boss) <= MESSAGE_RANGE) {
                player.displayClientMessage(message, true);
            }
        }
    }

    // Five burning rings appear on the ground, one right under the target, and a second later fire comes down in them
    private static void fireRain(ServerLevel level, Monster boss, LivingEntity target) {
        RandomSource random = boss.getRandom();
        List<Vec3> spots = new ArrayList<>();
        spots.add(target.position());
        for (int i = 0; i < 4; i++) {
            spots.add(target.position().add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8));
        }
        for (Vec3 spot : spots) {
            circle(level, spot, FIRE_RADIUS, ParticleTypes.FLAME);
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.6f);
        PENDING.put(boss.getUUID(), new Pending(FIRE_RAIN, spots, level.getGameTime() + FIRE_RAIN_DELAY));
    }

    private static void fireStrike(ServerLevel level, Monster boss, Vec3 spot) {
        level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.2, spot.z, 12, 0.5, 0.2, 0.5, 0);
        level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 1.5, spot.z, 30, 0.3, 1.5, 0.3, 0.02);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0f, 0.8f);
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6f;
        for (Player player : players(level, spot, FIRE_RADIUS)) {
            player.hurt(boss.damageSources().mobAttack(boss), damage);
            player.igniteForSeconds(3);
        }
    }

    // Three husks climb up out of the ground
    private static void raiseDead(ServerLevel level, Monster boss, LivingEntity target) {
        Bosses.summon(level, boss, target, EntityType.HUSK, 3, minion -> {
        });
        level.playSound(null, boss.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    // Four flying vexes that go away again after 15 seconds
    private static void crows(ServerLevel level, Monster boss, LivingEntity target) {
        Bosses.summon(level, boss, target, EntityType.VEX, 4, minion -> {
            if (minion instanceof Vex vex) {
                vex.setOwner(boss);
                vex.setLimitedLife(15 * 20);
                vex.moveTo(vex.getX(), vex.getY() + 1.5, vex.getZ());
            }
        });
        level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5f, 1.4f);
    }

    // Takes life from every player close by and heals the boss twice as much
    private static void bloodDrain(ServerLevel level, Monster boss) {
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4f;
        for (Player player : players(level, boss.position(), DRAIN_RADIUS)) {
            player.hurt(boss.damageSources().mobAttack(boss), damage);
            boss.heal(damage * 2);
            // A line of red particles from the player to the boss
            Vec3 from = player.position().add(0, 1, 0);
            Vec3 step = boss.position().add(0, boss.getBbHeight() / 2, 0).subtract(from);
            for (int i = 0; i <= 10; i++) {
                Vec3 point = from.add(step.scale(i / 10.0));
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 1.0f);
    }

    // Jumps at the target and hits everything around it when it lands
    private static void charge(ServerLevel level, Monster boss, LivingEntity target) {
        Vec3 direction = target.position().subtract(boss.position());
        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        if (flat.lengthSqr() < 0.01) {
            return;
        }
        // Further away means a longer jump
        double speed = Math.min(2.0, 0.6 + direction.horizontalDistance() * 0.12);
        flat = flat.normalize();
        boss.setDeltaMovement(flat.x * speed, 0.5, flat.z * speed);
        boss.hasImpulse = true;
        level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.2, boss.getZ(), 20, 0.5, 0.1, 0.5, 0.05);
        level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 1.2f);
        PENDING.put(boss.getUUID(), new Pending(CHARGE, List.of(), level.getGameTime() + CHARGE_LANDING));
    }

    private static void chargeLanding(ServerLevel level, Monster boss) {
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8f;
        for (Player player : players(level, boss.position(), CHARGE_HIT_RADIUS)) {
            player.hurt(boss.damageSources().mobAttack(boss), damage);
            Vec3 push = player.position().subtract(boss.position()).normalize();
            player.push(push.x * 1.0, 0.4, push.z * 1.0);
            player.hurtMarked = true;
        }
        circle(level, boss.position(), CHARGE_HIT_RADIUS, ParticleTypes.EXPLOSION);
        level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.2f);
    }

    // Darkness and slowness on every player close by
    private static void darkness(ServerLevel level, Monster boss) {
        for (Player player : players(level, boss.position(), DARKNESS_RADIUS)) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 6 * 20, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * 20, 0));
        }
        circle(level, boss.position(), 4, ParticleTypes.SQUID_INK);
        level.playSound(null, boss.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 2.0f, 0.7f);
    }

    // Marks where every player close by stands
    private static void doomCircles(ServerLevel level, Monster boss) {
        List<Vec3> spots = new ArrayList<>();
        for (Player player : players(level, boss.position(), MESSAGE_RANGE)) {
            spots.add(player.position());
        }
        for (Vec3 spot : spots) {
            doomRing(level, spot);
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.HOSTILE, 1.5f, 0.7f);
        PENDING.put(boss.getUUID(), new Pending(DOOM_CIRCLES, spots, level.getGameTime() + DOOM_DELAY));
    }

    private static void doomRing(ServerLevel level, Vec3 center) {
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2 * i / 36;
            level.sendParticles(DOOM_DUST, center.x + Math.cos(angle) * DOOM_RADIUS, center.y + 0.1, center.z + Math.sin(angle) * DOOM_RADIUS,
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(DOOM_DUST, center.x, center.y + 0.1, center.z, 14, DOOM_RADIUS / 3, 0, DOOM_RADIUS / 3, 0);
    }

    private static void doomStrike(ServerLevel level, Monster boss, Vec3 spot) {
        level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.5, spot.z, 3, 0.8, 0.2, 0.8, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spot.x, spot.y + 0.3, spot.z, 25, 1.0, 0.3, 1.0, 0.05);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 0.8f);
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9f;
        for (Player player : players(level, spot, DOOM_RADIUS)) {
            player.hurt(boss.damageSources().mobAttack(boss), damage);
        }
    }

    // Players that can be hurt within the radius of the spot
    private static List<Player> players(ServerLevel level, Vec3 spot, double radius) {
        return level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(radius),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative() && player.position().distanceTo(spot) <= radius);
    }

    private static void circle(ServerLevel level, Vec3 center, double radius, ParticleOptions particle) {
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + 0.1, center.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }
}
