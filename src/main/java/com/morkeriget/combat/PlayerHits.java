package com.morkeriget.combat;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Config;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.loot.ItemKind;
import com.morkeriget.loot.ItemSet;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.SetBonuses;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Everything that happens when a player hits something or gets hit: special affixes and the damage number.
// Affixes count from the weapon used for the hit plus everything the player wears (armour, shield and jewelry)
public final class PlayerHits {
    // The target that just got a crushing blow. Pre and Post run right after each other on the server thread,
    // so this only has to remember it between the two
    private static LivingEntity crushedTarget;
    // The most the special affixes can add up to over all items, so stacking many of them doesn't make you unkillable
    private static final int MAX_LIFESTEAL = 20;
    private static final int MAX_CRUSHING = 40;
    private static final int MAX_THORNS = 60;

    private PlayerHits() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DamageNumberPayload::register);
        NeoForge.EVENT_BUS.addListener(PlayerHits::onDamagePre);
        NeoForge.EVENT_BUS.addListener(PlayerHits::onDamagePost);
    }

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        ServerPlayer player = attacker(event.getEntity(), event.getSource());
        if (player == null) {
            return;
        }
        ItemStack weapon = weapon(player, event.getSource());
        List<ItemStack> items = affixItems(player, weapon);
        if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
            float multiplier = 1 + total(items, Loot.ARROW_DAMAGE_TAG) / 100f;
            // Bows have no flat damage bonus like swords, so arrows get stronger with the bow's item level instead
            if (Loot.getRarity(weapon) != null) {
                multiplier *= (float) (1 + Config.AFFIX_PER_LEVEL.get() * (Loot.itemLevel(weapon) - 1));
            }
            // Rangers do 25% more arrow damage
            if (Progression.playerClass(player) == PlayerClass.RANGER) {
                multiplier *= 1.25f;
            }
            event.setNewDamage(event.getNewDamage() * multiplier);
        }
        int crushingChance = Math.min(MAX_CRUSHING, total(items, Loot.CRUSHING_TAG));
        if (crushingChance > 0 && player.getRandom().nextInt(100) < crushingChance) {
            event.setNewDamage(event.getNewDamage() * 2);
            crushedTarget = event.getEntity();
        }
    }

    private static void onDamagePost(LivingDamageEvent.Post event) {
        boolean crushing = crushedTarget == event.getEntity();
        crushedTarget = null;
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            thorns(victim, event);
        }
        ServerPlayer player = attacker(event.getEntity(), event.getSource());
        if (player == null || event.getNewDamage() <= 0) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new DamageNumberPayload(event.getEntity().getId(), event.getNewDamage(), crushing));

        List<ItemStack> items = affixItems(player, weapon(player, event.getSource()));
        int lifesteal = Math.min(MAX_LIFESTEAL, total(items, Loot.LIFESTEAL_TAG) + (SetBonuses.has(player, ItemSet.BLOODHUNTER, 2) ? 6 : 0));
        if (lifesteal > 0) {
            player.heal(event.getNewDamage() * lifesteal / 100f);
        }
        int fireSeconds = total(items, Loot.FIRE_TAG);
        if (fireSeconds > 0) {
            event.getEntity().igniteForSeconds(fireSeconds);
        }
    }

    // Sends part of the damage back to whoever hit the player
    private static void thorns(ServerPlayer victim, LivingDamageEvent.Post event) {
        // Thorns damage must not cause more thorns, or two players with thorns would hit each other forever
        if (event.getSource().is(DamageTypes.THORNS) || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == victim) {
            return;
        }
        int percent = Math.min(MAX_THORNS,
                total(affixItems(victim, ItemStack.EMPTY), Loot.THORNS_TAG) + (SetBonuses.has(victim, ItemSet.GUARDIAN, 4) ? 25 : 0));
        float damage = (event.getNewDamage() + event.getBlockedDamage()) * percent / 100f;
        if (damage > 0) {
            attacker.hurt(victim.damageSources().thorns(victim), damage);
        }
    }

    // The player behind the hit, if it was with a weapon or an arrow (not e.g. fire or explosions), or null
    private static ServerPlayer attacker(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide() || !(source.getEntity() instanceof ServerPlayer player) || player == target
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.THORNS)) {
            return null;
        }
        boolean melee = source.getDirectEntity() == player;
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        return melee || arrow ? player : null;
    }

    // The weapon used for the hit: the sword or axe for melee, the bow or crossbow that shot the arrow
    private static ItemStack weapon(ServerPlayer player, DamageSource source) {
        if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            ItemStack bow = arrow.getWeaponItem();
            return bow != null && ItemKind.of(bow) == ItemKind.RANGED ? bow : ItemStack.EMPTY;
        }
        ItemStack held = player.getMainHandItem();
        return ItemKind.of(held) == ItemKind.MELEE ? held : ItemStack.EMPTY;
    }

    // The weapon plus everything the player wears in the right place: armour on the body, a shield in the off hand, jewelry
    private static List<ItemStack> affixItems(ServerPlayer player, ItemStack weapon) {
        List<ItemStack> items = new ArrayList<>();
        items.add(weapon);
        if (ItemKind.of(player.getOffhandItem()) == ItemKind.SHIELD) {
            items.add(player.getOffhandItem());
        }
        for (ItemStack armor : player.getArmorSlots()) {
            if (ItemKind.of(armor) == ItemKind.ARMOR) {
                items.add(armor);
            }
        }
        items.addAll(Jewelry.worn(player));
        return items;
    }

    // Everything the player wears plus the weapon in their hand, e.g. for the mana bonus from sapphires
    public static List<ItemStack> equippedItems(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        ItemKind kind = ItemKind.of(held);
        return affixItems(player, kind == ItemKind.MELEE || kind == ItemKind.RANGED ? held : ItemStack.EMPTY);
    }

    public static int total(List<ItemStack> items, String tag) {
        int total = 0;
        for (ItemStack item : items) {
            total += Loot.affix(item, tag);
        }
        return total;
    }
}
