package com.morkeriget.rift;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Affixes on rift stones, like the affixes on nightmare dungeon sigils in Diablo 4. Each one makes the rift harder,
// and each one also gives better rewards, see Rifts. Dungeon keys have them too, see Dungeon and Dungeons
public enum RiftAffix {
    EXPLOSIVE("Eksploderende", "Monstre eksploderer kort efter, de dør"),
    ARMORED("Pansrede", "Monstre har 40% mere liv"),
    FURIOUS("Rasende", "Monstre gør 25% mere skade"),
    SWIFT("Hurtige", "Monstre bevæger sig 25% hurtigere"),
    CURSED_GROUND("Forbandet jord", "Du heler 50% mindre"),
    METEORS("Meteorer", "Meteorer falder ned, hvor du står. Flyt dig!"),
    VAMPIRIC("Vampyriske", "Monstre heler, når de rammer dig"),
    ELITE_ARMY("Elitehær", "Dobbelt så mange elites");

    private static final String AFFIXES_TAG = "morkeriget_rift_affixes";
    // Each affix gives one more item and 25% more essence, XP and chance of a mythic
    public static final double REWARD_PER_AFFIX = 0.25;

    public final String displayName;
    public final String description;

    RiftAffix(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    // How many affixes a stone of this tier gets: none at I, one at II-IV, two at V-VII and three at VIII-X
    public static int count(int tier) {
        return tier <= 1 ? 0 : Math.min(3, 1 + (tier - 2) / 3);
    }

    public static List<RiftAffix> roll(RandomSource random, int tier) {
        List<RiftAffix> possible = new ArrayList<>(Arrays.asList(values()));
        List<RiftAffix> affixes = new ArrayList<>();
        for (int i = 0; i < count(tier) && !possible.isEmpty(); i++) {
            affixes.add(possible.remove(random.nextInt(possible.size())));
        }
        return affixes;
    }

    public static List<RiftAffix> of(ItemStack stone) {
        String names = stone.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(AFFIXES_TAG);
        List<RiftAffix> affixes = new ArrayList<>();
        for (String name : names.split(",")) {
            for (RiftAffix affix : values()) {
                if (affix.name().equals(name)) {
                    affixes.add(affix);
                }
            }
        }
        return affixes;
    }

    public static void markOn(ItemStack stone, List<RiftAffix> affixes) {
        if (!affixes.isEmpty()) {
            String names = String.join(",", affixes.stream().map(RiftAffix::name).toList());
            CustomData.update(DataComponents.CUSTOM_DATA, stone, tag -> tag.putString(AFFIXES_TAG, names));
        }
    }
}
