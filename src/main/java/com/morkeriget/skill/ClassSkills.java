package com.morkeriget.skill;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.loot.ItemKind;
import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.loot.Loot;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The skills that came with the classes: the barbarian's Leap and Frenzy, the sorcerer's Fireball, and the ranger's
// Multishot, Arrow Rain, Smoke Bomb and Wolves. Server only, called from Skills.use
final class ClassSkills {
    // 20 ticks is one second
    private static final double LEAP_RADIUS = 4.0;
    private static final int EARTHBREAKER_STUN = 2 * 20;
    private static final int FRENZY_DURATION = 6 * 20;
    private static final double FIREBALL_RANGE = 24.0;
    private static final double FIREBALL_RADIUS = 3.0;
    private static final double ARROW_RAIN_RANGE = 24.0;
    private static final double ARROW_RAIN_RADIUS = 4.0;
    // Before the bow's level and the ranger bonus. Three waves of this, half a second apart
    private static final double ARROW_RAIN_DAMAGE = 6.2;
    private static final int ARROW_RAIN_WAVES = 3;
    private static final int ARROW_RAIN_WAVE_TIME = 10;
    // Looking at the ground this close to an enemy still puts the rain on the enemy
    private static final double ARROW_RAIN_SNAP = 5.0;
    private static final int SMOKE_BOMB_DURATION = 4 * 20;
    private static final double SMOKE_BOMB_RADIUS = 8.0;
    private static final int WOLF_LIFETIME = 20 * 20;
    // Arrows from the ranger's skills, before the bow's level and the ranger bonus in PlayerHits
    private static final double ARROW_DAMAGE = 2.0;
    // Summoned wolves and skeletons disappear at this game time
    static final String SUMMON_UNTIL_TAG = "morkeriget_summon_until";
    static final String MULTISHOT_TAG = "morkeriget_multishot_arrow";
    private static final ResourceLocation WOLF_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "wolf_boost");

    // Players in the air after Leap, and when they jumped
    private static final Map<UUID, Long> LEAPING = new HashMap<>();

    // An Arrow Rain that still has waves left, and when the next one comes
    private record Rain(Vec3 center, double radius, float damage, int wavesLeft, long nextWave) {
    }

    private static final Map<UUID, Rain> RAINS = new HashMap<>();

    // A rain or leap shouldn't go on after the player logs out, dies or goes through a portal
    static void forget(Player player) {
        RAINS.remove(player.getUUID());
        LEAPING.remove(player.getUUID());
    }

    private ClassSkills() {
    }

    // Jumps forward the way you are looking and hits everything around you when you land
    static boolean leap(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 0.01) {
            return false;
        }
        flat = flat.normalize();
        player.setDeltaMovement(flat.x * 1.6, 0.8, flat.z * 1.6);
        // Tells the client about the jump, otherwise it only happens on the server
        player.hurtMarked = true;
        LEAPING.put(player.getUUID(), player.serverLevel().getGameTime());
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 15, 0.4, 0.1, 0.4, 0.05);
        // Dust pushed away from where you jumped off
        SkillVisuals.burst(player.serverLevel(), player.position(), 0.1, 16, 0.25, ParticleTypes.POOF);
        player.serverLevel().sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.4, 0.05, 0.4, 0.02);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5f, 0.8f);
        return true;
    }

    // While leaping you don't take fall damage, and when you land (or after three seconds) the slam happens
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SkillVisuals.buffAuras(player);
        Rain rain = RAINS.get(player.getUUID());
        if (rain != null && player.serverLevel().getGameTime() >= rain.nextWave) {
            rainWave(player.serverLevel(), player, rain);
            if (rain.wavesLeft > 1) {
                RAINS.put(player.getUUID(), new Rain(rain.center, rain.radius, rain.damage, rain.wavesLeft - 1,
                        rain.nextWave + ARROW_RAIN_WAVE_TIME));
            } else {
                RAINS.remove(player.getUUID());
            }
        }
        Long start = LEAPING.get(player.getUUID());
        if (start == null) {
            return;
        }
        player.resetFallDistance();
        long time = player.serverLevel().getGameTime() - start;
        // A trail of wind behind you in the air
        if (!player.onGround()) {
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.2, 0.3, 0.2, 0);
            player.serverLevel().sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 1, 0.3, 0.4, 0.3, 0);
        }
        if ((time > 5 && player.onGround()) || time > 60 || !player.isAlive()) {
            LEAPING.remove(player.getUUID());
            if (player.isAlive()) {
                leapSlam(player);
            }
        }
    }

    private static void leapSlam(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Leap upgrade rank adds 20% damage
        double upgrade = 1 + 0.2 * Progression.rank(player, Upgrade.LEAP);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.5 * upgrade);
        boolean earthbreaker = LegendaryPower.has(player, LegendaryPower.EARTHBREAKER);
        for (LivingEntity target : enemies(level, player.position(), LEAP_RADIUS)) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 push = target.position().subtract(player.position()).normalize();
            target.knockback(0.8, -push.x, -push.z);
            if (earthbreaker) {
                // Stunned: they can't move and barely hurt
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EARTHBREAKER_STUN, 9));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EARTHBREAKER_STUN, 4));
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
                        8, 0.3, 0.1, 0.3, 0);
            }
        }
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            level.sendParticles(ParticleTypes.EXPLOSION, player.getX() + Math.cos(angle) * 2.5, player.getY() + 0.2,
                    player.getZ() + Math.sin(angle) * 2.5, 1, 0, 0, 0, 0);
        }
        // Bits of the ground thrown up, a ring of dust rolling out and cracks of dust out to the edge
        BlockState ground = level.getBlockState(player.blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
                    40, 1.2, 0.1, 1.2, 0.3);
        }
        Vec3 center = player.position();
        SkillVisuals.burst(level, center, 0.2, 24, 0.4, ParticleTypes.CLOUD);
        SkillVisuals.burst(level, center, 0.5, 16, 0.6, ParticleTypes.CRIT);
        SkillVisuals.ring(level, center, 0.1, LEAP_RADIUS, 20, ParticleTypes.DUST_PLUME);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 0.5f);
    }

    // Faster attacks and running for a while, and heals a little right away
    static boolean frenzy(ServerPlayer player) {
        // Each Frenzy upgrade rank makes it last one second longer
        int duration = FRENZY_DURATION + 20 * Upgrade.utility(Progression.rank(player, Upgrade.FRENZY));
        // Haste makes attacks faster, 10% per level
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, 2));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0));
        player.heal(player.getMaxHealth() * 0.15f);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 8, 0.5, 0.6, 0.5, 0);
        // Red rage winding up around you, sparks flying off and a few hearts for the healing
        SkillVisuals.spiral(level, player.position(), 0.9, 2.2, 2.5, 30, SkillVisuals.RAGE);
        SkillVisuals.burst(level, player.position(), 1.0, 16, 0.5, ParticleTypes.CRIT);
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2, player.getZ(), 3, 0.4, 0.2, 0.4, 0);
        player.getPersistentData().putLong(SkillVisuals.FRENZY_UNTIL_TAG, level.getGameTime() + duration);
        level.playSound(null, player.blockPosition(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5f, 1.5f);
        return true;
    }

    // A ball of fire that flies the way you are looking and explodes on the first monster or wall it hits
    static boolean fireball(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 impact = level.clip(new ClipContext(eyes, eyes.add(look.scale(FIREBALL_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        double distance = impact.distanceTo(eyes);
        // Sparks of fire in your hand as you throw it
        Vec3 hand = eyes.add(look.scale(0.8)).subtract(0, 0.3, 0);
        level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 8, 0.15, 0.15, 0.15, 0.03);
        level.sendParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 4, 0.1, 0.1, 0.1, 0.01);
        int steps = 0;
        for (double travelled = 0.5; travelled <= distance; travelled += 0.5) {
            Vec3 point = eyes.add(look.scale(travelled));
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0);
            // A glowing tail of embers and smoke behind it
            level.sendParticles(SkillVisuals.FIRE_FADE, point.x, point.y, point.z, 1, 0.08, 0.08, 0.08, 0);
            if (steps++ % 2 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0);
            }
            // What the ball touches, not how far it is from their feet, or it would fly over a monster aimed at the head
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(0.6), Minions::isEnemy).isEmpty()) {
                impact = point;
                break;
            }
        }

        // Each Fireball upgrade rank adds 20% damage
        double upgrade = 1 + 0.2 * Progression.rank(player, Upgrade.FIREBALL);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.0 * upgrade);
        for (LivingEntity target : enemies(level, impact, FIREBALL_RADIUS)) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.igniteForSeconds(4);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 3, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 40, 0.8, 0.8, 0.8, 0.1);
        // A ring of fire rolling out, lava drops, smoke and a burning edge where it reaches
        SkillVisuals.burst(level, impact, 0, 20, 0.25, ParticleTypes.FLAME);
        level.sendParticles(ParticleTypes.LAVA, impact.x, impact.y, impact.z, 10, 0.5, 0.3, 0.5, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, impact.y + 0.5, impact.z, 10, 0.6, 0.5, 0.6, 0.02);
        SkillVisuals.ring(level, impact, 0, FIREBALL_RADIUS, 20, SkillVisuals.FIRE_FADE);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 0.8f);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // Five arrows in a fan, or nine closer together with Arrow Storm
    static boolean multishot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Multishot upgrade rank adds 15% damage
        double damage = ARROW_DAMAGE * (1 + 0.15 * Progression.rank(player, Upgrade.MULTISHOT));
        boolean storm = LegendaryPower.has(player, LegendaryPower.ARROW_STORM);
        int side = storm ? 4 : 2;
        float spread = storm ? 6 : 8;
        for (int i = -side; i <= side; i++) {
            Arrow arrow = arrow(level, player, damage);
            // So all of them can hit a monster up close, see TreeSkills.onProjectileImpact
            arrow.getPersistentData().putBoolean(MULTISHOT_TAG, true);
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + i * spread, 0, 3.0f, 1.0f);
            level.addFreshEntity(arrow);
            // A streak flying off the bow along each arrow, so the fan can be seen
            Vec3 way = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + i * spread);
            Vec3 start = player.getEyePosition().add(way.scale(1.2)).subtract(0, 0.2, 0);
            level.sendParticles(ParticleTypes.CRIT, start.x, start.y, start.z, 0, way.x, way.y, way.z, 1.2);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, start.x, start.y, start.z, 0, way.x, way.y, way.z, 0.8);
        }
        Vec3 bow = player.getEyePosition().add(player.getLookAngle().scale(1.0)).subtract(0, 0.2, 0);
        level.sendParticles(ParticleTypes.POOF, bow.x, bow.y, bow.z, 5, 0.15, 0.15, 0.15, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    // Arrows rain down where you are looking, in a few waves that hit every enemy in the area. Real arrows were too
    // easy to dodge and most of them did nothing, since a monster can't be hurt again right after a hit
    static boolean arrowRain(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = arrowRainTarget(level, player);
        // Each Arrow Rain upgrade rank adds 15% damage and a bit more area
        int rank = Progression.rank(player, Upgrade.ARROW_RAIN);
        double radius = ARROW_RAIN_RADIUS + 0.2 * Upgrade.utility(rank);
        float damage = (float) (ARROW_RAIN_DAMAGE * bowScale(player) * (1 + 0.15 * rank));
        int waves = LegendaryPower.has(player, LegendaryPower.ENDLESS_RAIN) ? ARROW_RAIN_WAVES * 2 : ARROW_RAIN_WAVES;
        RAINS.put(player.getUUID(), new Rain(center, radius, damage, waves, level.getGameTime() + ARROW_RAIN_WAVE_TIME));
        circle(level, center, radius);
        // A golden mark on the ground where it will come down, and a volley shot up into the sky
        SkillVisuals.ring(level, center, 0.1, radius * 0.5, 12, SkillVisuals.GOLD);
        level.sendParticles(SkillVisuals.GOLD, center.x, center.y + 0.1, center.z, 6, 0.2, 0, 0.2, 0);
        for (int i = 0; i < 8; i++) {
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.5, player.getZ(),
                    0, (player.getRandom().nextDouble() - 0.5) * 0.3, 1, (player.getRandom().nextDouble() - 0.5) * 0.3, 1.5);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.7f, 0.8f);
        return true;
    }

    // The enemy you look at, or else the spot you look at, moved to the closest enemy near it if there is one
    static Vec3 arrowRainTarget(ServerLevel level, ServerPlayer player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 end = level.clip(new ClipContext(eyes, eyes.add(player.getLookAngle().scale(ARROW_RAIN_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        EntityHitResult looked = ProjectileUtil.getEntityHitResult(level, player, eyes, end,
                new AABB(eyes, end).inflate(1), Minions::isEnemy, 0.3f);
        if (looked != null) {
            return looked.getEntity().position();
        }
        return enemies(level, end, ARROW_RAIN_SNAP).stream()
                .min(Comparator.comparingDouble(enemy -> enemy.position().distanceToSqr(end)))
                .map(LivingEntity::position)
                .orElse(end);
    }

    // One wave of arrows: falling arrow particles over the area and damage to every enemy in it
    private static void rainWave(ServerLevel level, ServerPlayer player, Rain rain) {
        RandomSource random = player.getRandom();
        for (int i = 0; i < 14; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * rain.radius;
            double x = rain.center.x + Math.cos(angle) * distance;
            double z = rain.center.z + Math.sin(angle) * distance;
            // Count 0 means the offsets are the speed, so these fly straight down
            level.sendParticles(ParticleTypes.CRIT, x, rain.center.y + 6, z, 0, 0, -1, 0, 1.5);
            level.sendParticles(ParticleTypes.CRIT, x, rain.center.y + 4, z, 0, 0, -1, 0, 1.5);
            // A puff of dust where it lands
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.POOF, x, rain.center.y + 0.1, z, 1, 0.1, 0, 0.1, 0.01);
            }
        }
        SkillVisuals.ring(level, rain.center, 0.2, rain.radius, 12, ParticleTypes.CRIT);
        for (LivingEntity target : enemies(level, rain.center, rain.radius)) {
            // Each wave is its own hit, even if the monster was just hit
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), rain.damage);
        }
        level.playSound(null, rain.center.x, rain.center.y, rain.center.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    // Arrows from a ranger's bow get stronger with the bow's item level and the ranger bonus, see PlayerHits.
    // Arrow Rain doesn't fire real arrows, so it does the same here
    static double bowScale(ServerPlayer player) {
        ItemStack bow = player.getMainHandItem();
        double scale = 1;
        if (ItemKind.of(bow) == ItemKind.RANGED && Loot.getRarity(bow) != null) {
            scale *= 1 + Config.AFFIX_PER_LEVEL.get() * (Loot.itemLevel(bow) - 1);
        }
        if (Progression.playerClass(player) == PlayerClass.RANGER) {
            scale *= 1.25;
        }
        return scale * (1 + 0.06 * Progression.rank(player, Upgrade.MARKSMAN));
    }

    static void circle(ServerLevel level, Vec3 center, double radius) {
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.CRIT, center.x + Math.cos(angle) * radius, center.y + 0.2,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    // You turn invisible and fast, and the monsters close by lose sight of you
    static boolean smokeBomb(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Smoke Bomb upgrade rank makes it last half a second longer
        int duration = SMOKE_BOMB_DURATION + 10 * Upgrade.utility(Progression.rank(player, Upgrade.SMOKE_BOMB));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1));
        for (LivingEntity target : enemies(level, player.position(), SMOKE_BOMB_RADIUS)) {
            if (target instanceof Mob mob && mob.getTarget() == player) {
                mob.setTarget(null);
            }
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 3 * 20, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * 20, 1));
            level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
                    4, 0.2, 0.1, 0.2, 0.02);
        }
        Vec3 center = player.position();
        // A flash and a pop, then a thick dark cloud that rolls outwards and smoke that hangs in the air for a while
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 80, 1.5, 1, 1.5, 0.02);
        level.sendParticles(SkillVisuals.SHADOW, player.getX(), player.getY() + 1, player.getZ(), 30, 1.5, 0.8, 1.5, 0);
        SkillVisuals.burst(level, center, 0.3, 20, 0.35, ParticleTypes.POOF);
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.8, 0.4, 0.8, 0.05);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 8, 1.2, 0.2, 1.2, 0.01);
        player.getPersistentData().putLong(SkillVisuals.SMOKE_BOMB_UNTIL_TAG, level.getGameTime() + duration);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 1.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 0.8f);
        return true;
    }

    // Tame wolves that fight for you for a while. They get stronger with your level
    static boolean wolves(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Wolves upgrade rank makes them 15% stronger, and every third rank adds a wolf. Pack Leader adds 10% a rank
        int rank = Progression.rank(player, Upgrade.WOLVES);
        double strength = (1 + 0.05 * (Progression.level(player) - 1)) * (1 + 0.15 * rank)
                * (1 + 0.1 * Progression.rank(player, Upgrade.PACK_LEADER)) - 1;
        LivingEntity nearest = enemies(level, player.position(), 16).stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player))).orElse(null);
        RandomSource random = player.getRandom();
        for (int i = 0; i < 2 + Upgrade.utility(rank) / 3; i++) {
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf == null) {
                continue;
            }
            wolf.moveTo(player.getX() + (random.nextDouble() - 0.5) * 2, player.getY(), player.getZ() + (random.nextDouble() - 0.5) * 2,
                    random.nextFloat() * 360, 0);
            wolf.tame(player);
            wolf.setOrderedToSit(false);
            // Wolf armour from the fifth rank, better every fifth
            SummonGear.equip(wolf, rank);
            boost(wolf.getAttribute(Attributes.MAX_HEALTH), strength);
            boost(wolf.getAttribute(Attributes.ATTACK_DAMAGE), strength);
            wolf.setHealth(wolf.getMaxHealth());
            wolf.getPersistentData().putLong(SUMMON_UNTIL_TAG, level.getGameTime() + WOLF_LIFETIME);
            if (nearest != null) {
                wolf.setTarget(nearest);
            }
            level.addFreshEntity(wolf);
            level.sendParticles(ParticleTypes.POOF, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            // Each wolf comes out of a swirl of pale mist
            SkillVisuals.spiral(level, wolf.position(), 0.6, 1.2, 1.5, 14, SkillVisuals.BONE_DUST);
            level.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 0.2, wolf.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
        }
        SkillVisuals.burst(level, player.position(), 0.2, 16, 0.2, ParticleTypes.CLOUD);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1, player.getZ(), 8, 0.4, 0.5, 0.4, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 0.8f, 0.8f);
        return true;
    }

    // Summoned wolves go away again when their time is up
    static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        // Explosive Arrow leaves a trail of fire and smoke in the air
        if (event.getEntity() instanceof Arrow arrow) {
            TreeSkills.explosiveTrail(level, arrow);
        }
        if (event.getEntity().tickCount % 20 != 0) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) {
            NecroSkills.curseGlow(level, living);
        }
        long until = event.getEntity().getPersistentData().getLong(SUMMON_UNTIL_TAG);
        if (until > 0 && level.getGameTime() > until) {
            level.sendParticles(ParticleTypes.POOF, event.getEntity().getX(), event.getEntity().getY() + 0.5, event.getEntity().getZ(),
                    10, 0.3, 0.3, 0.3, 0.02);
            event.getEntity().discard();
            return;
        }
        if (event.getEntity() instanceof Mob minion && Minions.isAlly(minion)) {
            Minions.tick(level, minion);
        }
    }

    // An arrow from the player that can't be picked up. It counts as shot from the bow in your hand, if you hold one,
    // so the bow's bonuses work on it
    static Arrow arrow(ServerLevel level, ServerPlayer player, double damage) {
        ItemStack held = player.getMainHandItem();
        ItemStack bow = ItemKind.of(held) == ItemKind.RANGED ? held : null;
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
        arrow.setBaseDamage(damage);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        return arrow;
    }

    static List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                entity -> Minions.isEnemy(entity) && entity.position().distanceTo(center) <= radius + 1);
    }

    private static void boost(AttributeInstance attribute, double amount) {
        if (attribute != null && amount > 0) {
            attribute.addPermanentModifier(new AttributeModifier(WOLF_BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
