package com.morkeriget.skill;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Config;
import com.morkeriget.loot.ItemSet;
import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.loot.MythicItem;
import com.morkeriget.loot.SetBonuses;
import com.morkeriget.progress.Paragon;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    private static final BlockParticleOption ICE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());

    private Skills() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Skills::registerPayloads);
        SkillEntities.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(Mana::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ClassSkills::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ClassSkills::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ClassSkills::onWolfBite);
        NeoForge.EVENT_BUS.addListener(Minions::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Minions::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(NecroSkills::onDamagePre);
        NeoForge.EVENT_BUS.addListener(NecroSkills::onDeath);
        NeoForge.EVENT_BUS.addListener(TreeSkills::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TreeSkills::onDamagePost);
        NeoForge.EVENT_BUS.addListener(TreeSkills::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> forget(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> forget(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (event.getEntity() instanceof Player player) {
                forget(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(Skills::onClone);
        NeoForge.EVENT_BUS.addListener(SummonGear::onInteract);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(SkillPayload.TYPE, SkillPayload.STREAM_CODEC, SkillPayload::handle)
                // The lambda keeps ManaBar (client only) from being loaded on a dedicated server
                .playToClient(ManaPayload.TYPE, ManaPayload.STREAM_CODEC, (payload, context) -> ManaBar.set(payload))
                .playToClient(CooldownPayload.TYPE, CooldownPayload.STREAM_CODEC, (payload, context) -> SkillBar.set(payload));
    }

    private static void forget(Player player) {
        TreeSkills.forget(player);
        ClassSkills.forget(player);
    }

    // The game makes a new player object after death and when leaving the End, and only copies some of the saved data.
    // Cooldowns carry over so they match the skill bar. Mana is full after death, like health, but not after the End
    private static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag now = event.getEntity().getPersistentData();
        for (String key : old.getAllKeys()) {
            if (key.startsWith("morkeriget_") && key.endsWith("_ready")) {
                now.putLong(key, old.getLong(key));
            }
        }
        if (!event.isWasDeath() && event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer player) {
            Mana.copy(original, player);
        }
    }

    // How much shorter all skill cooldowns are, also shown in the equipment screen. Four Stormcaller pieces make them
    // 30% shorter, each Time Warp rank 4%, each paragon Cooldown point 0.2% and the Jester's Crown 25%
    public static float cooldownScale(ServerPlayer player) {
        float scale = SetBonuses.has(player, ItemSet.STORMCALLER, 4) ? 0.7f : 1;
        scale *= 1 - 0.04f * Progression.rank(player, Upgrade.TIME_WARP);
        scale *= 1 - 0.002f * Progression.rank(player, Paragon.COOLDOWN);
        return MythicItem.has(player, MythicItem.JESTERS_CROWN) ? scale * 0.75f : scale;
    }

    public static void use(ServerPlayer player, Skill skill) {
        // Nothing from the death screen or in spectator mode
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        // Skills belong to a class and have to be unlocked in its tree. The potions are for everyone
        if (!skill.isPotion()) {
            PlayerClass playerClass = Progression.playerClass(player);
            if (playerClass == null) {
                player.displayClientMessage(Component.literal("Vælg en klasse først - tryk K"), true);
                return;
            }
            if (!Progression.hasUnlocked(player, skill)) {
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
        // Each Arcane Flow rank makes skills 5% cheaper
        float manaCost = skill.manaCost() * (1 - 0.05f * Progression.rank(player, Upgrade.ARCANE_FLOW));
        // The Jester's Crown takes off another 25%, and 25% of the cooldown below
        boolean crown = !skill.isPotion() && MythicItem.has(player, MythicItem.JESTERS_CROWN);
        if (crown) {
            manaCost *= 0.75f;
        }
        if (Mana.get(player) < manaCost) {
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
            // The third row of the skill trees
            case EARTHQUAKE -> TreeSkills.earthquake(player);
            case RUSH -> TreeSkills.rush(player);
            case METEOR -> TreeSkills.meteor(player);
            case ICE_ARMOR -> TreeSkills.iceArmor(player);
            case EXPLOSIVE_ARROW -> TreeSkills.explosiveArrow(player);
            case BEAR_TRAP -> TreeSkills.bearTrap(player);
            case BLOOD_NOVA -> TreeSkills.bloodNova(player);
            case BONE_PRISON -> TreeSkills.bonePrison(player);
            // The fourth row
            case THUNDERSTORM -> TreeSkills.thunderstorm(player);
            case DOOM_SLAM -> TreeSkills.doomSlam(player);
            case ARROW_HURRICANE -> TreeSkills.arrowHurricane(player);
            case ARMY_OF_THE_DEAD -> TreeSkills.armyOfTheDead(player);
            case HEALTH_POTION -> healthPotion(player);
            case MANA_POTION -> manaPotion(player);
        };
        if (used) {
            Mana.spend(player, manaCost);
            // Not for the potions
            float cooldownScale = 1;
            if (!skill.isPotion()) {
                cooldownScale *= cooldownScale(player);
                // Ranks above 10 in the skill itself
                Upgrade upgrade = Upgrade.forSkill(skill);
                if (upgrade != null) {
                    cooldownScale *= Upgrade.cooldownScale(Progression.rank(player, upgrade));
                }
            }
            int cooldown = Math.round(skill.cooldown() * cooldownScale);
            player.getPersistentData().putLong(readyTag, now + cooldown);
            PacketDistributor.sendToPlayer(player, new CooldownPayload(skill, cooldown));
        }
    }

    // Spins around and hits every monster close to you. With Maelstrom it pulls them in instead of pushing them away
    private static boolean whirlwind(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean maelstrom = LegendaryPower.has(player, LegendaryPower.MAELSTROM);
        // Each Whirlwind upgrade rank adds 20% damage
        double upgrade = 1 + 0.2 * Progression.rank(player, Upgrade.WHIRLWIND);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.1 * upgrade * (maelstrom ? 1.3 : 1));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WHIRLWIND_RADIUS),
                entity -> Minions.isEnemy(entity))) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 push = target.position().subtract(player.position()).normalize();
            if (maelstrom) {
                target.knockback(0.5, push.x, push.z);
            } else {
                target.knockback(0.6, -push.x, -push.z);
            }
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    6, 0.3, 0.3, 0.3, 0.2);
        }

        // Two rings of blades, a swirl of wind around you and dust kicked up from the ground
        Vec3 center = player.position();
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * 2.5, player.getY() + 1,
                    player.getZ() + Math.sin(angle) * 2.5, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * (i + 0.5) / 8;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * 1.5, player.getY() + 0.5,
                    player.getZ() + Math.sin(angle) * 1.5, 1, 0, 0, 0, 0);
        }
        SkillVisuals.spiral(level, center, 2.0, 2.0, 2, 32, ParticleTypes.CRIT);
        level.sendParticles(ParticleTypes.SMALL_GUST, player.getX(), player.getY() + 1, player.getZ(), 4, 1.2, 0.4, 1.2, 0);
        if (maelstrom) {
            // The wind pulls in, like the monsters
            SkillVisuals.implode(level, center, 0.3, WHIRLWIND_RADIUS, 20, 0.35, ParticleTypes.CLOUD);
        } else {
            SkillVisuals.burst(level, center, 0.2, 20, 0.3, ParticleTypes.CLOUD);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.3f);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
        return true;
    }

    // A ring of ice that hurts and freezes every monster around you, so they can barely move for a few seconds
    private static boolean frostNova(ServerPlayer player) {
        frostNovaAt(player, player.position());
        return true;
    }

    // The frost nova around a spot, so Frost Trail can leave one where you blinked from
    private static void frostNovaAt(ServerPlayer player, Vec3 center) {
        ServerLevel level = player.serverLevel();
        // Each Frost Nova upgrade rank adds half a block of radius and half a second of freezing
        int rank = Upgrade.utility(Progression.rank(player, Upgrade.FROST_NOVA));
        double radius = FROST_NOVA_RADIUS + rank * 0.5;
        int duration = FROST_NOVA_DURATION + rank * 10;
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center.add(0, player.getBbHeight(), 0)).inflate(radius),
                entity -> Minions.isEnemy(entity))) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 9));
            // Makes them shake and turn blue like in powder snow. It thaws by 2 every tick
            target.setTicksFrozen(target.getTicksRequiredToFreeze() + duration * 2);
            level.sendParticles(ICE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 6, 0.3, 0.4, 0.3, 0);
        }

        // Snowflakes flying outwards in a ring (count 0 means the offsets are used as speed)
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2 * i / 48;
            level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 0.5, center.z,
                    0, Math.cos(angle), 0, Math.sin(angle), 0.4);
        }
        // A slower ring of snow behind it, frost on the ground out to the edge and a cold flash in the middle
        SkillVisuals.burst(level, center, 0.2, 24, 0.25, ParticleTypes.ITEM_SNOWBALL);
        SkillVisuals.shockwave(level, center, 0.1, radius, 3, SkillVisuals.FROST_FADE);
        SkillVisuals.ring(level, center, 0.3, radius, 20, ICE);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1, center.z, 12, 0.3, 0.5, 0.3, 0.08);
        level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 1, center.z, 15, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    // Teleports you a few blocks in the direction you are looking. Stops in front of walls
    private static boolean blink(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // Each Blink upgrade rank adds 2 blocks
        double maxDistance = BLINK_DISTANCE + 2 * Upgrade.utility(Progression.rank(player, Upgrade.BLINK));
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
                    // A purple puff where you were, a streak of light along the way and a flash of sparks where you land
                    level.sendParticles(SkillVisuals.ARCANE, from.x, from.y + 1, from.z, 15, 0.3, 0.6, 0.3, 0);
                    level.sendParticles(ParticleTypes.WITCH, from.x, from.y + 1, from.z, 8, 0.3, 0.5, 0.3, 0.05);
                    SkillVisuals.line(level, from.add(0, 1, 0), feet.add(0, 1, 0), 0.5, ParticleTypes.END_ROD);
                    SkillVisuals.line(level, from.add(0, 0.6, 0), feet.add(0, 0.6, 0), 0.7, SkillVisuals.ARCANE);
                    SkillVisuals.burst(level, feet, 0.1, 16, 0.15, ParticleTypes.END_ROD);
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, feet.x, feet.y + 1, feet.z, 10, 0.3, 0.6, 0.3, 0.1);
                    level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.5f);
                    level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
                    level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.6f);
                    if (LegendaryPower.has(player, LegendaryPower.FROST_TRAIL)) {
                        frostNovaAt(player, from);
                        frostNovaAt(player, feet);
                    }
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
        // Each Chain Lightning upgrade rank adds 15% damage and one more jump, and Storm Eye three more
        int rank = Progression.rank(player, Upgrade.CHAIN_LIGHTNING);
        int jumps = CHAIN_LIGHTNING_JUMPS + Upgrade.utility(rank) + (LegendaryPower.has(player, LegendaryPower.STORM_EYE) ? 3 : 0);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2 * (1 + 0.15 * rank));
        List<LivingEntity> hit = new ArrayList<>();
        LivingEntity target = closestEnemy(level, player, CHAIN_LIGHTNING_RANGE, hit);
        if (target == null) {
            player.displayClientMessage(Component.literal("Ingen fjender i nærheden"), true);
            return false;
        }
        Vec3 from = player.position().add(0, player.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 10, 0.3, 0.3, 0.3, 0.2);
        while (target != null && hit.size() <= jumps) {
            hit.add(target);
            Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
            sparks(level, from, to);
            target.hurt(player.damageSources().playerAttack(player), damage);
            // A crackle on each monster, a little higher for every jump
            level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4f,
                    Math.min(2.0f, 1.4f + 0.1f * hit.size()));
            from = to;
            target = closestEnemy(level, target, CHAIN_LIGHTNING_JUMP_RANGE, hit);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.3f, 2.0f);
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

    // A jagged bolt of electric sparks between two points, and a small burst of sparks where it hits
    private static void sparks(ServerLevel level, Vec3 from, Vec3 to) {
        SkillVisuals.zigzag(level, from, to, ParticleTypes.ELECTRIC_SPARK, SkillVisuals.SPARK);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.2, 0.3, 0.2, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 3, 0.2, 0.2, 0.2, 0.05);
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
        SkillVisuals.spiral(level, player.position(), 0.7, 2.0, 1.5, 18, SkillVisuals.LIFE);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 6, 0.4, 0.5, 0.4, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.2f);
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
        SkillVisuals.spiral(level, player.position(), 0.7, 2.0, 1.5, 18, SkillVisuals.MANA);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.5, 0.3, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.3f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.8f);
        return true;
    }

    // Makes every skill ready to use again, but not the potions. Used by the Starless Ring
    public static void resetCooldowns(ServerPlayer player) {
        for (Skill skill : Skill.values()) {
            if (!skill.isPotion()) {
                player.getPersistentData().remove("morkeriget_" + skill.name().toLowerCase() + "_ready");
                PacketDistributor.sendToPlayer(player, new CooldownPayload(skill, 0));
            }
        }
    }

    // A roar that makes you stronger and tougher for a while and weakens the monsters around you
    private static boolean battleCry(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Battle Cry upgrade rank makes it last one second longer
        int duration = BATTLE_CRY_DURATION + 20 * Upgrade.utility(Progression.rank(player, Upgrade.BATTLE_CRY));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BATTLE_CRY_RADIUS),
                entity -> Minions.isEnemy(entity))) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
                    3, 0.3, 0.2, 0.3, 0);
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    4, 0.3, 0.4, 0.3, 0.01);
        }

        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(),
                    0, Math.cos(angle), 0.1, Math.sin(angle), 0.8);
        }
        // A golden shockwave out to where it reaches, a swirl going up around you and dust blown away from your feet
        Vec3 center = player.position();
        SkillVisuals.shockwave(level, center, 0.2, BATTLE_CRY_RADIUS, 3, SkillVisuals.GOLD);
        SkillVisuals.spiral(level, center, 1.0, 2.4, 2, 28, SkillVisuals.RAGE);
        SkillVisuals.burst(level, center, 0.1, 16, 0.3, ParticleTypes.POOF);
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.5, 0.1, 0.5, 0.03);
        player.getPersistentData().putLong(SkillVisuals.BATTLE_CRY_UNTIL_TAG, level.getGameTime() + duration);
        level.playSound(null, player.blockPosition(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.3f);
        return true;
    }
}
