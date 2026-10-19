package com.morkeriget.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

// The skills in the third row of the skill trees: the barbarian's Earthquake and Rush, the sorcerer's Meteor and Ice
// Armor, the ranger's Explosive Arrow and Bear Trap, and the necromancer's Blood Nova and Bone Prison. Also the big
// skill of each class in the fourth row: Doom Slam, Thunderstorm, Arrow Hurricane and Army of the Dead. Server only,
// called from Skills.use
final class TreeSkills {
    // 20 ticks is one second
    private static final double EARTHQUAKE_RADIUS = 4.0;
    private static final int EARTHQUAKE_PULSES = 6;
    private static final int EARTHQUAKE_PULSE_TIME = 10;
    private static final double RUSH_DISTANCE = 8.0;
    private static final double METEOR_RADIUS = 4.0;
    private static final int METEOR_DELAY = 30;
    private static final int ICE_ARMOR_DURATION = 8 * 20;
    private static final String ICE_ARMOR_UNTIL_TAG = "morkeriget_ice_armor_until";
    private static final String EXPLOSIVE_TAG = "morkeriget_explosive_arrow";
    private static final double EXPLOSIVE_RADIUS = 3.0;
    // Before the bow's level, the ranger bonus and Marksman, like Arrow Rain
    private static final double EXPLOSIVE_DAMAGE = 6.0;
    private static final double BEAR_TRAP_DAMAGE = 8.0;
    private static final double BEAR_TRAP_RADIUS = 1.5;
    private static final int BEAR_TRAP_LIFETIME = 15 * 20;
    private static final int BEAR_TRAP_HOLD = 3 * 20;
    private static final int BEAR_TRAP_CHECK = 5;
    private static final double BLOOD_NOVA_RADIUS = 5.0;
    private static final double BONE_PRISON_RADIUS = 4.0;
    private static final int BONE_PRISON_DURATION = 3 * 20;
    // Thunderstorm: dark clouds over the area, and a bolt every STORM_BOLT_TIME. It starts after the clouds have gathered
    private static final double STORM_RADIUS = 6.0;
    private static final double STORM_BOLT_RADIUS = 2.0;
    private static final double STORM_HEIGHT = 10.0;
    private static final int STORM_BOLTS = 8;
    private static final int STORM_BOLT_TIME = 6;
    private static final int STORM_GATHER = 15;
    private static final DustParticleOptions STORM_CLOUD = new DustParticleOptions(new Vector3f(0.18f, 0.18f, 0.24f), 3.0f);
    private static final BlockParticleOption BONE =new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
    private static final BlockParticleOption DIRT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7f, 0.05f, 0.05f), 1.5f);
    private static final BlockParticleOption STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState());
    private static final BlockParticleOption ICE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
    private static final BlockParticleOption MAGMA = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState());

    // Something a skill left behind that happens later: earthquake pulses, a falling meteor, a trap waiting for a monster
    private enum Kind {
        EARTHQUAKE, METEOR, BEAR_TRAP, THUNDERSTORM, DOOM_SLAM_FALL, DOOM_SLAM_WAVE, ARROW_HURRICANE, ARMY_OF_THE_DEAD
    }

    // Doom Slam: you jump up, and when you land three shockwaves roll out, each further than the last
    private static final double DOOM_SLAM_JUMP = 0.9;
    private static final int DOOM_SLAM_WAVES = 3;
    private static final double DOOM_SLAM_WAVE_WIDTH = 3.0;
    private static final int DOOM_SLAM_WAVE_TIME = 4;
    // Lands on its own after this, e.g. if you jumped onto a ledge and never "landed"
    private static final int DOOM_SLAM_MAX_AIR = 40;
    // Arrow Hurricane: two arrows at the monsters around you every HURRICANE_TIME
    private static final double HURRICANE_RANGE = 16.0;
    private static final int HURRICANE_VOLLEYS = 10;
    private static final int HURRICANE_TIME = 5;
    private static final double HURRICANE_ARROW_DAMAGE = 2.0;
    private static final String HURRICANE_TAG = "morkeriget_hurricane_arrow";
    // Army of the Dead: arms of bone come up under the monsters in the area, one every ARMY_TIME
    private static final double ARMY_RADIUS = 7.0;
    private static final double ARMY_HIT_RADIUS = 1.8;
    private static final int ARMY_ARMS = 10;
    private static final int ARMY_TIME = 4;

    private record Effect(Kind kind, Vec3 center, float damage, int left, long next, long until) {
    }

    // Each player can have several effects going at once, e.g. two traps
    private static final Map<UUID, List<Effect>> EFFECTS = new HashMap<>();

    private TreeSkills() {
    }

    private static void add(ServerPlayer player, Effect effect) {
        EFFECTS.computeIfAbsent(player.getUUID(), uuid -> new ArrayList<>()).add(effect);
    }

    private static float attackDamage(ServerPlayer player, double scale, Upgrade upgrade, double perRank) {
        return (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * scale * (1 + perRank * Progression.rank(player, upgrade)));
    }

    // The ground in front of you shakes for three seconds and hurts everything standing on it
    static boolean earthquake(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        Vec3 center = flat.lengthSqr() < 0.01 ? player.position() : player.position().add(flat.normalize().scale(3));
        // Each Earthquake rank adds 20% damage
        float damage = attackDamage(player, 0.6, Upgrade.EARTHQUAKE, 0.2);
        // Aftershock makes it go on twice as long
        int pulses = LegendaryPower.has(player, LegendaryPower.AFTERSHOCK) ? EARTHQUAKE_PULSES * 2 : EARTHQUAKE_PULSES;
        add(player, new Effect(Kind.EARTHQUAKE, center, damage, pulses, level.getGameTime(), 0));
        // You stamp the ground, and a crack of dust runs from your feet to the middle of it
        level.sendParticles(DIRT, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.5, 0.05, 0.5, 0.2);
        SkillVisuals.burst(level, player.position(), 0.1, 12, 0.25, ParticleTypes.POOF);
        SkillVisuals.line(level, player.position().add(0, 0.1, 0), center.add(0, 0.1, 0), 0.4, ParticleTypes.DUST_PLUME);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.5f);
        return true;
    }

    // You run forward fast and hit every monster on the way
    static boolean rush(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 0.01) {
            return false;
        }
        flat = flat.normalize();
        Vec3 start = player.position().add(0, 0.5, 0);
        Vec3 end = level.clip(new ClipContext(start, start.add(flat.scale(RUSH_DISTANCE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        // Each Rush rank adds 20% damage
        float damage = attackDamage(player, 1.5, Upgrade.RUSH, 0.2);
        List<LivingEntity> hit = new ArrayList<>();
        double distance = end.distanceTo(start);
        // A burst of dust behind you as you push off
        level.sendParticles(ParticleTypes.POOF, start.x - flat.x * 0.5, start.y, start.z - flat.z * 0.5, 10, 0.3, 0.2, 0.3, 0.05);
        int steps = 0;
        for (double travelled = 0; travelled <= distance; travelled += 0.5) {
            Vec3 point = start.add(flat.scale(travelled));
            level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 1, 0.2, 0.1, 0.2, 0);
            // Dust kicked up along the ground and air cut to the sides
            level.sendParticles(ParticleTypes.DUST_PLUME, point.x, point.y - 0.4, point.z, 1, 0.2, 0, 0.2, 0.01);
            if (steps++ % 3 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y + 0.5, point.z, 1, 0, 0, 0, 0);
            }
            for (LivingEntity target : ClassSkills.enemies(level, point, 1.2)) {
                if (!hit.contains(target)) {
                    hit.add(target);
                    target.hurt(player.damageSources().playerAttack(player), damage);
                    // Thrown to the side, out of the way
                    target.knockback(0.6, flat.z, -flat.x);
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                            10, 0.3, 0.3, 0.3, 0.3);
                }
            }
        }
        // Fast enough to get there in about half a second
        player.setDeltaMovement(flat.x * Math.min(2.2, distance / 3.5), 0.15, flat.z * Math.min(2.2, distance / 3.5));
        player.hurtMarked = true;
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
        if (!hit.isEmpty()) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        return true;
    }

    // A burning circle where you aim, and a moment later a meteor comes down in it
    static boolean meteor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = ClassSkills.arrowRainTarget(level, player);
        // The sorcerer's big hit: three Fireballs' worth, since it is slow to come down and costs a lot.
        // Each Meteor rank adds 20% damage
        float damage = attackDamage(player, 6.0, Upgrade.METEOR, 0.2);
        add(player, new Effect(Kind.METEOR, center, damage, 1, level.getGameTime() + METEOR_DELAY, 0));
        meteorMark(level, center);
        // Meteor Shower: two more a little after, a few blocks to the sides
        if (LegendaryPower.has(player, LegendaryPower.METEOR_SHOWER)) {
            for (int i = 0; i < 2; i++) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                Vec3 spot = center.add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
                add(player, new Effect(Kind.METEOR, spot, damage, 1, level.getGameTime() + METEOR_DELAY + 10 * (i + 1), 0));
                meteorMark(level, spot);
            }
        }
        // Fire gathering in your hands
        SkillVisuals.spiral(level, player.position(), 0.7, 2.0, 2, 20, ParticleTypes.FLAME);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    // The burning circle where a meteor will come down, and the glow in the sky above it
    private static void meteorMark(ServerLevel level, Vec3 center) {
        circle(level, center, METEOR_RADIUS, ParticleTypes.FLAME);
        SkillVisuals.ring(level, center, 0.15, METEOR_RADIUS, 24, SkillVisuals.FIRE_FADE);
        SkillVisuals.ring(level, center, 0.15, METEOR_RADIUS * 0.5, 12, SkillVisuals.FIRE);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.2, center.z, 10, METEOR_RADIUS / 3, 0.05, METEOR_RADIUS / 3, 0.01);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 10, center.z, 10, 0.4, 0.4, 0.4, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 10, center.z, 6, 0.5, 0.5, 0.5, 0.01);
    }

    // Extra hearts of ice for a while, and every monster that hits you freezes
    static boolean iceArmor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Ice Armor rank makes it last one second longer
        int duration = ICE_ARMOR_DURATION + 20 * Upgrade.utility(Progression.rank(player, Upgrade.ICE_ARMOR));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 1));
        player.getPersistentData().putLong(ICE_ARMOR_UNTIL_TAG, level.getGameTime() + duration);
        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
        // Frost winding up around you, ice shards pulled in to make the armour, and a cold glint
        SkillVisuals.spiral(level, player.position(), 1.0, 2.2, 2, 30, SkillVisuals.FROST_FADE);
        SkillVisuals.implode(level, player.position(), 1.0, 2.0, 12, 0.15, ParticleTypes.SNOWFLAKE);
        level.sendParticles(ICE, player.getX(), player.getY() + 1, player.getZ(), 15, 0.4, 0.6, 0.4, 0);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    // Frost going round you while Ice Armor lasts
    private static void iceArmorGlow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now % 2 != 0 || player.getPersistentData().getLong(ICE_ARMOR_UNTIL_TAG) < now) {
            return;
        }
        double angle = now * 0.3;
        for (int i = 0; i < 2; i++) {
            double a = angle + Math.PI * i;
            level.sendParticles(SkillVisuals.FROST, player.getX() + Math.cos(a) * 0.8, player.getY() + 1 + Math.sin(now * 0.15) * 0.5,
                    player.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
        }
        if (now % 6 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 2, 0.4, 0.6, 0.4, 0.01);
        }
    }

    // Ice Armor freezes whoever hits you
    static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == player || player.getPersistentData().getLong(ICE_ARMOR_UNTIL_TAG) < player.serverLevel().getGameTime()) {
            return;
        }
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2 * 20, 9));
        attacker.setTicksFrozen(attacker.getTicksRequiredToFreeze() + 2 * 20 * 2);
        player.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        player.serverLevel().sendParticles(ICE, attacker.getX(), attacker.getY() + attacker.getBbHeight() / 2, attacker.getZ(), 8, 0.3, 0.4, 0.3, 0);
        player.serverLevel().playSound(null, attacker.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    // One arrow that explodes where it hits, also on the ground
    static boolean explosiveArrow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Arrow arrow = ClassSkills.arrow(level, player, 2.0);
        arrow.getPersistentData().putBoolean(EXPLOSIVE_TAG, true);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 3.0f, 0.5f);
        level.addFreshEntity(arrow);
        Vec3 bow = player.getEyePosition().add(player.getLookAngle().scale(1.0)).subtract(0, 0.2, 0);
        level.sendParticles(ParticleTypes.FLAME, bow.x, bow.y, bow.z, 6, 0.1, 0.1, 0.1, 0.03);
        level.sendParticles(ParticleTypes.SMOKE, bow.x, bow.y, bow.z, 4, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
        return true;
    }

    // Fire and smoke behind an Explosive Arrow while it flies. Called from ClassSkills.onEntityTick
    static void explosiveTrail(ServerLevel level, Arrow arrow) {
        if (arrow.getPersistentData().getBoolean(EXPLOSIVE_TAG)) {
            level.sendParticles(ParticleTypes.FLAME, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.05, 0.05, 0.05, 0);
            level.sendParticles(ParticleTypes.SMOKE, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
        // Arrow Hurricane's arrows leave a gold streak while they fly, and stop once they are stuck in something
        if (arrow.getPersistentData().getBoolean(HURRICANE_TAG) && arrow.getDeltaMovement().lengthSqr() > 0.05) {
            Vec3 back = arrow.position().subtract(arrow.getDeltaMovement().scale(0.5));
            SkillVisuals.line(level, back, arrow.position(), 0.4, SkillVisuals.GOLD);
            level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
        }
    }

    static void onProjectileImpact(ProjectileImpactEvent event) {
        // A burst of gold where an Arrow Hurricane arrow hits. The arrow itself works like any other
        if (event.getProjectile() instanceof Arrow hurricane && hurricane.getPersistentData().getBoolean(HURRICANE_TAG)
                && hurricane.level() instanceof ServerLevel level) {
            Vec3 hit = event.getRayTraceResult().getLocation();
            // Two arrows a volley at the same monster would otherwise bounce off while it is still flashing from the last
            if (event.getRayTraceResult() instanceof EntityHitResult entityHit) {
                entityHit.getEntity().invulnerableTime = 0;
            }
            level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 10, 0.2, 0.2, 0.2, 0.3);
            level.sendParticles(SkillVisuals.GOLD, hit.x, hit.y, hit.z, 8, 0.25, 0.25, 0.25, 0);
            hurricane.getPersistentData().remove(HURRICANE_TAG);
        }
        if (!(event.getProjectile() instanceof Arrow arrow) || !arrow.getPersistentData().getBoolean(EXPLOSIVE_TAG)
                || !(arrow.level() instanceof ServerLevel level) || !(arrow.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        Vec3 center = event.getRayTraceResult().getLocation();
        // Each Explosive Arrow rank adds 15% damage
        float damage = (float) (EXPLOSIVE_DAMAGE * ClassSkills.bowScale(player) * (1 + 0.15 * Progression.rank(player, Upgrade.EXPLOSIVE_ARROW)));
        explode(level, player, center, EXPLOSIVE_RADIUS, damage);
        // Cluster Bomb: three smaller ones around it, half as strong
        if (LegendaryPower.has(player, LegendaryPower.CLUSTER_BOMB)) {
            for (int i = 0; i < 3; i++) {
                double angle = Math.PI * 2 * i / 3;
                explode(level, player, center.add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3), EXPLOSIVE_RADIUS * 0.7, damage * 0.5f);
            }
        }
        // The explosion did the damage, so the arrow itself goes away
        arrow.discard();
        event.setCanceled(true);
    }

    private static void explode(ServerLevel level, ServerPlayer player, Vec3 center, double radius, float damage) {
        for (LivingEntity target : ClassSkills.enemies(level, center, radius)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, radius / 5, 0.4, radius / 5, 0);
        // Fire thrown out in a ring, smoke going up and a scorched edge
        SkillVisuals.burst(level, center, 0.2, 20, 0.3, ParticleTypes.FLAME);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.5, center.z, 10, radius / 4, 0.4, radius / 4, 0.02);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.3, center.z, 6, 0.4, 0.2, 0.4, 0);
        SkillVisuals.ring(level, center, 0.1, radius, 16, SkillVisuals.FIRE_FADE);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 0.6f);
    }

    // A trap on the ground where you aim. The first monster that steps in it gets hurt and can't move for a while
    static boolean bearTrap(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = ClassSkills.arrowRainTarget(level, player);
        // Each Bear Trap rank adds 15% damage
        float damage = (float) (BEAR_TRAP_DAMAGE * ClassSkills.bowScale(player) * (1 + 0.15 * Progression.rank(player, Upgrade.BEAR_TRAP)));
        long now = level.getGameTime();
        add(player, new Effect(Kind.BEAR_TRAP, center, damage, 1, now, now + BEAR_TRAP_LIFETIME));
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y + 0.2, center.z, 10, 0.3, 0.05, 0.3, 0);
        // The iron jaws as two rings on the ground, and dirt thrown up where it lands
        trapJaws(level, center);
        level.sendParticles(DIRT, center.x, center.y + 0.1, center.z, 10, 0.3, 0.05, 0.3, 0.1);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.7f, 1.4f);
        return true;
    }

    private static void trapJaws(ServerLevel level, Vec3 center) {
        SkillVisuals.ring(level, center, 0.1, 0.7, 12, SkillVisuals.IRON);
        SkillVisuals.ring(level, center, 0.15, 0.4, 8, SkillVisuals.IRON);
    }

    // Blood bursts out of you, hurts everything close by and heals you a little for every one it hits
    static boolean bloodNova(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Blood Nova rank adds 20% damage
        float damage = attackDamage(player, 1.4, Upgrade.BLOOD_NOVA, 0.2);
        List<LivingEntity> targets = ClassSkills.enemies(level, player.position(), BLOOD_NOVA_RADIUS);
        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), damage);
        }
        // 3% of your max health for each, at most 30%
        player.heal(player.getMaxHealth() * Math.min(0.3f, 0.03f * targets.size()));
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2 * i / 40;
            level.sendParticles(BLOOD, player.getX() + Math.cos(angle) * 2, player.getY() + 1, player.getZ() + Math.sin(angle) * 2,
                    1, Math.cos(angle) * 0.5, 0, Math.sin(angle) * 0.5, 0);
        }
        // A wave of blood out to the edge, and from every one it hit a stream of blood back to you
        Vec3 center = player.position();
        SkillVisuals.shockwave(level, center, 0.6, BLOOD_NOVA_RADIUS, 2, SkillVisuals.BLOOD_FADE);
        level.sendParticles(SkillVisuals.BLOOD_FADE, player.getX(), player.getY() + 1, player.getZ(), 15, 0.3, 0.5, 0.3, 0);
        for (LivingEntity target : targets) {
            level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 6, 0.25, 0.3, 0.25, 0);
            SkillVisuals.line(level, target.position().add(0, target.getBbHeight() / 2, 0), center.add(0, 1, 0), 0.6, BLOOD);
        }
        if (!targets.isEmpty()) {
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2, player.getZ(), 2, 0.3, 0.1, 0.3, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    // Walls of bone come up where you aim and hold every monster there
    static boolean bonePrison(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = ClassSkills.arrowRainTarget(level, player);
        // Each Bone Prison rank holds them 0.3 seconds longer
        int duration = BONE_PRISON_DURATION + 6 * Upgrade.utility(Progression.rank(player, Upgrade.BONE_PRISON));
        boolean cage = LegendaryPower.has(player, LegendaryPower.DEATHS_CAGE);
        for (LivingEntity target : ClassSkills.enemies(level, center, BONE_PRISON_RADIUS)) {
            hold(target, duration);
            // Death's Cage: the same curse as the Curse skill
            if (cage) {
                NecroSkills.applyCurse(level, target, player, NecroSkills.curseBonus(player));
            }
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8;
                level.sendParticles(BONE, target.getX() + Math.cos(angle) * 0.8, target.getY() + 0.5, target.getZ() + Math.sin(angle) * 0.8,
                        6, 0.05, 0.5, 0.05, 0);
            }
            // The bars closing over its head, and the dust they threw up
            SkillVisuals.ring(level, target.position(), target.getBbHeight() + 0.2, 0.5, 8, SkillVisuals.BONE_DUST);
            level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY() + 0.1, target.getZ(), 6, 0.5, 0.05, 0.5, 0.02);
        }
        circle(level, center, BONE_PRISON_RADIUS, BONE);
        SkillVisuals.ring(level, center, 0.1, BONE_PRISON_RADIUS, 24, SkillVisuals.BONE_DUST);
        SkillVisuals.implode(level, center, 0.3, BONE_PRISON_RADIUS, 16, 0.25, ParticleTypes.SOUL);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.5, center.z, 8, 1.0, 0.3, 1.0, 0.02);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    // Dark clouds gather where you aim, and then lightning comes down on the monsters under them, one bolt at a time
    static boolean thunderstorm(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = ClassSkills.arrowRainTarget(level, player);
        // Each Thunderstorm rank adds 20% damage, and every second rank a bolt
        int rank = Progression.rank(player, Upgrade.THUNDERSTORM);
        float damage = attackDamage(player, 1.6, Upgrade.THUNDERSTORM, 0.2);
        int bolts = STORM_BOLTS + Upgrade.utility(rank) / 2;
        add(player, new Effect(Kind.THUNDERSTORM, center, damage, bolts, level.getGameTime() + STORM_GATHER, 0));
        stormClouds(level, center, 60);
        circle(level, center, STORM_RADIUS, ParticleTypes.ELECTRIC_SPARK);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    private static void stormClouds(ServerLevel level, Vec3 center, int count) {
        level.sendParticles(STORM_CLOUD, center.x, center.y + STORM_HEIGHT, center.z, count, STORM_RADIUS / 2, 0.4, STORM_RADIUS / 2, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + STORM_HEIGHT, center.z, count / 3, STORM_RADIUS / 2, 0.3, STORM_RADIUS / 2, 0.01);
    }

    // One bolt on a random monster under the clouds, or a random spot if there are none
    private static void lightningStrike(ServerLevel level, ServerPlayer player, Effect effect) {
        List<LivingEntity> under = ClassSkills.enemies(level, effect.center, STORM_RADIUS);
        Vec3 spot;
        if (!under.isEmpty()) {
            spot = under.get(player.getRandom().nextInt(under.size())).position();
        } else {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double distance = player.getRandom().nextDouble() * STORM_RADIUS;
            spot = effect.center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        }
        bolt(level, spot);
        for (LivingEntity target : ClassSkills.enemies(level, spot, STORM_BOLT_RADIUS)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), effect.damage);
            // Stunned for a moment by the shock
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 4));
        }
        stormClouds(level, effect.center, 12);
    }

    // The bolt coming down on the spot, with a flash, sparks, soul fire and a crack where it hits. Vanilla's lightning
    // bolt isn't used, since it would be heard all over the world and set things on fire
    private static void bolt(ServerLevel level, Vec3 spot) {
        RandomSource random = level.getRandom();
        // The bolt itself is a StormBolt, drawn on every client by StormBoltRenderer. It is only there to be seen
        StormBolt bolt = SkillEntities.STORM_BOLT.get().create(level);
        if (bolt != null) {
            bolt.moveTo(spot.x, spot.y, spot.z);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.FLASH, spot.x, spot.y + 0.5, spot.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spot.x, spot.y + 0.2, spot.z, 25, 0.6, 0.2, 0.6, 0.3);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spot.x, spot.y + 0.2, spot.z, 14, 0.5, 0.15, 0.5, 0.04);
        level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.1, spot.z, 6, 0.4, 0.05, 0.4, 0.02);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f, 0.9f + random.nextFloat() * 0.3f);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    // The barbarian jumps high, and slams down so hard the ground throws every monster around into the air
    static boolean doomSlam(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Doom Slam rank adds 20% damage
        float damage = attackDamage(player, 3.0, Upgrade.DOOM_SLAM, 0.2);
        player.setDeltaMovement(player.getDeltaMovement().x * 0.3, DOOM_SLAM_JUMP, player.getDeltaMovement().z * 0.3);
        player.hurtMarked = true;
        long now = level.getGameTime();
        // Waits a few ticks before checking for the ground, since you are still on it right when you jump
        add(player, new Effect(Kind.DOOM_SLAM_FALL, player.position(), damage, 1, now + 5, now + DOOM_SLAM_MAX_AIR));
        SkillVisuals.ring(level, player.position(), 0.1, 1.5, 20, ParticleTypes.CLOUD);
        SkillVisuals.spiral(level, player.position(), 1.0, 2.5, 2, 30, SkillVisuals.RAGE);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.5f);
        return true;
    }

    private static void doomSlamLanding(ServerLevel level, ServerPlayer player) {
        Vec3 center = player.position();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.3, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(DIRT, center.x, center.y + 0.2, center.z, 60, 1.5, 0.3, 1.5, 0.4);
        SkillVisuals.burst(level, center, 0.3, 30, 0.5, ParticleTypes.CRIT);
        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    // One shockwave. It hurts the monsters in its own band of distance, so each monster is only hit by one of them
    private static void doomSlamWave(ServerLevel level, ServerPlayer player, Effect effect) {
        int wave = DOOM_SLAM_WAVES - effect.left;
        // Each rank makes the waves a little wider, up to rank 10
        double width = DOOM_SLAM_WAVE_WIDTH + 0.1 * Upgrade.utility(Progression.rank(player, Upgrade.DOOM_SLAM));
        double inner = wave * width;
        double outer = inner + width;
        Vec3 center = effect.center;
        for (LivingEntity target : ClassSkills.enemies(level, center, outer)) {
            // enemies() reaches a little past the radius, so the outer edge is checked too
            double distance = target.position().distanceTo(center);
            if (distance < inner || distance >= outer) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), effect.damage);
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.7, 0));
            target.hurtMarked = true;
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }
        SkillVisuals.ring(level, center, 0.2, outer, (int) (outer * 8), DIRT);
        SkillVisuals.ring(level, center, 0.4, outer, (int) (outer * 5), SkillVisuals.EARTH_FADE);
        SkillVisuals.ring(level, center, 0.1, outer - 0.5, (int) (outer * 4), ParticleTypes.CLOUD);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 0.6f + wave * 0.15f);
    }

    // The ranger turns into a storm of arrows: for a few seconds two arrows fly at the monsters around them every
    // quarter of a second, by themselves
    static boolean arrowHurricane(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Arrow Hurricane rank adds 15% damage, and every second rank a volley of two arrows
        int rank = Progression.rank(player, Upgrade.ARROW_HURRICANE);
        float damage = (float) (HURRICANE_ARROW_DAMAGE * (1 + 0.15 * rank));
        int volleys = HURRICANE_VOLLEYS + Upgrade.utility(rank) / 2;
        // It runs every tick so the tornado moves smoothly, and a volley goes off every HURRICANE_TIME ticks of it
        add(player, new Effect(Kind.ARROW_HURRICANE, player.position(), damage, volleys * HURRICANE_TIME, level.getGameTime(), 0));
        // A gust that blows out from the ranger as the storm starts
        Vec3 center = player.position();
        SkillVisuals.burst(level, center, 0.3, 32, 0.6, ParticleTypes.CLOUD);
        SkillVisuals.shockwave(level, center, 0.1, 4.0, 3, SkillVisuals.GOLD);
        SkillVisuals.spiral(level, center, 1.8, 4.0, 4, 60, ParticleTypes.SWEEP_ATTACK);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    // Every tick: the tornado around the ranger, a volley when it is time, and a last gust when it ends
    private static void hurricaneTick(ServerLevel level, ServerPlayer player, Effect effect) {
        tornado(level, player);
        if (effect.left % HURRICANE_TIME == 0) {
            hurricaneVolley(level, player, effect);
        }
        if (effect.left == 1) {
            Vec3 center = player.position();
            SkillVisuals.burst(level, center, 0.5, 40, 0.8, ParticleTypes.CLOUD);
            SkillVisuals.shockwave(level, center, 0.1, 5.0, 2, ParticleTypes.SWEEP_ATTACK);
            level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
            level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    // A funnel of wind that gets wider towards the top and turns fast, with blades of wind, gold dust in it and dirt
    // whirled up off the ground
    private static void tornado(ServerLevel level, ServerPlayer player) {
        long time = level.getGameTime();
        double turn = time * 0.55;
        for (int layer = 0; layer < 8; layer++) {
            double height = layer * 0.45;
            double radius = 0.7 + layer * 0.22;
            for (int arm = 0; arm < 2; arm++) {
                double angle = turn + layer * 0.7 + arm * Math.PI;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                // Moving along the circle, so each puff is carried round a little before it fades
                double vx = -Math.sin(angle) * 0.15;
                double vz = Math.cos(angle) * 0.15;
                level.sendParticles(ParticleTypes.CLOUD, x, player.getY() + height, z, 0, vx, 0.02, vz, 1);
                if (layer % 2 == 0) {
                    level.sendParticles(SkillVisuals.GOLD, x, player.getY() + height + 0.2, z, 1, 0.05, 0.05, 0.05, 0);
                }
            }
        }
        if (time % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = turn * 1.3 + i * Math.PI;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * 1.6, player.getY() + 0.6 + i * 1.2,
                        player.getZ() + Math.sin(angle) * 1.6, 1, 0, 0, 0, 0);
            }
        }
        if (time % 2 == 0) {
            BlockState ground = level.getBlockState(player.blockPosition().below());
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
                        4, 1.2, 0.05, 1.2, 0.15);
            }
        }
        if (time % 10 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
    }

    private static void hurricaneVolley(ServerLevel level, ServerPlayer player, Effect effect) {
        List<LivingEntity> targets = new ArrayList<>(ClassSkills.enemies(level, player.position(), HURRICANE_RANGE));
        if (targets.isEmpty()) {
            return;
        }
        RandomSource random = player.getRandom();
        Vec3 from = player.getEyePosition().subtract(0, 0.2, 0);
        for (int i = 0; i < 2; i++) {
            LivingEntity target = targets.get(random.nextInt(targets.size()));
            Arrow arrow = ClassSkills.arrow(level, player, effect.damage);
            arrow.setPos(from.x, from.y, from.z);
            arrow.getPersistentData().putBoolean(HURRICANE_TAG, true);
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(from);
            // A little higher the further away, so the arrow doesn't drop short
            arrow.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.05, aim.z, 2.6f, 1.5f);
            level.addFreshEntity(arrow);
            // A flash where it leaves
            Vec3 muzzle = from.add(aim.normalize().scale(0.8));
            level.sendParticles(ParticleTypes.CRIT, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.2);
            level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8f, 1.2f + random.nextFloat() * 0.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.4f, 1.6f + random.nextFloat() * 0.3f);
    }

    // The necromancer calls the dead up from the ground where they aim: arms of bone burst up under the monsters there,
    // hurt them and hold them for a moment
    static boolean armyOfTheDead(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = ClassSkills.arrowRainTarget(level, player);
        // Each Army of the Dead rank adds 20% damage, and every second rank an arm
        int rank = Progression.rank(player, Upgrade.ARMY_OF_THE_DEAD);
        float damage = attackDamage(player, 1.8, Upgrade.ARMY_OF_THE_DEAD, 0.2);
        int arms = ARMY_ARMS + Upgrade.utility(rank) / 2;
        add(player, new Effect(Kind.ARMY_OF_THE_DEAD, center, damage, arms, level.getGameTime() + 10, 0));
        SkillVisuals.ring(level, center, 0.1, ARMY_RADIUS, 48, SkillVisuals.SHADOW);
        SkillVisuals.ring(level, center, 0.1, ARMY_RADIUS - 0.5, 32, ParticleTypes.SOUL);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.3, center.z, 30, ARMY_RADIUS / 2, 0.2, ARMY_RADIUS / 2, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + 0.1, center.z, 25, ARMY_RADIUS / 2, 0.05, ARMY_RADIUS / 2, 0.01);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 0.5f);
        return true;
    }

    // One arm, under a random monster in the area, or a random spot if there are none
    private static void boneArm(ServerLevel level, ServerPlayer player, Effect effect) {
        RandomSource random = player.getRandom();
        List<LivingEntity> inside = ClassSkills.enemies(level, effect.center, ARMY_RADIUS);
        Vec3 spot;
        if (!inside.isEmpty()) {
            spot = inside.get(random.nextInt(inside.size())).position();
        } else {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * ARMY_RADIUS;
            spot = effect.center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        }
        for (LivingEntity target : ClassSkills.enemies(level, spot, ARMY_HIT_RADIUS)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), effect.damage);
            hold(target, 15);
        }
        // A column of bone shooting up, with grasping fingers spread out at the top and souls rising from the hole
        for (double y = 0; y < 2.2; y += 0.25) {
            level.sendParticles(BONE, spot.x, spot.y + y, spot.z, 2, 0.12, 0.05, 0.12, 0);
        }
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * 2 * i / 5;
            level.sendParticles(SkillVisuals.BONE_DUST, spot.x + Math.cos(angle) * 0.4, spot.y + 2.3, spot.z + Math.sin(angle) * 0.4,
                    2, 0.03, 0.15, 0.03, 0);
        }
        SkillVisuals.ring(level, spot, 0.1, 1.0, 12, SkillVisuals.SHADOW);
        level.sendParticles(ParticleTypes.SOUL, spot.x, spot.y + 0.5, spot.z, 4, 0.3, 0.3, 0.3, 0.03);
        level.sendParticles(DIRT, spot.x, spot.y + 0.1, spot.z, 12, 0.4, 0.1, 0.4, 0.15);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f + random.nextFloat() * 0.3f);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.5f, 0.5f);
    }

    // Can't move and barely hurts
    private static void hold(LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 9));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
    }

    // Runs what the skills left behind when its time comes
    // What a skill left behind stops when the player dies, logs out or goes to another dimension, so it doesn't
    // go on at the old spot or come back later
    static void forget(Player player) {
        EFFECTS.remove(player.getUUID());
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        iceArmorGlow(player);
        List<Effect> effects = EFFECTS.get(player.getUUID());
        if (effects == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        List<Effect> next = new ArrayList<>();
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            Effect effect = it.next();
            if (now < effect.next) {
                continue;
            }
            it.remove();
            switch (effect.kind) {
                case EARTHQUAKE -> {
                    earthquakePulse(level, player, effect);
                    if (effect.left > 1) {
                        next.add(new Effect(effect.kind, effect.center, effect.damage, effect.left - 1, now + EARTHQUAKE_PULSE_TIME, 0));
                    }
                }
                case METEOR -> meteorImpact(level, player, effect);
                case THUNDERSTORM -> {
                    lightningStrike(level, player, effect);
                    if (effect.left > 1) {
                        next.add(new Effect(effect.kind, effect.center, effect.damage, effect.left - 1, now + STORM_BOLT_TIME, 0));
                    }
                }
                case DOOM_SLAM_FALL -> {
                    if (player.onGround() || now >= effect.until) {
                        player.resetFallDistance();
                        doomSlamLanding(level, player);
                        next.add(new Effect(Kind.DOOM_SLAM_WAVE, player.position(), effect.damage, DOOM_SLAM_WAVES, now, 0));
                    } else {
                        // A trail on the way down. The fall is reset every tick, like Leap, since the server hands
                        // out fall damage as soon as you land, before this sees it
                        player.resetFallDistance();
                        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 2, 0.2, 0.1, 0.2, 0);
                        next.add(new Effect(effect.kind, effect.center, effect.damage, 1, now + 1, effect.until));
                    }
                }
                case DOOM_SLAM_WAVE -> {
                    doomSlamWave(level, player, effect);
                    if (effect.left > 1) {
                        next.add(new Effect(effect.kind, effect.center, effect.damage, effect.left - 1, now + DOOM_SLAM_WAVE_TIME, 0));
                    }
                }
                case ARROW_HURRICANE -> {
                    hurricaneTick(level, player, effect);
                    if (effect.left > 1) {
                        next.add(new Effect(effect.kind, effect.center, effect.damage, effect.left - 1, now + 1, 0));
                    }
                }
                case ARMY_OF_THE_DEAD -> {
                    boneArm(level, player, effect);
                    if (effect.left > 1) {
                        next.add(new Effect(effect.kind, effect.center, effect.damage, effect.left - 1, now + ARMY_TIME, 0));
                    }
                }
                case BEAR_TRAP -> {
                    if (!bearTrapCheck(level, player, effect) && now < effect.until) {
                        next.add(new Effect(effect.kind, effect.center, effect.damage, 1, now + BEAR_TRAP_CHECK, effect.until));
                    }
                }
            }
        }
        effects.addAll(next);
        if (effects.isEmpty()) {
            EFFECTS.remove(player.getUUID());
        }
    }

    private static void earthquakePulse(ServerLevel level, ServerPlayer player, Effect effect) {
        for (LivingEntity target : ClassSkills.enemies(level, effect.center, EARTHQUAKE_RADIUS)) {
            // Each pulse is its own hit, even if the monster was just hit
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), effect.damage);
        }
        Vec3 center = effect.center;
        level.sendParticles(DIRT, center.x, center.y + 0.1, center.z, 30, EARTHQUAKE_RADIUS / 2, 0.1, EARTHQUAKE_RADIUS / 2, 0.2);
        // A ring of dust rolling out, the edge shaking and a couple of rocks bursting up from the ground
        SkillVisuals.burst(level, center, 0.1, 12, 0.3, ParticleTypes.DUST_PLUME);
        SkillVisuals.ring(level, center, 0.1, EARTHQUAKE_RADIUS, 16, SkillVisuals.EARTH_FADE);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * EARTHQUAKE_RADIUS;
            level.sendParticles(STONE, center.x + Math.cos(angle) * distance, center.y + 0.2, center.z + Math.sin(angle) * distance,
                    6, 0.15, 0.3, 0.15, 0.2);
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3f, 0.5f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    private static void meteorImpact(ServerLevel level, ServerPlayer player, Effect effect) {
        Vec3 center = effect.center;
        // A trail of fire and smoke coming down at a slant from the sky, from behind the player
        Vec3 back = player.position().subtract(center).multiply(1, 0, 1);
        back = back.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : back.normalize();
        Vec3 sky = center.add(back.scale(4)).add(0, 10, 0);
        for (double t = 0; t < 1; t += 0.05) {
            Vec3 point = sky.lerp(center, t);
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 2, 0.15, 0.1, 0.15, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y, point.z, 1, 0.2, 0.2, 0.2, 0);
            level.sendParticles(SkillVisuals.FIRE_FADE, point.x, point.y, point.z, 1, 0.25, 0.25, 0.25, 0);
        }
        for (LivingEntity target : ClassSkills.enemies(level, center, METEOR_RADIUS)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), effect.damage);
            target.igniteForSeconds(4);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, 30, 1.5, 0.5, 1.5, 0);
        // Fire rolling out over the ground, burning rock thrown up, a smoke cloud and the burnt edge
        SkillVisuals.burst(level, center, 0.3, 32, 0.35, ParticleTypes.FLAME);
        level.sendParticles(MAGMA, center.x, center.y + 0.3, center.z, 25, 1.0, 0.3, 1.0, 0.3);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1, center.z, 20, 1.5, 0.8, 1.5, 0.03);
        SkillVisuals.ring(level, center, 0.15, METEOR_RADIUS, 24, SkillVisuals.FIRE_FADE);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.5f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    // true if a monster stepped in the trap, so it is used up
    private static boolean bearTrapCheck(ServerLevel level, ServerPlayer player, Effect effect) {
        List<LivingEntity> caught = ClassSkills.enemies(level, effect.center, BEAR_TRAP_RADIUS);
        if (caught.isEmpty()) {
            // Glints a little now and then, so you can see where it is
            // Once a second since it was put down. It is checked every BEAR_TRAP_CHECK ticks from then
            if ((level.getGameTime() - (effect.until - BEAR_TRAP_LIFETIME)) % 20 == 0) {
                level.sendParticles(ParticleTypes.CRIT, effect.center.x, effect.center.y + 0.1, effect.center.z, 3, 0.2, 0, 0.2, 0);
                trapJaws(level, effect.center);
            }
            return false;
        }
        LivingEntity target = caught.get(0);
        target.hurt(player.damageSources().playerAttack(player), effect.damage);
        hold(target, BEAR_TRAP_HOLD);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.3, target.getZ(), 20, 0.3, 0.2, 0.3, 0.2);
        // The jaws snap shut: two sweeps closing, blood and chips of iron
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.3, target.getZ(), 2, 0.2, 0, 0.2, 0);
        level.sendParticles(BLOOD, target.getX(), target.getY() + 0.4, target.getZ(), 10, 0.3, 0.2, 0.3, 0);
        SkillVisuals.implode(level, target.position(), 0.2, 0.9, 12, 0.2, ParticleTypes.CRIT);
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    private static void circle(ServerLevel level, Vec3 center, double radius, ParticleOptions particle) {
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + 0.2, center.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }
}
