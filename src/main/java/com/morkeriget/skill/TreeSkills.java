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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

// The skills in the third row of the skill trees: the barbarian's Earthquake and Rush, the sorcerer's Meteor and Ice
// Armor, the ranger's Explosive Arrow and Bear Trap, and the necromancer's Blood Nova and Bone Prison. Server only,
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
    private static final BlockParticleOption BONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
    private static final BlockParticleOption DIRT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7f, 0.05f, 0.05f), 1.5f);

    // Something a skill left behind that happens later: earthquake pulses, a falling meteor, a trap waiting for a monster
    private enum Kind {
        EARTHQUAKE, METEOR, BEAR_TRAP
    }

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
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 0.6f);
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
        for (double travelled = 0; travelled <= distance; travelled += 0.5) {
            Vec3 point = start.add(flat.scale(travelled));
            level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 1, 0.2, 0.1, 0.2, 0);
            for (LivingEntity target : ClassSkills.enemies(level, point, 1.2)) {
                if (!hit.contains(target)) {
                    hit.add(target);
                    target.hurt(player.damageSources().playerAttack(player), damage);
                    // Thrown to the side, out of the way
                    target.knockback(0.6, flat.z, -flat.x);
                }
            }
        }
        // Fast enough to get there in about half a second
        player.setDeltaMovement(flat.x * Math.min(2.2, distance / 3.5), 0.15, flat.z * Math.min(2.2, distance / 3.5));
        player.hurtMarked = true;
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.8f);
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
        circle(level, center, METEOR_RADIUS, ParticleTypes.FLAME);
        // Meteor Shower: two more a little after, a few blocks to the sides
        if (LegendaryPower.has(player, LegendaryPower.METEOR_SHOWER)) {
            for (int i = 0; i < 2; i++) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                Vec3 spot = center.add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
                add(player, new Effect(Kind.METEOR, spot, damage, 1, level.getGameTime() + METEOR_DELAY + 10 * (i + 1), 0));
                circle(level, spot, METEOR_RADIUS, ParticleTypes.FLAME);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.5f);
        return true;
    }

    // Extra hearts of ice for a while, and every monster that hits you freezes
    static boolean iceArmor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Ice Armor rank makes it last one second longer
        int duration = ICE_ARMOR_DURATION + 20 * Progression.rank(player, Upgrade.ICE_ARMOR);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 1));
        player.getPersistentData().putLong(ICE_ARMOR_UNTIL_TAG, level.getGameTime() + duration);
        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
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
    }

    // One arrow that explodes where it hits, also on the ground
    static boolean explosiveArrow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Arrow arrow = ClassSkills.arrow(level, player, 2.0);
        arrow.getPersistentData().putBoolean(EXPLOSIVE_TAG, true);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 3.0f, 0.5f);
        level.addFreshEntity(arrow);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f);
        return true;
    }

    static void onProjectileImpact(ProjectileImpactEvent event) {
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
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
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
        level.playSound(null, center.x, center.y, center.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
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
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    // Walls of bone come up where you aim and hold every monster there
    static boolean bonePrison(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = ClassSkills.arrowRainTarget(level, player);
        // Each Bone Prison rank holds them 0.3 seconds longer
        int duration = BONE_PRISON_DURATION + 6 * Progression.rank(player, Upgrade.BONE_PRISON);
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
        }
        circle(level, center, BONE_PRISON_RADIUS, BONE);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    // Can't move and barely hurts
    private static void hold(LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 9));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
    }

    // Runs what the skills left behind when its time comes
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
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
        level.sendParticles(DIRT, effect.center.x, effect.center.y + 0.1, effect.center.z, 40, EARTHQUAKE_RADIUS / 2, 0.1, EARTHQUAKE_RADIUS / 2, 0.2);
        level.playSound(null, effect.center.x, effect.center.y, effect.center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3f, 0.5f);
    }

    private static void meteorImpact(ServerLevel level, ServerPlayer player, Effect effect) {
        Vec3 center = effect.center;
        // A trail of fire coming down from the sky
        for (double y = 8; y > 0; y -= 0.5) {
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y + y, center.z, 2, 0.15, 0.1, 0.15, 0);
        }
        for (LivingEntity target : ClassSkills.enemies(level, center, METEOR_RADIUS)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), effect.damage);
            target.igniteForSeconds(4);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, 30, 1.5, 0.5, 1.5, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    // true if a monster stepped in the trap, so it is used up
    private static boolean bearTrapCheck(ServerLevel level, ServerPlayer player, Effect effect) {
        List<LivingEntity> caught = ClassSkills.enemies(level, effect.center, BEAR_TRAP_RADIUS);
        if (caught.isEmpty()) {
            // Glints a little now and then, so you can see where it is
            if (level.getGameTime() % 20 == 0) {
                level.sendParticles(ParticleTypes.CRIT, effect.center.x, effect.center.y + 0.1, effect.center.z, 3, 0.2, 0, 0.2, 0);
            }
            return false;
        }
        LivingEntity target = caught.get(0);
        target.hurt(player.damageSources().playerAttack(player), effect.damage);
        hold(target, BEAR_TRAP_HOLD);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.3, target.getZ(), 20, 0.3, 0.2, 0.3, 0.2);
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.6f);
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
