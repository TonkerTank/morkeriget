package com.morkeriget.bounty;

import java.util.Locale;

import com.morkeriget.Text;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

// What a bounty asks for. goal() rolls how many, text() is shown on the board, e.g. "Dræb 6 elites"
public enum BountyType {
    // The texts are in the language files, under bounty.<name> and bounty.<name>.one
    MONSTERS(40, 80),
    ELITES(4, 8),
    // Only one, since bosses are rare outside rifts (the rift guardian counts too)
    BOSSES(1, 1),
    RIFTS(1, 2);

    private final int minGoal;
    private final int maxGoal;

    BountyType(int minGoal, int maxGoal) {
        this.minGoal = minGoal;
        this.maxGoal = maxGoal;
    }

    int goal(RandomSource random) {
        return minGoal + random.nextInt(maxGoal - minGoal + 1);
    }

    public MutableComponent text(int goal) {
        String key = "bounty." + name().toLowerCase(Locale.ROOT);
        return goal == 1 ? Text.tr(key + ".one") : Text.tr(key, goal);
    }

    static BountyType byName(String name) {
        for (BountyType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return MONSTERS;
    }
}
