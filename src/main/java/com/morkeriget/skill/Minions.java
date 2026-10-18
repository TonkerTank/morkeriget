package com.morkeriget.skill;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// Monsters that fight for a player, like the necromancer's skeletons. They are monsters, so everything that looks
// for enemies has to skip them, and what they kill counts as killed by their owner so it drops loot and gives XP
public final class Minions {
    private static final String OWNER_TAG = "morkeriget_minion_owner";

    private Minions() {
    }

    static void makeAlly(Monster monster, ServerPlayer owner) {
        monster.getPersistentData().putUUID(OWNER_TAG, owner.getUUID());
    }

    public static boolean isAlly(Entity entity) {
        return entity.getPersistentData().hasUUID(OWNER_TAG);
    }

    static boolean isOwnedBy(Entity entity, ServerPlayer player) {
        return isAlly(entity) && entity.getPersistentData().getUUID(OWNER_TAG).equals(player.getUUID());
    }

    // The player a minion fights for, if they are online, or null
    static ServerPlayer owner(ServerLevel level, Entity minion) {
        if (!isAlly(minion)) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(minion.getPersistentData().getUUID(OWNER_TAG));
    }

    // A monster that isn't on the player's side
    public static boolean isEnemy(Entity entity) {
        return entity instanceof Enemy && entity.isAlive() && !isAlly(entity);
    }

    // Goals aren't saved with the monster, so a minion's targeting is set up every time it joins the world:
    // it only goes after enemies, never players or other minions
    static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && isAlly(mob)) {
            mob.targetSelector.removeAllGoals(goal -> true);
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, Minions::isEnemy));
        }
    }

    // Called about once a second for each minion: with nothing to fight it walks after its owner,
    // and if it gets left far behind it is moved to them
    static void tick(ServerLevel level, Mob minion) {
        UUID owner = minion.getPersistentData().getUUID(OWNER_TAG);
        if (!(level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) || player.level() != level) {
            return;
        }
        LivingEntity target = minion.getTarget();
        if (target != null && (!target.isAlive() || !isEnemy(target))) {
            minion.setTarget(null);
            target = null;
        }
        double distance = minion.distanceTo(player);
        if (distance > 32) {
            minion.teleportTo(player.getX(), player.getY(), player.getZ());
        } else if (target == null && distance > 6) {
            minion.getNavigation().moveTo(player, 1.2);
        }
    }

    // When a minion hits something, its owner gets the credit, just like with tame wolves.
    // Minions and their owner can't hurt each other, e.g. with a Whirlwind in the middle of a fight
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player && isOwnedBy(event.getEntity(), player)) {
            event.setCanceled(true);
            return;
        }
        if (attacker == null || !isAlly(attacker) || !(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        UUID owner = attacker.getPersistentData().getUUID(OWNER_TAG);
        LivingEntity target = event.getEntity();
        if (level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) {
            if (target == player) {
                // Never hurt the one who raised them
                event.setCanceled(true);
            } else {
                target.setLastHurtByPlayer(player);
            }
        }
    }
}
