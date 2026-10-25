package com.morkeriget.smithy;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.jewelry.JewelryMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// Durability works like in Diablo: things never wear out from being used (see ItemStackMixin), but when a player dies,
// everything they wear and hold loses some. It never breaks, and the smithy repairs it for essence
public final class Durability {
    // A tenth of the full durability for each death
    private static final double DEATH_LOSS = 0.1;
    // What a full repair of an item that is all worn out costs. Less worn costs less
    private static final int FULL_REPAIR_COST = 10;

    private Durability() {
    }

    static void register() {
        // Before Dungeons saves what a dying player carries, so the loss is in what they get back
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Durability::onDeath);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        boolean lost = false;
        for (ItemStack stack : equipped(player)) {
            if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                stack.setDamageValue(Math.min(max - 1, stack.getDamageValue() + (int) Math.ceil(max * DEATH_LOSS)));
                lost = true;
            }
        }
        if (lost) {
            player.sendSystemMessage(Component.literal("Dit udstyr mistede " + Math.round(DEATH_LOSS * 100) + "% holdbarhed - reparér det i smedjen")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // Armor, both hands and the weapon slot, each only once if the weapon is also in the hand
    private static List<ItemStack> equipped(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.BODY) {
                items.add(player.getItemBySlot(slot));
            }
        }
        ItemStack weapon = player.getInventory().getItem(JewelryMenu.WEAPON_SLOT);
        if (items.stream().noneMatch(stack -> stack == weapon)) {
            items.add(weapon);
        }
        return items;
    }

    // 0 if it isn't worn
    public static int repairCost(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getDamageValue() == 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(FULL_REPAIR_COST * stack.getDamageValue() / (double) stack.getMaxDamage()));
    }
}
