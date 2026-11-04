package com.morkeriget.progress;

import java.util.List;
import java.util.Locale;

import com.morkeriget.Text;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.loot.Staffs;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// A plain weapon for the class the first time a player picks it, so a new player has more than one skill to fight
// with. Each class gives it once per player, also if they switch back and forth, and it is remembered after death
// (see Skills.onClone, which copies the tags)
public final class StarterWeapons {
    public static final String TAG_PREFIX = "morkeriget_starter_";

    private StarterWeapons() {
    }

    public static void give(ServerPlayer player, PlayerClass playerClass) {
        String tag = TAG_PREFIX + playerClass.name().toLowerCase(Locale.ROOT);
        if (player.getPersistentData().getBoolean(tag)) {
            return;
        }
        player.getPersistentData().putBoolean(tag, true);
        for (ItemStack stack : weapons(player, playerClass)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.sendSystemMessage(Text.tr("progress.starter_weapon").withStyle(ChatFormatting.GREEN));
    }

    // Common, at the player's level. The shield and arrows are plain
    private static List<ItemStack> weapons(ServerPlayer player, PlayerClass playerClass) {
        int level = Math.max(1, Progression.level(player));
        return switch (playerClass) {
            case BARBARIAN -> List.of(Loot.create(player.getRandom(), Rarity.COMMON, level, Items.IRON_SWORD));
            case PALADIN -> List.of(Loot.create(player.getRandom(), Rarity.COMMON, level, Items.IRON_SWORD), new ItemStack(Items.SHIELD));
            case SORCERER, NECROMANCER -> List.of(Loot.create(player.getRandom(), Rarity.COMMON, level, Staffs.OAK.get()));
            case RANGER -> List.of(Loot.create(player.getRandom(), Rarity.COMMON, level, Items.BOW), new ItemStack(Items.ARROW, 64));
        };
    }
}
