package com.morkeriget.elite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.LootFilter;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// Elites out in the world come in packs, like in Diablo: the elite is the leader, with a gold name, and a few monsters
// of the same kind come with it. They have blue names and the leader's modifiers, but are weaker than it. When the
// leader dies the rest go wild, and killing the whole pack gives a bonus. Shielding and Healing leaders protect their
// pack, so it pays to go for the leader first. Elites without a pack, like in rifts, only protect themselves. Server only
public final class ElitePacks {
    // Every monster in a pack has the leader's id under this, the leader too
    private static final String PACK_TAG = "morkeriget_pack";
    private static final String MEMBER_TAG = "morkeriget_pack_member";
    // The leader brings its pack the first time it ticks. Adding monsters while one joins the world isn't safe
    private static final String PENDING_TAG = "morkeriget_pack_pending";
    // Put on the last one of a pack when it dies, so it drops the bonus
    private static final String LAST_TAG = "morkeriget_pack_last";
    private static final ResourceLocation MEMBER_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "pack_member_boost");
    private static final ResourceLocation ENRAGED = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "pack_enraged");
    private static final double MEMBER_HEALTH = 1.8;
    private static final double MEMBER_DAMAGE = 1.15;
    // The part of the pack that the leader's Shielding and Healing reach, and the rest of the pack looked for when
    // one dies
    private static final double PACK_RANGE = 16.0;
    // 20 ticks is one second
    private static final int SHIELD_INTERVAL = 8 * 20;
    private static final int SHIELD_TIME = 3 * 20;
    private static final int HEAL_INTERVAL = 6 * 20;
    private static final float HEAL_SHARE = 0.1f;
    private static final double MEMBER_LOOT_CHANCE = 0.4;
    // How many of each pack are still alive, by the pack's id, so the bonus comes once and only when the whole pack is
    // dead, however far apart they are. It's only kept while the server runs, so after a restart the last one is the
    // one with no others close by
    private static final Map<String, Integer> ALIVE = new HashMap<>();
    private static final double UNTRACKED_RANGE = 48.0;

    private ElitePacks() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ElitePacks::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ElitePacks::onDeath);
        NeoForge.EVENT_BUS.addListener(ElitePacks::onDrops);
        NeoForge.EVENT_BUS.addListener(ElitePacks::onLeaveLevel);
        // In singleplayer the game can go on to another world, which has packs of its own
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ALIVE.clear());
        NeoForge.EVENT_BUS.addListener(ElitePacks::registerCommands);
    }

    // "/morkeriget pack" puts an elite zombie with two modifiers and its pack a few blocks in front of you, for testing.
    // Needs cheats
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("pack")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    Monster leader = EntityType.ZOMBIE.create(level);
                    if (leader == null) {
                        return 0;
                    }
                    Vec3 flat = player.getLookAngle().multiply(1, 0, 1);
                    Vec3 spot = player.position().add(flat.lengthSqr() < 0.01 ? new Vec3(5, 0, 0) : flat.normalize().scale(5));
                    leader.moveTo(spot.x, player.getY(), spot.z, player.getYRot() + 180, 0);
                    leader.finalizeSpawn(level, level.getCurrentDifficultyAt(leader.blockPosition()), MobSpawnType.COMMAND, null);
                    Elites.markChecked(leader);
                    if (Config.MONSTER_LEVELS.get()) {
                        MonsterLevels.apply(leader, Progression.monsterLevel(player));
                    }
                    Elites.makeElite(leader, 2);
                    makeLeader(leader);
                    level.addFreshEntity(leader);
                    return 1;
                })));
    }

    // Makes the elite the leader of a pack, which comes the first time it ticks
    static void makeLeader(Monster leader) {
        leader.getPersistentData().putString(PACK_TAG, leader.getStringUUID());
        leader.getPersistentData().putBoolean(PENDING_TAG, true);
    }

    public static boolean isMember(Entity entity) {
        return entity.getPersistentData().getBoolean(MEMBER_TAG);
    }

    // How often a pack member drops loot of its own, at least magic. See Loot
    public static double memberLootChance() {
        return MEMBER_LOOT_CHANCE;
    }

    private static String pack(Entity entity) {
        return entity.getPersistentData().getString(PACK_TAG);
    }

    // The others in the same pack close by that are still alive
    private static List<Monster> rest(ServerLevel level, Monster monster, double range) {
        String pack = pack(monster);
        if (pack.isEmpty()) {
            return List.of();
        }
        return level.getEntitiesOfClass(Monster.class, monster.getBoundingBox().inflate(range),
                other -> other != monster && other.isAlive() && pack.equals(pack(other)));
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Monster monster) || !(monster.level() instanceof ServerLevel level)
                || pack(monster).isEmpty() && !Elites.isElite(monster)) {
            return;
        }
        if (monster.getPersistentData().getBoolean(PENDING_TAG)) {
            monster.getPersistentData().remove(PENDING_TAG);
            spawnPack(level, monster);
        }
        // Only the leader protects the pack, so it stops once the leader is dead
        if (isMember(monster) || !monster.isAlive()) {
            return;
        }
        if (EliteModifier.SHIELDING.isOn(monster) && monster.tickCount % SHIELD_INTERVAL == 0) {
            shield(level, monster);
        }
        if (EliteModifier.HEALING.isOn(monster) && monster.tickCount % HEAL_INTERVAL == 0) {
            heal(level, monster);
        }
    }

    private static void spawnPack(ServerLevel level, Monster leader) {
        RandomSource random = leader.getRandom();
        int max = Config.PACK_MAX_SIZE.get();
        int min = Math.min(Config.PACK_MIN_SIZE.get(), max);
        int size = min + random.nextInt(max - min + 1);
        // Shielding and Healing stay with the leader, since only it protects the pack
        List<EliteModifier> modifiers = new ArrayList<>();
        for (EliteModifier modifier : EliteModifier.values()) {
            if (modifier.isOn(leader) && modifier != EliteModifier.SHIELDING && modifier != EliteModifier.HEALING) {
                modifiers.add(modifier);
            }
        }
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            if (!(leader.getType().create(level) instanceof Monster member)) {
                break;
            }
            double x = leader.getX() + (random.nextDouble() - 0.5) * 5;
            double z = leader.getZ() + (random.nextDouble() - 0.5) * 5;
            member.moveTo(x, leader.getY(), z, random.nextFloat() * 360, 0);
            if (!level.noCollision(member)) {
                continue;
            }
            // Its normal gear, like a bow for skeletons, and never an elite or a pack of its own
            member.finalizeSpawn(level, level.getCurrentDifficultyAt(member.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            Elites.markChecked(member);
            int leaderLevel = MonsterLevels.get(leader);
            if (leaderLevel > 0) {
                MonsterLevels.apply(member, leaderLevel);
            }
            makeMember(member, leader, modifiers);
            member.setTarget(leader.getTarget());
            level.addFreshEntity(member);
            level.sendParticles(ParticleTypes.POOF, x, leader.getY() + 0.5, z, 8, 0.3, 0.3, 0.3, 0.02);
            spawned++;
        }
        if (spawned == 0) {
            // No room for any, e.g. in a narrow tunnel, so it's just an elite and gives no pack bonus
            leader.getPersistentData().remove(PACK_TAG);
        } else {
            ALIVE.put(pack(leader), spawned + 1);
        }
    }

    // One less of the pack alive. The last one is marked so it drops the bonus
    private static void countDown(Monster monster, boolean killed) {
        String pack = pack(monster);
        Integer alive = ALIVE.get(pack);
        if (alive == null) {
            return;
        }
        if (alive > 1) {
            ALIVE.put(pack, alive - 1);
        } else {
            ALIVE.remove(pack);
            if (killed) {
                monster.getPersistentData().putBoolean(LAST_TAG, true);
            }
        }
    }

    // A pack member that despawns isn't there to be killed anymore
    private static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Monster monster && !pack(monster).isEmpty()
                && monster.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
            countDown(monster, false);
        }
    }

    private static void makeMember(Monster member, Monster leader, List<EliteModifier> modifiers) {
        member.getPersistentData().putString(PACK_TAG, pack(leader));
        member.getPersistentData().putBoolean(MEMBER_TAG, true);
        boost(member.getAttribute(Attributes.MAX_HEALTH), MEMBER_BOOST, MEMBER_HEALTH - 1);
        boost(member.getAttribute(Attributes.ATTACK_DAMAGE), MEMBER_BOOST, MEMBER_DAMAGE - 1);
        MutableComponent name = Component.empty();
        for (EliteModifier modifier : modifiers) {
            modifier.apply(member);
            name.append(Component.literal(modifier.displayName + " ").withStyle(modifier.color));
        }
        name.append(member.getType().getDescription().copy().withStyle(ChatFormatting.BLUE));
        member.setHealth(member.getMaxHealth());
        member.setCustomName(name);
        member.setCustomNameVisible(true);
    }

    // Shielding: the leader and its pack take far less damage for a moment, with a blue ring on each
    private static void shield(ServerLevel level, Monster leader) {
        List<Monster> pack = new ArrayList<>(rest(level, leader, PACK_RANGE));
        pack.add(leader);
        for (Monster monster : pack) {
            monster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SHIELD_TIME, 1));
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2 * i / 12;
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, monster.getX() + Math.cos(angle) * 0.8, monster.getY() + monster.getBbHeight() / 2,
                        monster.getZ() + Math.sin(angle) * 0.8, 1, 0, 0.1, 0, 0);
            }
        }
        level.playSound(null, leader.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 1.0f, 0.7f);
    }

    // Healing: the leader and its pack get some health back, with hearts over each
    private static void heal(ServerLevel level, Monster leader) {
        List<Monster> pack = new ArrayList<>(rest(level, leader, PACK_RANGE));
        pack.add(leader);
        for (Monster monster : pack) {
            if (monster.getHealth() < monster.getMaxHealth()) {
                monster.heal(monster.getMaxHealth() * HEAL_SHARE);
                level.sendParticles(ParticleTypes.HEART, monster.getX(), monster.getY() + monster.getBbHeight() + 0.3, monster.getZ(),
                        3, 0.3, 0.2, 0.3, 0);
            }
        }
        level.playSound(null, leader.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.0f, 1.2f);
    }

    // When the leader dies, the rest of the pack goes wild: stronger and faster until they die too
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Monster leader) || !(leader.level() instanceof ServerLevel level) || pack(leader).isEmpty()) {
            return;
        }
        countDown(leader, true);
        if (isMember(leader)) {
            return;
        }
        List<Monster> rest = rest(level, leader, PACK_RANGE);
        if (rest.isEmpty()) {
            return;
        }
        for (Monster member : rest) {
            boost(member.getAttribute(Attributes.ATTACK_DAMAGE), ENRAGED, 0.3);
            boost(member.getAttribute(Attributes.MOVEMENT_SPEED), ENRAGED, 0.25);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, member.getX(), member.getY() + member.getBbHeight() + 0.3, member.getZ(),
                    4, 0.3, 0.2, 0.3, 0);
        }
        level.playSound(null, leader.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 1.3f);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, leader.getBoundingBox().inflate(PACK_RANGE * 2))) {
            player.displayClientMessage(Component.literal("Lederen er faldet - flokken raser!").withStyle(ChatFormatting.RED), true);
        }
    }

    // The last one of a pack to die drops a bonus for the whole pack
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Monster monster) || !(monster.level() instanceof ServerLevel level) || pack(monster).isEmpty()
                || !(monster.getKillCredit() instanceof ServerPlayer player)) {
            return;
        }
        boolean last = monster.getPersistentData().getBoolean(LAST_TAG)
                || !ALIVE.containsKey(pack(monster)) && rest(level, monster, UNTRACKED_RANGE).isEmpty();
        if (!last) {
            return;
        }
        RandomSource random = monster.getRandom();
        int itemLevel = Math.max(1, MonsterLevels.get(monster));
        Rarity rarity = Rarity.roll(random, Rarity.RARE);
        int essence = 5 + random.nextInt(6);
        List<ItemStack> bonus = new ArrayList<>();
        // The killer's loot filter turns the item into essence, like normal drops
        if (LootFilter.hides(player, rarity)) {
            essence += Smithy.salvageValue(rarity);
        } else {
            bonus.add(Loot.create(random, rarity, itemLevel, Progression.playerClass(player)));
        }
        bonus.add(new ItemStack(Smithy.ESSENCE.get(), essence));
        for (ItemStack stack : bonus) {
            event.getDrops().add(new ItemEntity(level, monster.getX(), monster.getY(), monster.getZ(), stack));
        }
        player.displayClientMessage(Component.literal("Flokken er besejret! Bonus-loot").withStyle(ChatFormatting.GOLD), true);
        level.playSound(null, monster.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, monster.getX(), monster.getY() + 1, monster.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
    }

    private static void boost(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute != null && !attribute.hasModifier(id)) {
            attribute.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
