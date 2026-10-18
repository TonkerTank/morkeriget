package com.morkeriget.progress;

import java.util.List;
import java.util.function.ToIntFunction;

// The rules of the class skill trees, used both by the server and the skill screen. The first row is open from the
// start, and the next rows open when you have spent enough points in the tree. The ranks come in as a function,
// since the server has them in PlayerProgress and the client in ProgressPayload
public final class SkillTree {
    public static final int ROWS = 3;
    // Points spent in the tree it takes to open each row
    private static final int[] ROW_POINTS = {0, 4, 10};
    // How many skills can be on the keys 1-4
    public static final int SLOTS = 4;

    private SkillTree() {
    }

    // The upgrades in one row of the class's tree, in the order they are shown
    public static List<Upgrade> row(PlayerClass playerClass, int row) {
        return List.of(Upgrade.values()).stream()
                .filter(upgrade -> upgrade.playerClass == playerClass && upgrade.row == row)
                .toList();
    }

    public static int pointsForRow(int row) {
        return ROW_POINTS[row];
    }

    // Points spent in the class's tree. The shared upgrades don't count
    public static int spent(PlayerClass playerClass, ToIntFunction<Upgrade> rank) {
        int spent = 0;
        for (Upgrade upgrade : Upgrade.values()) {
            if (upgrade.playerClass == playerClass) {
                spent += rank.applyAsInt(upgrade);
            }
        }
        return spent;
    }

    public static boolean isRowOpen(PlayerClass playerClass, int row, ToIntFunction<Upgrade> rank) {
        return spent(playerClass, rank) >= ROW_POINTS[row];
    }

    // Whether a point can go into the upgrade right now, apart from having a point to spend
    public static boolean canRaise(PlayerClass playerClass, Upgrade upgrade, ToIntFunction<Upgrade> rank) {
        if (rank.applyAsInt(upgrade) >= upgrade.maxRank()) {
            return false;
        }
        if (upgrade.playerClass == null) {
            return true;
        }
        return upgrade.playerClass == playerClass && isRowOpen(playerClass, upgrade.row, rank);
    }
}
