package com.morkeriget.bounty;

import net.minecraft.util.RandomSource;

// What a bounty asks for. goal() rolls how many, text() is shown on the board, e.g. "Dræb 6 elites"
public enum BountyType {
    MONSTERS("Dræb %d monstre", "Dræb 1 monster", 40, 80),
    ELITES("Dræb %d elites", "Dræb 1 elite", 4, 8),
    // Only one, since bosses are rare outside rifts (the rift guardian counts too)
    BOSSES("Dræb %d bosser", "Dræb 1 boss", 1, 1),
    RIFTS("Luk %d rifts", "Luk 1 rift", 1, 2);

    private final String text;
    private final String textForOne;
    private final int minGoal;
    private final int maxGoal;

    BountyType(String text, String textForOne, int minGoal, int maxGoal) {
        this.text = text;
        this.textForOne = textForOne;
        this.minGoal = minGoal;
        this.maxGoal = maxGoal;
    }

    int goal(RandomSource random) {
        return minGoal + random.nextInt(maxGoal - minGoal + 1);
    }

    public String text(int goal) {
        return goal == 1 ? textForOne : String.format(text, goal);
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
