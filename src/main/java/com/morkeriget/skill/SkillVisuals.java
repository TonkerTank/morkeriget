package com.morkeriget.skill;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Shapes and colours drawn with particles, so the skills look alike. Only for looks, nothing here changes what a
// skill does. Server only
final class SkillVisuals {
    static final DustParticleOptions FROST = new DustParticleOptions(new Vector3f(0.6f, 0.85f, 1.0f), 1.3f);
    static final DustParticleOptions FIRE = new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.08f), 1.6f);
    static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7f, 0.05f, 0.05f), 1.5f);
    static final DustParticleOptions BONE_DUST = new DustParticleOptions(new Vector3f(0.95f, 0.93f, 0.85f), 1.2f);
    static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.08f, 0.08f, 0.1f), 2.0f);
    static final DustParticleOptions RAGE = new DustParticleOptions(new Vector3f(0.9f, 0.12f, 0.08f), 1.2f);
    static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.25f), 1.2f);
    static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.75f, 0.85f, 1.0f), 1.0f);
    static final DustParticleOptions IRON = new DustParticleOptions(new Vector3f(0.55f, 0.55f, 0.6f), 0.9f);
    static final DustParticleOptions MANA = new DustParticleOptions(new Vector3f(0.3f, 0.45f, 1.0f), 1.2f);
    static final DustParticleOptions LIFE = new DustParticleOptions(new Vector3f(1.0f, 0.3f, 0.35f), 1.2f);
    static final DustParticleOptions ARCANE = new DustParticleOptions(new Vector3f(0.7f, 0.35f, 1.0f), 1.2f);
    // Colours that fade from one to the other while they live
    static final DustColorTransitionOptions FROST_FADE = new DustColorTransitionOptions(
            new Vector3f(1.0f, 1.0f, 1.0f), new Vector3f(0.35f, 0.65f, 1.0f), 1.5f);
    static final DustColorTransitionOptions FIRE_FADE = new DustColorTransitionOptions(
            new Vector3f(1.0f, 0.6f, 0.1f), new Vector3f(0.25f, 0.2f, 0.2f), 2.0f);
    static final DustColorTransitionOptions BLOOD_FADE = new DustColorTransitionOptions(
            new Vector3f(0.85f, 0.1f, 0.1f), new Vector3f(0.3f, 0.0f, 0.0f), 1.5f);
    static final DustColorTransitionOptions CURSE_FADE = new DustColorTransitionOptions(
            new Vector3f(0.5f, 0.1f, 0.65f), new Vector3f(0.2f, 0.6f, 0.2f), 1.5f);
    static final DustColorTransitionOptions EARTH_FADE = new DustColorTransitionOptions(
            new Vector3f(0.55f, 0.4f, 0.25f), new Vector3f(0.3f, 0.25f, 0.2f), 2.0f);

    // Game time until a buff's glow is shown, written when the skill is used
    static final String FRENZY_UNTIL_TAG = "morkeriget_frenzy_visual_until";
    static final String BATTLE_CRY_UNTIL_TAG = "morkeriget_battle_cry_visual_until";
    static final String SMOKE_BOMB_UNTIL_TAG = "morkeriget_smoke_bomb_visual_until";
    static final String BONE_ARMOR_UNTIL_TAG = "morkeriget_bone_armor_visual_until";

    private SkillVisuals() {
    }

    // A flat ring of particles that stay where they are
    static void ring(ServerLevel level, Vec3 center, double y, double radius, int points, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + y, center.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    // A flat ring that flies outwards from the center, so it grows by itself (count 0 means the offsets are the speed)
    static void burst(ServerLevel level, Vec3 center, double y, int points, double speed, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x, center.y + y, center.z, 0, Math.cos(angle), 0, Math.sin(angle), speed);
        }
    }

    // A ring that starts out wide and flies in towards the center
    static void implode(ServerLevel level, Vec3 center, double y, double radius, int points, double speed, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + y, center.z + Math.sin(angle) * radius,
                    0, -Math.cos(angle), 0, -Math.sin(angle), speed);
        }
    }

    // Rings at a few radii out to the edge, like a shockwave. Bigger rings get more points
    static void shockwave(ServerLevel level, Vec3 center, double y, double radius, int rings, ParticleOptions particle) {
        for (int ring = 1; ring <= rings; ring++) {
            double r = radius * ring / rings;
            ring(level, center, y, r, Math.max(8, (int) (r * 6)), particle);
        }
    }

    // Particles winding up around a point, like a swirl around the player
    static void spiral(ServerLevel level, Vec3 center, double radius, double height, double turns, int points, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = Math.PI * 2 * turns * t;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + height * t, center.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    // A straight line of particles between two points
    static void line(ServerLevel level, Vec3 from, Vec3 to, double spacing, ParticleOptions particle) {
        Vec3 step = to.subtract(from);
        int count = Math.max(1, (int) Math.ceil(step.length() / spacing));
        for (int i = 0; i <= count; i++) {
            Vec3 point = from.add(step.scale((double) i / count));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    // A jagged line like a bolt of lightning, with a bright core and a glow around it
    static void zigzag(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions core, ParticleOptions glow) {
        RandomSource random = level.getRandom();
        int segments = Math.max(2, (int) (to.distanceTo(from) / 1.2));
        Vec3 last = from;
        for (int i = 1; i <= segments; i++) {
            Vec3 next = from.lerp(to, (double) i / segments);
            if (i < segments) {
                next = next.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
            }
            line(level, last, next, 0.4, core);
            Vec3 middle = last.lerp(next, 0.5);
            level.sendParticles(glow, middle.x, middle.y, middle.z, 2, 0.1, 0.1, 0.1, 0);
            last = next;
        }
    }

    // The glow of the buffs that last a while, a few particles every other tick. Called from ClassSkills.onPlayerTick
    static void buffAuras(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now % 2 != 0) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        // Two points going round you, half a turn apart
        double angle = now * 0.35;
        if (data.getLong(FRENZY_UNTIL_TAG) > now && player.hasEffect(MobEffects.DIG_SPEED)) {
            level.sendParticles(RAGE, x, y + 1, z, 2, 0.35, 0.5, 0.35, 0);
            if (now % 6 == 0) {
                level.sendParticles(ParticleTypes.CRIT, x, y + 1, z, 2, 0.3, 0.4, 0.3, 0.1);
            }
        }
        if (data.getLong(BATTLE_CRY_UNTIL_TAG) > now && player.hasEffect(MobEffects.DAMAGE_BOOST)) {
            for (int i = 0; i < 2; i++) {
                double a = angle + Math.PI * i;
                level.sendParticles(GOLD, x + Math.cos(a) * 0.8, y + 0.2, z + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
            }
            if (now % 8 == 0) {
                level.sendParticles(ParticleTypes.FLAME, x, y + 0.1, z, 1, 0.3, 0, 0.3, 0.01);
            }
        }
        if (data.getLong(SMOKE_BOMB_UNTIL_TAG) > now && player.hasEffect(MobEffects.INVISIBILITY)) {
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 2, 0.25, 0.1, 0.25, 0.01);
            level.sendParticles(SHADOW, x, y + 0.5, z, 1, 0.3, 0.3, 0.3, 0);
        }
        if (data.getLong(BONE_ARMOR_UNTIL_TAG) > now && player.getAbsorptionAmount() > 0) {
            for (int i = 0; i < 3; i++) {
                double a = -angle + Math.PI * 2 * i / 3;
                level.sendParticles(BONE_DUST, x + Math.cos(a) * 0.9, y + 1 + Math.sin(now * 0.2 + i) * 0.4, z + Math.sin(a) * 0.9,
                        1, 0, 0, 0, 0);
            }
        }
    }
}
