package com.morkeriget.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Elites;
import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// The necromancer's skills: Bone Spear, Raise Skeletons, Bone Armor and Curse, and the class bonus that heals the
// necromancer when enemies die close by. Server only, called from Skills.use
final class NecroSkills {
    // 20 ticks is one second
    private static final double BONE_SPEAR_RANGE = 20.0;
    private static final int SKELETON_LIFETIME = 40 * 20;
    private static final int MAX_SKELETONS = 8;
    private static final int BONE_ARMOR_DURATION = 10 * 20;
    private static final double CURSE_RADIUS = 10.0;
    private static final int CURSE_DURATION = 8 * 20;
    private static final double HEAL_RANGE = 16.0;
    private static final String CURSED_UNTIL_TAG = "morkeriget_cursed_until";
    private static final String CURSE_BONUS_TAG = "morkeriget_curse_bonus";
    private static final String CURSED_BY_TAG = "morkeriget_cursed_by";
    private static final double CURSE_SPREAD_RADIUS = 5.0;
    private static final double CORPSE_EXPLOSION_RADIUS = 3.0;
    private static final ResourceLocation SKELETON_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "skeleton_boost");
    private static final BlockParticleOption BONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());

    private NecroSkills() {
    }

    // A spear of bone that flies straight through every enemy in a line until it hits a wall
    static boolean boneSpear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = level.clip(new ClipContext(eyes, eyes.add(look.scale(BONE_SPEAR_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        // Each Bone Spear upgrade rank adds 20% damage
        double upgrade = 1 + 0.2 * Progression.rank(player, Upgrade.BONE_SPEAR);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.2 * upgrade);
        List<LivingEntity> hit = new ArrayList<>();
        double distance = end.distanceTo(eyes);
        // Bone splinters and a wisp of soul where it leaves your hand
        Vec3 hand = eyes.add(look.scale(0.8)).subtract(0, 0.3, 0);
        level.sendParticles(BONE, hand.x, hand.y, hand.z, 8, 0.15, 0.15, 0.15, 0);
        level.sendParticles(ParticleTypes.SOUL, hand.x, hand.y, hand.z, 3, 0.1, 0.1, 0.1, 0.02);
        int steps = 0;
        for (double travelled = 0.5; travelled <= distance; travelled += 0.5) {
            Vec3 point = eyes.add(look.scale(travelled));
            level.sendParticles(BONE, point.x, point.y, point.z, 2, 0.05, 0.05, 0.05, 0);
            // A pale streak behind the spear, with a soul now and then
            level.sendParticles(SkillVisuals.BONE_DUST, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0);
            if (steps++ % 4 == 0) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(0.8),
                    entity -> Minions.isEnemy(entity) && !hit.contains(entity))) {
                hit.add(target);
                target.hurt(player.damageSources().playerAttack(player), damage);
                level.sendParticles(BONE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 10, 0.25, 0.3, 0.25, 0.1);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 5, 0.2, 0.2, 0.2, 0.3);
            }
        }
        // It shatters on the wall at the end
        level.sendParticles(BONE, end.x, end.y, end.z, 15, 0.2, 0.2, 0.2, 0.15);
        level.sendParticles(ParticleTypes.POOF, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.6f, 0.7f);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
        return true;
    }

    // Wither skeletons that fight for you for a while. They don't burn in the sun. Every cast adds more, so you can build
    // up an army of up to MAX_SKELETONS. When there is no room, the ones that would disappear first make way
    static boolean raiseSkeletons(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Each Raise Skeletons upgrade rank makes them 15% stronger, and every third rank adds a skeleton per cast
        int rank = Progression.rank(player, Upgrade.RAISE_SKELETONS);
        int count = 2 + Upgrade.utility(rank) / 3;
        List<WitherSkeleton> army = new ArrayList<>(level.getEntitiesOfClass(WitherSkeleton.class, player.getBoundingBox().inflate(64),
                skeleton -> Minions.isOwnedBy(skeleton, player)));
        army.sort(Comparator.comparingLong(skeleton -> skeleton.getPersistentData().getLong(ClassSkills.SUMMON_UNTIL_TAG)));
        for (int i = 0; i < army.size() + count - MAX_SKELETONS && i < army.size(); i++) {
            poof(level, army.get(i).position());
            army.get(i).discard();
        }
        // Death Lord adds 10% a rank
        double strength = (1 + 0.025 * (Progression.level(player) - 1)) * (1 + 0.15 * rank)
                * (1 + 0.1 * Progression.rank(player, Upgrade.DEATH_LORD)) - 1;
        RandomSource random = player.getRandom();
        for (int i = 0; i < count; i++) {
            WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level);
            if (skeleton == null) {
                continue;
            }
            skeleton.moveTo(player.getX() + (random.nextDouble() - 0.5) * 3, player.getY(), player.getZ() + (random.nextDouble() - 0.5) * 3,
                    random.nextFloat() * 360, 0);
            skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(skeleton.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            // Better gear every fifth rank, in place of the stone sword it spawns with
            SummonGear.equip(skeleton, rank);
            // Never an elite, and no monster level of its own
            Elites.markChecked(skeleton);
            Minions.makeAlly(skeleton, player);
            boost(skeleton.getAttribute(Attributes.MAX_HEALTH), strength);
            boost(skeleton.getAttribute(Attributes.ATTACK_DAMAGE), strength);
            skeleton.setHealth(skeleton.getMaxHealth());
            skeleton.getPersistentData().putLong(ClassSkills.SUMMON_UNTIL_TAG, level.getGameTime() + SKELETON_LIFETIME);
            level.addFreshEntity(skeleton);
            level.sendParticles(ParticleTypes.SOUL, skeleton.getX(), skeleton.getY() + 0.5, skeleton.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            level.sendParticles(BONE, skeleton.getX(), skeleton.getY() + 0.2, skeleton.getZ(), 15, 0.4, 0.1, 0.4, 0);
            // Blue soul fire rising out of a dark patch on the ground where it climbs up
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, skeleton.getX(), skeleton.getY() + 0.1, skeleton.getZ(), 6, 0.3, 0.05, 0.3, 0.03);
            SkillVisuals.ring(level, skeleton.position(), 0.05, 0.7, 8, SkillVisuals.SHADOW);
        }
        // A ring of soul fire rolling out from you
        SkillVisuals.burst(level, player.position(), 0.2, 16, 0.15, ParticleTypes.SOUL_FIRE_FLAME);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 6, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // A shield of extra hearts and less damage taken for a while
    static boolean boneArmor(ServerPlayer player) {
        // 30% of your max health as a shield, and each Bone Armor upgrade rank adds 5% more. Absorption gives 4 per level
        double shield = player.getMaxHealth() * (0.3 + 0.05 * Upgrade.utility(Progression.rank(player, Upgrade.BONE_ARMOR)));
        int amplifier = Math.max(0, (int) Math.round(shield / 4) - 1);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BONE_ARMOR_DURATION, amplifier));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BONE_ARMOR_DURATION, 0));
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            level.sendParticles(BONE, player.getX() + Math.cos(angle) * 1.2, player.getY() + 1, player.getZ() + Math.sin(angle) * 1.2,
                    3, 0.05, 0.4, 0.05, 0);
        }
        // Bones winding up around you and pulled in tight, like the armour closing
        SkillVisuals.spiral(level, player.position(), 1.0, 2.2, 2, 32, SkillVisuals.BONE_DUST);
        SkillVisuals.implode(level, player.position(), 1.0, 2.0, 12, 0.15, ParticleTypes.WHITE_ASH);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 5, 0.4, 0.5, 0.4, 0.02);
        player.getPersistentData().putLong(SkillVisuals.BONE_ARMOR_UNTIL_TAG, level.getGameTime() + BONE_ARMOR_DURATION);
        level.playSound(null, player.blockPosition(), SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 0.7f);
        return true;
    }

    // Enemies around you become slow and weak, and take more damage from everything for a while
    static boolean curse(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int bonus = curseBonus(player);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CURSE_RADIUS),
                entity -> Minions.isEnemy(entity) && entity.distanceTo(player) <= CURSE_RADIUS)) {
            applyCurse(level, target, player, bonus);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 3, 0.5, 3, 0.02);
        // A sickly wave out to where it reaches and dark magic winding up around you
        SkillVisuals.shockwave(level, player.position(), 0.2, CURSE_RADIUS, 3, SkillVisuals.CURSE_FADE);
        SkillVisuals.spiral(level, player.position(), 0.9, 2.2, 2, 24, ParticleTypes.WITCH);
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.6, 0.1, 0.6, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.3f, 1.3f);
        return true;
    }

    // A cursed enemy smokes a little once a second while the curse lasts. Called from ClassSkills.onEntityTick
    static void curseGlow(ServerLevel level, LivingEntity entity) {
        if (entity.getPersistentData().getLong(CURSED_UNTIL_TAG) > level.getGameTime() && entity.isAlive()) {
            double top = entity.getY() + entity.getBbHeight();
            level.sendParticles(ParticleTypes.WITCH, entity.getX(), top, entity.getZ(), 3, 0.25, 0.1, 0.25, 0.01);
            level.sendParticles(SkillVisuals.CURSE_FADE, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    3, 0.3, 0.4, 0.3, 0);
        }
    }

    // How much more damage cursed enemies take, in percent: 25%, and each Curse upgrade rank adds 5%
    static int curseBonus(ServerPlayer player) {
        return 25 + 5 * Progression.rank(player, Upgrade.CURSE);
    }

    // The one who cursed it is saved too, so Contagious Curse knows whose power to check when it dies
    static void applyCurse(ServerLevel level, LivingEntity target, ServerPlayer player, int bonus) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CURSE_DURATION, 0));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CURSE_DURATION, 0));
        CompoundTag data = target.getPersistentData();
        data.putLong(CURSED_UNTIL_TAG, level.getGameTime() + CURSE_DURATION);
        data.putInt(CURSE_BONUS_TAG, bonus);
        data.putUUID(CURSED_BY_TAG, player.getUUID());
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 6, 0.3, 0.2, 0.3, 0.01);
        level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
    }

    // Cursed enemies take more damage from everything, also from other players, minions and fire
    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        if (data.getLong(CURSED_UNTIL_TAG) > level.getGameTime()) {
            // Grave Chill: the necromancer's own hits do 4% more a rank on top
            int chill = event.getSource().getEntity() instanceof ServerPlayer player ? 4 * Progression.rank(player, Upgrade.GRAVE_CHILL) : 0;
            event.setNewDamage(event.getNewDamage() * (1 + (data.getInt(CURSE_BONUS_TAG) + chill) / 100f));
        }
    }

    // The necromancer's class bonus: every enemy that dies close by heals them a little.
    // Also the legendary powers that happen when something dies: Corpse Explosion and Contagious Curse
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        if (dead instanceof WitherSkeleton && Minions.owner(level, dead) instanceof ServerPlayer owner
                && LegendaryPower.has(owner, LegendaryPower.CORPSE_EXPLOSION)) {
            corpseExplosion(level, dead, owner);
        }
        if (!isFormerEnemy(dead)) {
            return;
        }
        spreadCurse(level, dead);
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(dead) <= HEAL_RANGE && player.isAlive() && Progression.playerClass(player) == PlayerClass.NECROMANCER) {
                // 5%, and Soul Harvest adds 1% a rank
                player.heal(player.getMaxHealth() * (0.05f + 0.01f * Progression.rank(player, Upgrade.SOUL_HARVEST)));
                level.sendParticles(ParticleTypes.SOUL, dead.getX(), dead.getY() + 1, dead.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    // A dying skeleton hurts every enemy around it, as much as a Bone Spear from its owner
    private static void corpseExplosion(ServerLevel level, LivingEntity skeleton, ServerPlayer owner) {
        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.2);
        Vec3 center = skeleton.position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, skeleton.getBoundingBox().inflate(CORPSE_EXPLOSION_RADIUS),
                entity -> Minions.isEnemy(entity) && entity.position().distanceTo(center) <= CORPSE_EXPLOSION_RADIUS + 1)) {
            target.hurt(owner.damageSources().playerAttack(owner), damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 2, 0.4, 0.4, 0.4, 0);
        level.sendParticles(BONE, center.x, center.y + 0.5, center.z, 40, 0.8, 0.6, 0.8, 0.1);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    // With Contagious Curse, a cursed enemy that dies passes the curse on to the enemies around it
    private static void spreadCurse(ServerLevel level, LivingEntity dead) {
        CompoundTag data = dead.getPersistentData();
        if (data.getLong(CURSED_UNTIL_TAG) <= level.getGameTime() || !data.hasUUID(CURSED_BY_TAG)
                || !(level.getServer().getPlayerList().getPlayer(data.getUUID(CURSED_BY_TAG)) instanceof ServerPlayer player)
                || !LegendaryPower.has(player, LegendaryPower.CONTAGIOUS_CURSE)) {
            return;
        }
        Vec3 center = dead.position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(CURSE_SPREAD_RADIUS),
                entity -> Minions.isEnemy(entity) && entity.position().distanceTo(center) <= CURSE_SPREAD_RADIUS)) {
            applyCurse(level, target, player, data.getInt(CURSE_BONUS_TAG));
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 1, center.z, 15, 1.5, 0.5, 1.5, 0.02);
    }

    // Minions.isEnemy checks isAlive, which is already false while the death event runs
    private static boolean isFormerEnemy(LivingEntity entity) {
        return entity instanceof Enemy && !Minions.isAlly(entity);
    }

    private static void poof(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 10, 0.3, 0.3, 0.3, 0.02);
    }

    private static void boost(AttributeInstance attribute, double amount) {
        if (attribute != null && amount > 0) {
            attribute.addPermanentModifier(new AttributeModifier(SKELETON_BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
