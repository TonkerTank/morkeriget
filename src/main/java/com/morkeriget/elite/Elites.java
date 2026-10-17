package com.morkeriget.elite;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// Some monsters spawn as elites: golden name, glowing outline, much more health and damage, and they always drop good loot.
// Each elite also gets one or two modifiers (EliteModifier) that change how it fights
public final class Elites {
    private static final String ELITE_TAG = "morkeriget_elite";
    private static final String CHECKED_TAG = "morkeriget_elite_checked";
    private static final ResourceLocation ELITE_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "elite_boost");
    // Teleporting elites jump to their target when it is further away than this
    private static final double TELEPORT_DISTANCE = 6.0;

    private Elites() {
    }

    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(Elites::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Elites::onDamagePost);
        NeoForge.EVENT_BUS.addListener(Elites::onEntityTick);
        Bosses.register();
    }

    public static boolean isElite(Entity entity) {
        return entity.getPersistentData().getBoolean(ELITE_TAG);
    }

    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster monster)) {
            return;
        }
        // Only roll once per monster, so it doesn't happen again when the chunk is loaded later
        if (monster.getPersistentData().getBoolean(CHECKED_TAG)) {
            return;
        }
        markChecked(monster);
        float roll = monster.getRandom().nextFloat();
        if (roll < Config.BOSS_CHANCE.get() && Bosses.canBeBoss(monster)) {
            Bosses.makeBoss(monster);
        } else if (roll < Config.ELITE_CHANCE.get()) {
            makeElite(monster, monster.getRandom().nextFloat() < Config.SECOND_MODIFIER_CHANCE.get() ? 2 : 1);
        }
    }

    // Makes sure the monster never rolls to become an elite, e.g. a boss's minions
    public static void markChecked(Entity entity) {
        entity.getPersistentData().putBoolean(CHECKED_TAG, true);
    }

    // Turns the monster into an elite with this many random modifiers, and returns which ones it got
    static List<EliteModifier> makeElite(Monster monster, int modifierCount) {
        monster.getPersistentData().putBoolean(ELITE_TAG, true);

        boost(monster.getAttribute(Attributes.MAX_HEALTH), Config.ELITE_HEALTH.get() - 1);
        boost(monster.getAttribute(Attributes.ATTACK_DAMAGE), Config.ELITE_DAMAGE.get() - 1);

        List<EliteModifier> available = new ArrayList<>(List.of(EliteModifier.values()));
        List<EliteModifier> chosen = new ArrayList<>();
        MutableComponent name = Component.empty();
        for (int i = 0; i < modifierCount && !available.isEmpty(); i++) {
            EliteModifier modifier = available.remove(monster.getRandom().nextInt(available.size()));
            modifier.apply(monster);
            chosen.add(modifier);
            name.append(Component.literal(modifier.displayName + " ").withStyle(modifier.color));
        }
        name.append(Component.literal("Elite ").append(monster.getType().getDescription()).withStyle(ChatFormatting.GOLD));

        monster.setHealth(monster.getMaxHealth());
        monster.setCustomName(name);
        monster.setCustomNameVisible(true);
        monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        return chosen;
    }

    // When an elite hits something
    private static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof Monster elite)
                || !isElite(elite) || event.getNewDamage() <= 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (EliteModifier.BURNING.isOn(elite)) {
            target.igniteForSeconds(4);
        }
        if (EliteModifier.FROST.isOn(elite)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * 20, 1));
        }
        if (EliteModifier.VAMPIRIC.isOn(elite)) {
            elite.heal(event.getNewDamage());
        }
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Monster monster) || !(monster.level() instanceof ServerLevel level)
                || monster.tickCount % 5 != 0 || !isElite(monster)) {
            return;
        }
        if (EliteModifier.BURNING.isOn(monster)) {
            aura(level, monster, ParticleTypes.FLAME);
        }
        if (EliteModifier.FROST.isOn(monster)) {
            aura(level, monster, ParticleTypes.SNOWFLAKE);
        }
        // Teleporting elites try once a second, with a 1 in 3 chance, so it doesn't happen all the time
        if (EliteModifier.TELEPORTING.isOn(monster) && monster.tickCount % 20 == 0 && monster.getRandom().nextInt(3) == 0) {
            LivingEntity target = monster.getTarget();
            if (target != null && target.isAlive() && monster.distanceTo(target) > TELEPORT_DISTANCE) {
                teleportTo(level, monster, target);
            }
        }
    }

    private static void teleportTo(ServerLevel level, Monster monster, LivingEntity target) {
        double fromX = monster.getX();
        double fromY = monster.getY();
        double fromZ = monster.getZ();
        // Try a few spots around the target until one is free
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = monster.getRandom().nextDouble() * Math.PI * 2;
            double distance = 2 + monster.getRandom().nextDouble() * 2;
            double x = target.getX() + Math.cos(angle) * distance;
            double z = target.getZ() + Math.sin(angle) * distance;
            if (monster.randomTeleport(x, target.getY(), z, false)) {
                level.sendParticles(ParticleTypes.PORTAL, fromX, fromY + 1, fromZ, 30, 0.4, 0.8, 0.4, 0.2);
                level.sendParticles(ParticleTypes.PORTAL, monster.getX(), monster.getY() + 1, monster.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
                level.playSound(null, monster.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
                return;
            }
        }
    }

    private static void aura(ServerLevel level, Monster monster, ParticleOptions particle) {
        level.sendParticles(particle, monster.getX(), monster.getY() + monster.getBbHeight() / 2, monster.getZ(),
                2, monster.getBbWidth() / 2, monster.getBbHeight() / 3, monster.getBbWidth() / 2, 0.01);
    }

    private static void boost(AttributeInstance attribute, double amount) {
        if (attribute != null) {
            attribute.addPermanentModifier(new AttributeModifier(ELITE_BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
