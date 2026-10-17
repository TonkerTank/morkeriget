package com.morkeriget.skill;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Config;
import com.morkeriget.loot.ItemSet;
import com.morkeriget.loot.SetBonuses;
import com.morkeriget.progress.PlayerClass;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Skills the player can use. They run on the server so monsters really take the damage
public final class Skills {
    private static final double WHIRLWIND_RADIUS = 4.0;
    private static final double FROST_NOVA_RADIUS = 5.0;
    // 20 ticks is one second
    private static final int FROST_NOVA_DURATION = 3 * 20;
    private static final double BLINK_DISTANCE = 8.0;
    // Chain Lightning hits the first monster within this range and then jumps this many times to monsters close by
    private static final double CHAIN_LIGHTNING_RANGE = 12.0;
    private static final double CHAIN_LIGHTNING_JUMP_RANGE = 6.0;
    private static final int CHAIN_LIGHTNING_JUMPS = 3;
    private static final double BATTLE_CRY_RADIUS = 8.0;
    private static final int BATTLE_CRY_DURATION = 6 * 20;

    private Skills() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Skills::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Mana::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ClassSkills::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ClassSkills::onEntityTick);
        NeoForge.EVENT_BUS.addListener(Minions::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Minions::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(NecroSkills::onDamagePre);
        NeoForge.EVENT_BUS.addListener(NecroSkills::onDeath);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(SkillPayload.TYPE, SkillPayload.STREAM_CODEC, SkillPayload::handle)
                // The lambda keeps ManaBar (client only) from being loaded on a dedicated server
                .playToClient(ManaPayload.TYPE, ManaPayload.STREAM_CODEC, (payload, context) -> ManaBar.set(payload))
                .playToClient(CooldownPayload.TYPE, CooldownPayload.STREAM_CODEC, (payload, context) -> SkillBar.set(payload));
    }

    public static void use(ServerPlayer player, Skill skill) {
        // Skills belong to a class. The potions are for everyone
        if (!skill.isPotion()) {
            PlayerClass playerClass = Progression.playerClass(player);
            if (playerClass == null) {
                player.displayClientMessage(Component.literal("Vælg en klasse først - tryk K"), true);
                return;
            }
            if (!playerClass.skills.contains(skill)) {
                return;
            }
        }
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
            case CHAIN_LIGHTNING -> chainLightning(player);
            case BATTLE_CRY -> battleCry(player);
            // The newer class skills live in ClassSkills, so this file doesn't get too long
            case LEAP -> ClassSkills.leap(player);
            case FRENZY -> ClassSkills.frenzy(player);
            case FIREBALL -> ClassSkills.fireball(player);
            case MULTISHOT -> ClassSkills.multishot(player);
            case ARROW_RAIN -> ClassSkills.arrowRain(player);
            case SMOKE_BOMB -> ClassSkills.smokeBomb(player);
            case WOLVES -> ClassSkills.wolves(player);
            case BONE_SPEAR -> NecroSkills.boneSpear(player);
            case RAISE_SKELETONS -> NecroSkills.raiseSkeletons(player);
            case BONE_ARMOR -> NecroSkills.boneArmor(player);
            case CURSE -> NecroSkills.curse(player);
            case HEALTH_POTION -> healthPotion(player);
            case MANA_POTION -> manaPotion(player);
        };
        if (used) {
            Mana.spend(player, skill.manaCost());
            // Four Stormcaller pieces make skill cooldowns 30% shorter (not the potions)
            boolean shorter = !skill.isPotion() && SetBonuses.has(player, ItemSet.STORMCALLER, 4);
            int cooldown = shorter ? Math.round(skill.cooldown() * 0.7f) : skill.cooldown();
            player.getPersistentData().putLong(readyTag, now + cooldown);
            PacketDistributor.sendToPlayer(player, new CooldownPayload(skill, cooldown));
        }
    }

    // Spins around and hits every monster close to you
    private static boolean whirlwind(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Whirlwind upgrade rank adds 20% damage
        double upgrade = 1 + 0.2 * Progression.rank(player, Upgrade.WHIRLWIND);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5 * upgrade);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WHIRLWIND_RADIUS),
                entity -> Minions.isEnemy(entity))) {
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
        // Each Frost Nova upgrade rank adds half a block of radius and half a second of freezing
        int rank = Progression.rank(player, Upgrade.FROST_NOVA);
        double radius = FROST_NOVA_RADIUS + rank * 0.5;
        int duration = FROST_NOVA_DURATION + rank * 10;
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                entity -> Minions.isEnemy(entity))) {
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

    // Lightning hits the closest monster and jumps on to the next one close to it, never hitting the same one twice
    private static boolean chainLightning(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Chain Lightning upgrade rank adds 15% damage and one more jump
        int rank = Progression.rank(player, Upgrade.CHAIN_LIGHTNING);
        int jumps = CHAIN_LIGHTNING_JUMPS + rank;
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2 * (1 + 0.15 * rank));
        List<LivingEntity> hit = new ArrayList<>();
        LivingEntity target = closestEnemy(level, player, CHAIN_LIGHTNING_RANGE, hit);
        if (target == null) {
            player.displayClientMessage(Component.literal("Ingen fjender i nærheden"), true);
            return false;
        }
        Vec3 from = player.position().add(0, player.getBbHeight() * 0.6, 0);
        while (target != null && hit.size() <= jumps) {
            hit.add(target);
            Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
            sparks(level, from, to);
            target.hurt(player.damageSources().playerAttack(player), damage);
            from = to;
            target = closestEnemy(level, target, CHAIN_LIGHTNING_JUMP_RANGE, hit);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.6f);
        return true;
    }

    // The closest living monster around the entity that it can see and that isn't in the list
    private static LivingEntity closestEnemy(ServerLevel level, LivingEntity center, double range, List<LivingEntity> skip) {
        LivingEntity closest = null;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(range),
                entity -> Minions.isEnemy(entity) && !skip.contains(entity)
                        && entity.distanceTo(center) <= range && center.hasLineOfSight(entity))) {
            if (closest == null || entity.distanceToSqr(center) < closest.distanceToSqr(center)) {
                closest = entity;
            }
        }
        return closest;
    }

    // A line of electric sparks between two points
    private static void sparks(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int count = (int) Math.ceil(step.length() / 0.4);
        for (int i = 0; i <= count; i++) {
            Vec3 point = from.add(step.scale((double) i / count));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // Gives back part of your max health at once. Not used up if you already have full health
    private static boolean healthPotion(ServerPlayer player) {
        if (player.getHealth() >= player.getMaxHealth()) {
            player.displayClientMessage(Component.literal("Du har fuldt liv"), true);
            return false;
        }
        player.heal(player.getMaxHealth() * Config.HEALTH_POTION_HEAL.get().floatValue());
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(), 6, 0.4, 0.3, 0.4, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    // Gives mana at once. Not used up if your mana is already full
    private static boolean manaPotion(ServerPlayer player) {
        if (Mana.get(player) >= Mana.MAX) {
            player.displayClientMessage(Component.literal("Du har fuld mana"), true);
            return false;
        }
        Mana.add(player, Config.MANA_POTION_MANA.get().floatValue());
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.5);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.3f);
        return true;
    }

    // A roar that makes you stronger and tougher for a while and weakens the monsters around you
    private static boolean battleCry(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Battle Cry upgrade rank makes it last one second longer
        int duration = BATTLE_CRY_DURATION + 20 * Progression.rank(player, Upgrade.BATTLE_CRY);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BATTLE_CRY_RADIUS),
                entity -> Minions.isEnemy(entity))) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
                    3, 0.3, 0.2, 0.3, 0);
        }

        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(),
                    0, Math.cos(angle), 0.1, Math.sin(angle), 0.8);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }
}
