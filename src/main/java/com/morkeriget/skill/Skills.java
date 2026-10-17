package com.morkeriget.skill;

import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Skills the player can use. They run on the server so monsters really take the damage
public final class Skills {
    private static final double WHIRLWIND_RADIUS = 4.0;
    private static final double FROST_NOVA_RADIUS = 5.0;
    // 20 ticks is one second
    private static final int FROST_NOVA_DURATION = 3 * 20;
    private static final double BLINK_DISTANCE = 8.0;

    private Skills() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Skills::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Mana::onPlayerTick);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(SkillPayload.TYPE, SkillPayload.STREAM_CODEC, SkillPayload::handle)
                // The lambda keeps ManaBar (client only) from being loaded on a dedicated server
                .playToClient(ManaPayload.TYPE, ManaPayload.STREAM_CODEC, (payload, context) -> ManaBar.set(payload));
    }

    public static void use(ServerPlayer player, Skill skill) {
        long now = player.serverLevel().getGameTime();
        String readyTag = "morkeriget_" + skill.name().toLowerCase() + "_ready";
        long ready = player.getPersistentData().getLong(readyTag);
        if (now < ready) {
            long seconds = (ready - now + 19) / 20;
            player.displayClientMessage(Component.literal(skill.displayName + " er klar om " + seconds + " sek."), true);
            return;
        }
        if (Mana.get(player) < skill.manaCost()) {
            player.displayClientMessage(Component.literal("Ikke nok mana til " + skill.displayName), true);
            return;
        }
        boolean used = switch (skill) {
            case WHIRLWIND -> whirlwind(player);
            case FROST_NOVA -> frostNova(player);
            case BLINK -> blink(player);
        };
        if (used) {
            Mana.spend(player, skill.manaCost());
            player.getPersistentData().putLong(readyTag, now + skill.cooldown());
        }
    }

    // Spins around and hits every monster close to you
    private static boolean whirlwind(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Whirlwind upgrade rank adds 20% damage
        double upgrade = 1 + 0.2 * Progression.rank(player, Upgrade.WHIRLWIND);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5 * upgrade);
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
        return true;
    }

    // A ring of ice that hurts and freezes every monster around you, so they can barely move for a few seconds
    private static boolean frostNova(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Frost Nova upgrade rank adds 1 block of radius and half a second of freezing
        int rank = Progression.rank(player, Upgrade.FROST_NOVA);
        double radius = FROST_NOVA_RADIUS + rank;
        int duration = FROST_NOVA_DURATION + rank * 10;
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                entity -> entity instanceof Enemy && entity.isAlive())) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 9));
            // Makes them shake and turn blue like in powder snow. It thaws by 2 every tick
            target.setTicksFrozen(target.getTicksRequiredToFreeze() + duration * 2);
        }

        // Snowflakes flying outwards in a ring (count 0 means the offsets are used as speed)
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2 * i / 48;
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.5, player.getZ(),
                    0, Math.cos(angle), 0, Math.sin(angle), 0.4);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // Teleports you a few blocks in the direction you are looking. Stops in front of walls
    private static boolean blink(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // Each Blink upgrade rank adds 2 blocks
        double maxDistance = BLINK_DISTANCE + 2 * Progression.rank(player, Upgrade.BLINK);
        BlockHitResult hit = level.clip(new ClipContext(eyes, eyes.add(look.scale(maxDistance)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 from = player.position();
        double furthest = hit.getLocation().distanceTo(eyes) - 0.5;

        // Start at the furthest point and move back until the player fits. If you look down at the ground,
        // the spot is under the floor, so it also tries lifting it up a bit
        for (double distance = furthest; distance >= 1; distance -= 0.5) {
            Vec3 point = eyes.add(look.scale(distance)).subtract(0, player.getEyeHeight(), 0);
            for (double lift = 0; lift <= player.getEyeHeight() + 0.5; lift += 0.25) {
                Vec3 feet = point.add(0, lift, 0);
                if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(from)))) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.05);
                    player.connection.teleport(feet.x, feet.y, feet.z, player.getYRot(), player.getXRot());
                    player.resetFallDistance();
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1, feet.z, 30, 0.3, 0.6, 0.3, 0.05);
                    level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
                    return true;
                }
            }
        }
        player.displayClientMessage(Component.literal("Ingen plads at blinke til"), true);
        return false;
    }
}
