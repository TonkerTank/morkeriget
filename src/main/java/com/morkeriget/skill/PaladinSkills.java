package com.morkeriget.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Quaternionf;

// The paladin's skills: Holy Hammer, Shield Bash, Holy Aura, Holy Light, Consecration, Spear of Judgement and the big
// one, Heaven's Judgement. Also the class bonus (less damage taken with enemies close by) and Retribution. The paladin
// holds the line and keeps the group alive rather than hitting hardest. Server only, called from Skills.use
final class PaladinSkills {
    // 20 ticks is one second. Holy Hammer: the hammer spirals out from you, three turns in HAMMER_TICKS. It can hit the
    // same enemy again after HAMMER_REHIT ticks, so one that stays close is hit on each pass
    private static final int HAMMER_TICKS = 40;
    private static final int HAMMER_REHIT = 8;
    private static final double HAMMER_START = 0.8;
    private static final double HAMMER_REACH = 5.0;
    private static final double HAMMER_HIT = 1.4;
    private static final double HAMMER_TURNS = 3.0;
    private static final float HAMMER_SCALE = 1.4f;
    // How far the hammer spins each tick, in radians
    private static final float HAMMER_SPIN = 0.7f;
    private static final int GOLD_GLOW = 0xFFD24A;
    // What the light is made of: see-through gold for the glow, and a thin white core in pillars
    private static final BlockState LIGHT = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
    private static final BlockState CORE = Blocks.WHITE_CONCRETE.defaultBlockState();
    // Spear of Judgement flies this far each tick
    private static final double SPEAR_SPEED = 3.0;
    // Shield Bash: hits what is in front of you within this range
    private static final double BASH_RANGE = 4.0;
    private static final int BASH_STUN = 30;
    // Holy Aura: you and other players within AURA_RADIUS take less damage and heal while it lasts
    private static final double AURA_RADIUS = 8.0;
    private static final int AURA_DURATION = 10 * 20;
    private static final String AURA_UNTIL_TAG = "morkeriget_holy_aura_until";
    // Holy Light heals players within LIGHT_HEAL_RADIUS and hurts enemies within LIGHT_HIT_RADIUS
    private static final double LIGHT_HEAL_RADIUS = 8.0;
    private static final double LIGHT_HIT_RADIUS = 5.0;
    private static final double LIGHT_HEAL = 0.25;
    // Consecration: holy ground around where you stood, a pulse every CONSECRATION_TIME
    private static final double CONSECRATION_RADIUS = 4.0;
    private static final int CONSECRATION_PULSES = 12;
    private static final int CONSECRATION_TIME = 10;
    private static final float CONSECRATION_HEAL = 0.01f;
    private static final double SPEAR_RANGE = 20.0;
    // Heaven's Judgement: a pillar of light every JUDGEMENT_TIME on an enemy within JUDGEMENT_RADIUS of you
    private static final double JUDGEMENT_RADIUS = 7.0;
    private static final double PILLAR_RADIUS = 2.0;
    private static final int JUDGEMENT_PILLARS = 8;
    private static final int JUDGEMENT_TIME = 4;
    private static final double PILLAR_HEIGHT = 9.0;
    // The class bonus: this much less damage taken while an enemy is within GUARD_RANGE
    private static final double GUARD_RANGE = 4.0;
    private static final float GUARD = 0.15f;
    // Retribution: a pillar of light on the one who hit you, at most once per RETRIBUTION_COOLDOWN
    private static final int RETRIBUTION_COOLDOWN = 20;
    private static final String RETRIBUTION_READY_TAG = "morkeriget_retribution_ready";
    // The variants and keystones. The wider ones reach this much further
    private static final double WIDER = 1.5;
    // Heavy Hammer stuns this long, Shield Wall lasts this long, Holy Blast is this big
    private static final int HEAVY_HAMMER_STUN = 15;
    private static final int SHIELD_WALL_DURATION = 3 * 20;
    private static final double HOLY_BLAST_RADIUS = 3.0;
    // Pillars of Light heal this part of your health for each pillar that hits
    private static final float PILLAR_HEAL = 0.01f;
    // Aura of Might: players in the aura do this much more damage
    static final float MIGHT = 0.15f;
    private static final String MIGHT_UNTIL_TAG = "morkeriget_aura_might_until";
    // Martyr: others heal more, you take more. Holy Wrath: weaker healing. Unbreakable: less damage when low
    static final float MARTYR_HEAL = 1.5f;
    static final float MARTYR_TAKEN = 0.1f;
    static final float WRATH_HEAL = 0.5f;
    static final float UNBREAKABLE = 0.4f;
    static final float UNBREAKABLE_BELOW = 0.3f;

    private enum Kind {
        HAMMER, CONSECRATION, JUDGEMENT, SPEAR
    }

    // Something a skill left behind that goes on for a while. Mutable, since a hammer remembers whom it has hit
    private static final class Effect {
        final Kind kind;
        final Vec3 center;
        final float damage;
        // The hammer's angle to start from, or the radius of the holy ground
        final double extra;
        int step;
        final int steps;
        long next;
        final Set<Integer> hit = new HashSet<>();
        // The step the hammer last hit each enemy on
        final Map<Integer, Integer> lastHit = new HashMap<>();
        // What is shown of it: the hammer or the spear
        Display display;
        // The way the spear flies
        Vec3 way;
        // The enemy Focused Judgement's pillars come down on
        LivingEntity focus;

        Effect(Kind kind, Vec3 center, float damage, double extra, int steps, long next) {
            this.kind = kind;
            this.center = center;
            this.damage = damage;
            this.extra = extra;
            this.steps = steps;
            this.next = next;
        }
    }

    private static final Map<UUID, List<Effect>> EFFECTS = new HashMap<>();
    // The star under the feet of each paladin with Holy Aura
    private static final Map<UUID, List<Display>> AURA_STARS = new HashMap<>();

    private PaladinSkills() {
    }

    private static void add(ServerPlayer player, Effect effect) {
        EFFECTS.computeIfAbsent(player.getUUID(), uuid -> new ArrayList<>()).add(effect);
    }

    static void forget(Player player) {
        EFFECTS.remove(player.getUUID());
        List<Display> stars = AURA_STARS.remove(player.getUUID());
        if (stars != null) {
            stars.forEach(Display::discard);
        }
    }

    // Each damage rank of a skill adds 20%
    private static float damage(ServerPlayer player, double scale, Upgrade upgrade) {
        return (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * scale * (1 + 0.2 * Progression.rank(player, upgrade)));
    }

    // Each Devotion rank makes the healing 5% stronger
    private static float devotion(ServerPlayer player) {
        return 1 + 0.05f * Progression.rank(player, Upgrade.DEVOTION);
    }

    private static void hit(ServerPlayer player, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage);
    }

    // Can't move and barely hurts
    private static void stun(LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 9));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
    }

    // The player and the other players close by, for the healing and the aura
    // How far the skills reach with their variants: Big Hammer, Long Thrust, Wide Aura and Radiant Light
    private static double hammerReach(ServerPlayer player) {
        return Progression.has(player, Upgrade.HOLY_HAMMER_BIG) ? HAMMER_REACH * WIDER : HAMMER_REACH;
    }

    private static double bashRange(ServerPlayer player) {
        return Progression.has(player, Upgrade.SHIELD_BASH_LONG) ? BASH_RANGE * 2 : BASH_RANGE;
    }

    private static double auraRadius(ServerPlayer player) {
        return Progression.has(player, Upgrade.HOLY_AURA_WIDE) ? AURA_RADIUS * WIDER : AURA_RADIUS;
    }

    private static double lightHitRadius(ServerPlayer player) {
        return Progression.has(player, Upgrade.HOLY_LIGHT_RADIANT) ? LIGHT_HIT_RADIUS * WIDER : LIGHT_HIT_RADIUS;
    }

    private static List<ServerPlayer> allies(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(radius),
                other -> other.isAlive() && !other.isSpectator() && other.distanceTo(player) <= radius);
    }

    // A hammer of light that spirals out from you and hits everything it passes. Hammerfall throws three at once
    static boolean holyHammer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float damage = damage(player, 0.6, Upgrade.HOLY_HAMMER);
        int hammers = LegendaryPower.has(player, LegendaryPower.HAMMERFALL) ? 3 : 1;
        double start = Math.toRadians(player.getYRot() + 90);
        for (int i = 0; i < hammers; i++) {
            add(player, new Effect(Kind.HAMMER, player.position(), damage, start + Math.PI * 2 * i / hammers, HAMMER_TICKS, level.getGameTime()));
        }
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4f, 1.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    private static void hammerTick(ServerLevel level, ServerPlayer player, Effect effect) {
        double progress = (double) effect.step / HAMMER_TICKS;
        double radius = HAMMER_START + (hammerReach(player) - HAMMER_START) * progress;
        double angle = effect.extra + Math.PI * 2 * HAMMER_TURNS * progress;
        // It follows you, so you can walk with it
        Vec3 spot = player.position().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
        for (LivingEntity target : ClassSkills.enemies(level, spot.subtract(0, 1, 0), HAMMER_HIT)) {
            Integer last = effect.lastHit.get(target.getId());
            if (last == null || effect.step - last >= HAMMER_REHIT) {
                effect.lastHit.put(target.getId(), effect.step);
                hit(player, target, effect.damage);
                if (Progression.has(player, Upgrade.HOLY_HAMMER_HEAVY)) {
                    stun(target, HEAVY_HAMMER_STUN);
                }
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        6, 0.3, 0.3, 0.3, 0.2);
                level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 1.9f);
            }
        }
        // A golden hammer that tumbles end over end along the spiral, with a trail of light behind it
        if (effect.display == null) {
            effect.display = SkillDisplays.item(level, spot, new ItemStack(Items.MACE), hammerShape(angle, 0), HAMMER_TICKS + 2);
            if (effect.display != null) {
                SkillDisplays.smoothMoves(effect.display, 1);
                SkillDisplays.glow(effect.display, GOLD_GLOW);
            }
        } else {
            effect.display.moveTo(spot.x, spot.y, spot.z);
            SkillDisplays.reshape(effect.display, hammerShape(angle, effect.step), 1);
        }
        level.sendParticles(SkillVisuals.GOLD, spot.x, spot.y, spot.z, 2, 0.12, 0.12, 0.12, 0);
        level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y, spot.z, 1, 0.05, 0.05, 0.05, 0.01);
        if (effect.step % 3 == 0) {
            level.sendParticles(ParticleTypes.WAX_ON, spot.x, spot.y, spot.z, 1, 0.1, 0.1, 0.1, 0);
        }
        if (effect.step == HAMMER_TICKS - 1 && effect.display != null) {
            // It bursts into light at the end
            effect.display.discard();
            SkillVisuals.burst(level, spot.subtract(0, 1, 0), 1.0, 10, 0.12, ParticleTypes.END_ROD);
            level.sendParticles(ParticleTypes.FLASH, spot.x, spot.y, spot.z, 1, 0, 0, 0, 0);
        }
    }

    // The hammer turned so its flat side faces out from you, along the way it flies, and spun end over end
    private static com.mojang.math.Transformation hammerShape(double angle, int step) {
        Quaternionf rotation = new Quaternionf().rotationY((float) -(angle + Math.PI / 2)).rotateZ(-step * HAMMER_SPIN);
        return SkillDisplays.centered(HAMMER_SCALE, rotation);
    }

    // You throw yourself forward behind your shield: everything in front of you is hit, knocked back and stunned
    static boolean shieldBash(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
        float damage = damage(player, 2.4, Upgrade.SHIELD_BASH);
        // Each Shield Bash rank stuns for a tenth of a second longer
        int stun = BASH_STUN + 2 * Upgrade.utility(Progression.rank(player, Upgrade.SHIELD_BASH));
        player.setDeltaMovement(flat.x * 0.9, Math.max(0.1, player.getDeltaMovement().y), flat.z * 0.9);
        player.hurtMarked = true;
        Vec3 front = player.position().add(flat.scale(bashRange(player) / 2));
        for (LivingEntity target : ClassSkills.enemies(level, player.position(), bashRange(player))) {
            Vec3 way = target.position().subtract(player.position());
            Vec3 wayFlat = new Vec3(way.x, 0, way.z);
            if (wayFlat.lengthSqr() > 0.01 && wayFlat.normalize().dot(flat) < 0.3) {
                continue;
            }
            hit(player, target, damage);
            target.knockback(1.0, -flat.x, -flat.z);
            stun(target, stun);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
            level.sendParticles(SkillVisuals.GOLD, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 5, 0.3, 0.1, 0.3, 0);
        }
        // Shield Wall: Resistance II (40% less damage) for a while after
        if (Progression.has(player, Upgrade.SHIELD_BASH_WALL)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SHIELD_WALL_DURATION, 1));
        }
        // A golden wall of light where the shield hits and dust kicked up behind you
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        for (double offset = -1.5; offset <= 1.5; offset += 0.25) {
            for (double y = 0.2; y <= 2.0; y += 0.45) {
                Vec3 point = front.add(side.scale(offset)).add(0, y, 0);
                level.sendParticles(SkillVisuals.GOLD, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, front.x, front.y + 1, front.z, 1, 0, 0, 0, 0);
        // A big glowing shield that slams out in front of you and fades
        Display shield = SkillDisplays.item(level, front.add(0, 1.1, 0), new ItemStack(Items.SHIELD), SkillDisplays.centered(1.2f, new Quaternionf()), 10);
        if (shield != null) {
            SkillDisplays.faceCamera(shield);
            SkillDisplays.glow(shield, GOLD_GLOW);
            SkillDisplays.later(shield, 1, SkillDisplays.centered(2.6f, new Quaternionf()), 3);
            SkillDisplays.later(shield, 5, SkillDisplays.centered(0, new Quaternionf()), 4);
        }
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.3, 0.05, 0.3, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // For a while you and every player near you take less damage and heal. With Radiant Aura it also burns enemies
    static boolean holyAura(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Holy Aura rank and each Devotion rank makes it last a second longer
        int duration = AURA_DURATION + 20 * Upgrade.utility(Progression.rank(player, Upgrade.HOLY_AURA)) + 20 * Progression.rank(player, Upgrade.DEVOTION);
        player.getPersistentData().putLong(AURA_UNTIL_TAG, level.getGameTime() + duration);
        auraPulse(level, player);
        // A star of light under your feet that turns and follows you while it lasts
        Vec3 feet = player.position().add(0, 0.03, 0);
        // The old one goes if the aura is used again before it ran out
        List<Display> old = AURA_STARS.remove(player.getUUID());
        if (old != null) {
            old.forEach(Display::discard);
        }
        List<Display> stars = new ArrayList<>();
        stars.add(SkillDisplays.spin(level, feet, LIGHT, 2.8f, 0.02f, 0, 3, duration, player, 0.03));
        stars.add(SkillDisplays.spin(level, feet, LIGHT, 2.8f, 0.02f, 45, 3, duration, player, 0.036));
        stars.removeIf(java.util.Objects::isNull);
        AURA_STARS.put(player.getUUID(), stars);
        SkillVisuals.shockwave(level, player.position(), 0.2, auraRadius(player), 3, SkillVisuals.GOLD);
        SkillVisuals.spiral(level, player.position(), 1.0, 2.4, 2, 28, ParticleTypes.END_ROD);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.3f);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6f, 1.5f);
        return true;
    }

    // Once a second while the aura lasts, so players who come close get it too
    private static void auraPulse(ServerLevel level, ServerPlayer player) {
        boolean might = Progression.has(player, Upgrade.HOLY_AURA_MIGHT);
        boolean martyr = Progression.has(player, Upgrade.MARTYR);
        for (ServerPlayer ally : allies(player, auraRadius(player))) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, true, true));
            // Martyr heals the others faster
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, martyr && ally != player ? 1 : 0, true, true));
            if (might) {
                ally.getPersistentData().putLong(MIGHT_UNTIL_TAG, level.getGameTime() + 30);
            }
        }
        if (LegendaryPower.has(player, LegendaryPower.RADIANT_AURA)) {
            float damage = damage(player, 0.5, Upgrade.HOLY_AURA);
            for (LivingEntity target : ClassSkills.enemies(level, player.position(), auraRadius(player))) {
                hit(player, target, damage);
                level.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 4, 0.3, 0.4, 0.3, 0);
            }
        }
    }

    private static void auraGlow(ServerLevel level, ServerPlayer player, long now) {
        if (now % 2 != 0) {
            return;
        }
        // Three points of light going round you at the edge of your feet, and a faint ring out where it reaches
        double angle = now * 0.25;
        for (int i = 0; i < 3; i++) {
            double a = angle + Math.PI * 2 * i / 3;
            level.sendParticles(SkillVisuals.GOLD, player.getX() + Math.cos(a) * 1.0, player.getY() + 0.15, player.getZ() + Math.sin(a) * 1.0, 1, 0, 0, 0, 0);
        }
        if (now % 20 == 0) {
            SkillVisuals.ring(level, player.position(), 0.15, auraRadius(player), 32, ParticleTypes.WAX_ON);
        }
    }

    // A burst of light that heals you and the players near you, and hurts the enemies close by. The undead twice as much
    static boolean holyLight(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Holy Light rank heals 2% more of your max health, and each Devotion rank 5% more on top
        float share = (float) (LIGHT_HEAL + 0.02 * Upgrade.utility(Progression.rank(player, Upgrade.HOLY_LIGHT))) * devotion(player);
        // Inner Light heals only you, twice as much. Martyr heals the others more
        boolean inner = Progression.has(player, Upgrade.HOLY_LIGHT_INNER);
        boolean martyr = Progression.has(player, Upgrade.MARTYR);
        for (ServerPlayer ally : inner ? List.of(player) : allies(player, LIGHT_HEAL_RADIUS)) {
            ally.heal(ally.getMaxHealth() * share * (inner ? 2 : 1) * (martyr && ally != player ? MARTYR_HEAL : 1));
            level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 2, ally.getZ(), 3, 0.3, 0.2, 0.3, 0);
            SkillVisuals.spiral(level, ally.position(), 0.7, 2.2, 1.5, 18, SkillVisuals.GOLD);
        }
        float damage = damage(player, 1.2, Upgrade.HOLY_LIGHT);
        for (LivingEntity target : ClassSkills.enemies(level, player.position(), lightHitRadius(player))) {
            hit(player, target, target.isInvertedHealAndHarm() ? damage * 2 : damage);
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
        }
        // A wide pillar of light coming down on you and a flash going out
        pillar(level, player.position(), 2.4f, 14, (float) lightHitRadius(player));
        SkillVisuals.burst(level, player.position(), 1.0, 24, 0.25, ParticleTypes.END_ROD);
        SkillVisuals.shockwave(level, player.position(), 0.2, lightHitRadius(player), 2, SkillVisuals.GOLD);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.6f);
        return true;
    }

    // Holy ground where you stand: it hurts enemies on it a little at a time, and heals you while you stand on it
    static boolean consecration(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Consecration rank adds 20% damage and a fifth of a block
        int rank = Progression.rank(player, Upgrade.CONSECRATION);
        double radius = (CONSECRATION_RADIUS + 0.2 * Upgrade.utility(rank)) * (Progression.has(player, Upgrade.CONSECRATION_BIG) ? WIDER : 1);
        add(player, new Effect(Kind.CONSECRATION, player.position(), damage(player, 0.5, Upgrade.CONSECRATION), radius, CONSECRATION_PULSES,
                level.getGameTime()));
        SkillVisuals.shockwave(level, player.position(), 0.1, radius, 3, SkillVisuals.GOLD);
        // A star of light on the ground: two big squares turning one way and two small ones the other way
        Vec3 ground = player.position().add(0, 0.03, 0);
        int life = CONSECRATION_PULSES * CONSECRATION_TIME;
        float size = (float) radius * 1.75f;
        SkillDisplays.spin(level, ground, LIGHT, size, 0.02f, 0, 0.6f, life, null, 0);
        SkillDisplays.spin(level, ground.add(0, 0.006, 0), LIGHT, size, 0.02f, 45, 0.6f, life, null, 0);
        SkillDisplays.spin(level, ground.add(0, 0.012, 0), LIGHT, size * 0.5f, 0.02f, 0, -1.2f, life, null, 0);
        SkillDisplays.spin(level, ground.add(0, 0.018, 0), LIGHT, size * 0.5f, 0.02f, 45, -1.2f, life, null, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    private static void consecrationPulse(ServerLevel level, ServerPlayer player, Effect effect) {
        double radius = effect.extra;
        for (LivingEntity target : ClassSkills.enemies(level, effect.center, radius)) {
            hit(player, target, effect.damage);
            level.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY() + 0.3, target.getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
        if (player.position().distanceTo(effect.center) <= radius + 0.5) {
            // Healing Ground heals twice as much
            player.heal(player.getMaxHealth() * CONSECRATION_HEAL * devotion(player) * (Progression.has(player, Upgrade.CONSECRATION_HEALING) ? 2 : 1));
        }
        // The edge, a glow over the ground and specks of light rising from it
        SkillVisuals.ring(level, effect.center, 0.1, radius, 28, SkillVisuals.GOLD);
        level.sendParticles(ParticleTypes.WAX_ON, effect.center.x, effect.center.y + 0.1, effect.center.z, 10, radius / 2, 0.02, radius / 2, 0);
        level.sendParticles(ParticleTypes.END_ROD, effect.center.x, effect.center.y + 0.2, effect.center.z, 4, radius / 2, 0.05, radius / 2, 0.03);
    }

    // A spear of light thrown straight ahead. It goes through every enemy in a line until it hits a wall, and dazzles them
    static boolean spearOfJudgement(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = level.clip(new ClipContext(eyes, eyes.add(look.scale(SPEAR_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getLocation();
        Vec3 start = eyes.subtract(0, 0.3, 0);
        double distance = end.distanceTo(start);
        Effect spear = new Effect(Kind.SPEAR, start, damage(player, 2.6, Upgrade.SPEAR_OF_JUDGEMENT), distance,
                Math.max(1, (int) Math.ceil(distance / SPEAR_SPEED)), level.getGameTime() + 1);
        spear.way = look;
        // A long glowing rod of light, flying point first
        spear.display = SkillDisplays.block(level, start.add(look.scale(1.5)), Blocks.END_ROD.defaultBlockState(),
                SkillDisplays.bar(look, 3, (float) SPEAR_SPEED), spear.steps + 3);
        if (spear.display != null) {
            SkillDisplays.smoothMoves(spear.display, 1);
            SkillDisplays.glow(spear.display, GOLD_GLOW);
        }
        add(player, spear);
        level.sendParticles(ParticleTypes.END_ROD, start.x, start.y, start.z, 8, 0.15, 0.15, 0.15, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.8f);
        return true;
    }

    // The spear flies on SPEAR_SPEED and hits everything along the way, once each
    private static void spearTick(ServerLevel level, ServerPlayer player, Effect effect) {
        double from = effect.step * SPEAR_SPEED;
        double to = Math.min(effect.extra, from + SPEAR_SPEED);
        for (double travelled = from; travelled <= to; travelled += 0.5) {
            Vec3 point = effect.center.add(effect.way.scale(travelled));
            for (LivingEntity target : ClassSkills.enemies(level, point.subtract(0, 0.5, 0), 0.7)) {
                if (effect.hit.add(target.getId())) {
                    hit(player, target, effect.damage);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4));
                    // Holy Fire
                    if (Progression.has(player, Upgrade.SPEAR_FIRE)) {
                        target.igniteForSeconds(4);
                    }
                    level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1, target.getZ(), 8, 0.2, 0.3, 0.2, 0.1);
                    level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7f, 1.5f);
                }
            }
        }
        // The middle of the rod, a little behind the point
        Vec3 head = effect.center.add(effect.way.scale(Math.max(SPEAR_SPEED / 2, to - SPEAR_SPEED / 2)));
        if (effect.display != null) {
            effect.display.moveTo(head.x, head.y, head.z);
        }
        SkillVisuals.line(level, effect.center.add(effect.way.scale(from)), effect.center.add(effect.way.scale(to)), 0.5, SkillVisuals.GOLD);
        if (effect.step == effect.steps - 1) {
            // It bursts into light where it stops
            Vec3 end = effect.center.add(effect.way.scale(effect.extra));
            if (effect.display != null) {
                effect.display.discard();
            }
            // Holy Blast: it explodes where it stops
            if (Progression.has(player, Upgrade.SPEAR_BLAST)) {
                for (LivingEntity target : ClassSkills.enemies(level, end, HOLY_BLAST_RADIUS)) {
                    hit(player, target, effect.damage);
                }
                SkillVisuals.shockwave(level, end, 0, HOLY_BLAST_RADIUS, 2, SkillVisuals.GOLD);
                level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
            }
            SkillVisuals.burst(level, end.subtract(0, 0.5, 0), 0.5, 14, 0.15, ParticleTypes.END_ROD);
            level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
            level.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
        }
    }

    // Pillars of light come down from the sky on the enemies around you, one at a time. Divine Storm doubles them
    static boolean heavensJudgement(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each rank adds 20% damage, and every second rank a pillar
        int rank = Progression.rank(player, Upgrade.HEAVENS_JUDGEMENT);
        int pillars = JUDGEMENT_PILLARS + Upgrade.utility(rank) / 2;
        if (LegendaryPower.has(player, LegendaryPower.DIVINE_STORM)) {
            pillars *= 2;
        }
        add(player, new Effect(Kind.JUDGEMENT, player.position(), damage(player, 1.5, Upgrade.HEAVENS_JUDGEMENT), 0, pillars,
                level.getGameTime() + 10));
        SkillVisuals.ring(level, player.position(), 0.1, JUDGEMENT_RADIUS, 48, SkillVisuals.GOLD);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + PILLAR_HEIGHT, player.getZ(), 40, JUDGEMENT_RADIUS / 2, 0.3,
                JUDGEMENT_RADIUS / 2, 0.01);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    // One pillar, on a random enemy around you, or a random spot if there are none
    private static void judgementPillar(ServerLevel level, ServerPlayer player, Effect effect) {
        RandomSource random = player.getRandom();
        List<LivingEntity> around = ClassSkills.enemies(level, player.position(), JUDGEMENT_RADIUS);
        Vec3 spot;
        // Focused Judgement: every pillar on the same enemy, for as long as it is alive and in reach
        if (Progression.has(player, Upgrade.HEAVENS_FOCUS) && !around.isEmpty()) {
            if (effect.focus == null || !effect.focus.isAlive() || !around.contains(effect.focus)) {
                effect.focus = around.get(random.nextInt(around.size()));
            }
            spot = effect.focus.position();
        } else if (!around.isEmpty()) {
            spot = around.get(random.nextInt(around.size())).position();
        } else {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * JUDGEMENT_RADIUS;
            spot = player.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        }
        pillar(level, spot);
        List<LivingEntity> struck = ClassSkills.enemies(level, spot, PILLAR_RADIUS);
        for (LivingEntity target : struck) {
            hit(player, target, effect.damage);
        }
        // Pillars of Light: a pillar that hits something heals you
        if (!struck.isEmpty() && Progression.has(player, Upgrade.HEAVENS_LIGHT)) {
            player.heal(player.getMaxHealth() * PILLAR_HEAL);
        }
    }

    private static void pillar(ServerLevel level, Vec3 spot) {
        pillar(level, spot, 1.6f, (float) PILLAR_HEIGHT, (float) PILLAR_RADIUS);
    }

    // A pillar of light shooting down from the sky onto the spot: it comes down in a moment, swells, and thins out to
    // nothing. Gold glass around a white core, a flash of light spreading over the ground, and specks of light
    private static void pillar(ServerLevel level, Vec3 spot, float width, float height, float radius) {
        Display glow = SkillDisplays.block(level, spot, LIGHT, SkillDisplays.column(width * 0.3f, height, height), 16);
        SkillDisplays.later(glow, 1, SkillDisplays.column(width, 0, height), 2);
        SkillDisplays.later(glow, 6, SkillDisplays.column(0, 0, height), 8);
        Display core = SkillDisplays.block(level, spot, CORE, SkillDisplays.column(width * 0.1f, height, height), 16);
        SkillDisplays.later(core, 1, SkillDisplays.column(width * 0.35f, 0, height), 2);
        SkillDisplays.later(core, 6, SkillDisplays.column(0, 0, height), 8);
        Display ground = SkillDisplays.block(level, spot.add(0, 0.02, 0), LIGHT, SkillDisplays.plate(width * 0.5f, 0.02f, 45), 14);
        SkillDisplays.later(ground, 3, SkillDisplays.plate(radius * 2, 0.02f, 90), 3);
        SkillDisplays.later(ground, 7, SkillDisplays.plate(0, 0.02f, 135), 6);
        for (double y = 0; y < height; y += 0.7) {
            level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + y, spot.z, 1, width * 0.3, 0.1, width * 0.3, 0.02);
        }
        level.sendParticles(ParticleTypes.FLASH, spot.x, spot.y + 0.5, spot.z, 1, 0, 0, 0, 0);
        SkillVisuals.burst(level, spot, 0.2, 16, 0.25, ParticleTypes.END_ROD);
        SkillVisuals.ring(level, spot, 0.1, radius, 16, SkillVisuals.GOLD);
        level.sendParticles(ParticleTypes.WAX_ON, spot.x, spot.y + 0.2, spot.z, 8, 0.6, 0.1, 0.6, 0);
        RandomSource random = level.getRandom();
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f + random.nextFloat() * 0.4f);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3f, 1.8f);
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(AURA_UNTIL_TAG) > now) {
            auraGlow(level, player, now);
            if (now % 20 == 0) {
                auraPulse(level, player);
            }
        }
        List<Effect> effects = EFFECTS.get(player.getUUID());
        if (effects == null) {
            return;
        }
        for (Iterator<Effect> it = effects.iterator(); it.hasNext(); ) {
            Effect effect = it.next();
            if (now < effect.next) {
                continue;
            }
            switch (effect.kind) {
                case HAMMER -> {
                    hammerTick(level, player, effect);
                    effect.next = now + 1;
                }
                case CONSECRATION -> {
                    consecrationPulse(level, player, effect);
                    effect.next = now + CONSECRATION_TIME;
                }
                case JUDGEMENT -> {
                    judgementPillar(level, player, effect);
                    effect.next = now + JUDGEMENT_TIME;
                }
                case SPEAR -> {
                    spearTick(level, player, effect);
                    effect.next = now + 1;
                }
            }
            effect.step++;
            if (effect.step >= effect.steps) {
                it.remove();
            }
        }
        if (effects.isEmpty()) {
            EFFECTS.remove(player.getUUID());
        }
    }

    // The class bonus: a paladin with an enemy close by takes less damage, since that is where they belong
    static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && Progression.playerClass(player) == PlayerClass.PALADIN
                && !ClassSkills.enemies(player.serverLevel(), player.position(), GUARD_RANGE).isEmpty()) {
            event.setNewDamage(event.getNewDamage() * (1 - GUARD));
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            // Martyr takes more, Unbreakable takes less when low
            if (Progression.has(player, Upgrade.MARTYR)) {
                event.setNewDamage(event.getNewDamage() * (1 + MARTYR_TAKEN));
            }
            if (Progression.has(player, Upgrade.UNBREAKABLE) && player.getHealth() < player.getMaxHealth() * UNBREAKABLE_BELOW) {
                event.setNewDamage(event.getNewDamage() * (1 - UNBREAKABLE));
            }
        }
        // Aura of Might: players in the aura hit harder
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getEntity() != attacker
                && attacker.getPersistentData().getLong(MIGHT_UNTIL_TAG) > attacker.serverLevel().getGameTime()) {
            event.setNewDamage(event.getNewDamage() * (1 + MIGHT));
        }
    }

    // Holy Wrath: every kind of healing is half as strong
    static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Progression.has(player, Upgrade.HOLY_WRATH)) {
            event.setAmount(event.getAmount() * WRATH_HEAL);
        }
    }

    // Retribution: each rank gives a 4% chance that the one who hit you gets a pillar of light on them
    static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == player || !Minions.isEnemy(attacker)) {
            return;
        }
        int rank = Progression.rank(player, Upgrade.RETRIBUTION);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (rank <= 0 || player.getPersistentData().getLong(RETRIBUTION_READY_TAG) > now || player.getRandom().nextInt(100) >= 4 * rank) {
            return;
        }
        player.getPersistentData().putLong(RETRIBUTION_READY_TAG, now + RETRIBUTION_COOLDOWN);
        pillar(level, attacker.position());
        hit(player, attacker, (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2));
    }
}
