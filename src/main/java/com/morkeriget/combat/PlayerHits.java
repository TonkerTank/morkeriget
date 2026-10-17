package com.morkeriget.combat;

import com.morkeriget.loot.Loot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Everything that happens when a player hits something: the special affixes on the weapon, and the damage number
public final class PlayerHits {
    // The target that just got a crushing blow. Pre and Post run right after each other on the server thread,
    // so this only has to remember it between the two
    private static LivingEntity crushedTarget;

    private PlayerHits() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DamageNumberPayload::register);
        NeoForge.EVENT_BUS.addListener(PlayerHits::onDamagePre);
        NeoForge.EVENT_BUS.addListener(PlayerHits::onDamagePost);
    }

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        ServerPlayer player = meleeAttacker(event.getEntity(), event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (player == null) {
            return;
        }
        int crushingChance = weaponAffixes(player).getInt(Loot.CRUSHING_TAG);
        if (crushingChance > 0 && player.getRandom().nextInt(100) < crushingChance) {
            event.setNewDamage(event.getNewDamage() * 2);
            crushedTarget = event.getEntity();
        }
    }

    private static void onDamagePost(LivingDamageEvent.Post event) {
        boolean crushing = crushedTarget == event.getEntity();
        crushedTarget = null;
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker == event.getEntity() || event.getNewDamage() <= 0) {
            return;
        }
        PacketDistributor.sendToPlayer(attacker, new DamageNumberPayload(event.getEntity().getId(), event.getNewDamage(), crushing));

        ServerPlayer player = meleeAttacker(event.getEntity(), event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (player == null) {
            return;
        }
        CompoundTag affixes = weaponAffixes(player);
        int lifesteal = affixes.getInt(Loot.LIFESTEAL_TAG);
        if (lifesteal > 0) {
            player.heal(event.getNewDamage() * lifesteal / 100f);
        }
        int fireSeconds = affixes.getInt(Loot.FIRE_TAG);
        if (fireSeconds > 0) {
            event.getEntity().igniteForSeconds(fireSeconds);
        }
    }

    // The player who hit the target with their weapon (not with an arrow or similar), or null
    private static ServerPlayer meleeAttacker(LivingEntity target, Entity attacker, Entity direct) {
        if (target.level().isClientSide() || !(attacker instanceof ServerPlayer player) || direct != player || player == target) {
            return null;
        }
        return player;
    }

    private static CompoundTag weaponAffixes(ServerPlayer player) {
        return player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
