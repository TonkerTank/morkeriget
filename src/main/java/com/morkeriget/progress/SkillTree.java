package com.morkeriget.progress;

import java.util.List;
import java.util.function.ToIntFunction;

// The rules of the class skill trees, used both by the server and the skill screen. The ranks come in as a function,
// since the server has them in PlayerProgress and the client in ProgressPayload.
// In the old trees the first row is open from the start, and the next rows open when you have spent enough points in
// the tree. In the reworked ones (Upgrade.column set) each box needs a point in the box above it, and some also need
// points spent in the tree. Variants need VARIANT_RANK in their skill
public final class SkillTree {
    // Points spent in the tree it takes to open each row of the old trees
    private static final int[] ROW_POINTS = {0, 4, 10, 20};
    // How many skills can be on the keys 1-4
    public static final int SLOTS = 4;

    private SkillTree() {
    }

    // The skills and passives in one row of the class's tree, in the order they are shown. Variants and keystones
    // aren't boxes of their own: the variants are picked on their skill, and the keystones share one box, see keystoneColumn
    public static List<Upgrade> row(PlayerClass playerClass, int row) {
        return List.of(Upgrade.values()).stream()
                .filter(upgrade -> upgrade.playerClass == playerClass && upgrade.row == row
                        && (upgrade.kind == Upgrade.Kind.SKILL || upgrade.kind == Upgrade.Kind.PASSIVE))
                .toList();
    }

    // The column the upgrade's box is in
    public static int column(Upgrade upgrade) {
        return upgrade.column >= 0 ? upgrade.column : row(upgrade.playerClass, upgrade.row).indexOf(upgrade);
    }

    // How many rows the class's tree has: up to the last one with anything in it
    public static int rows(PlayerClass playerClass) {
        int rows = 0;
        for (Upgrade upgrade : Upgrade.values()) {
            if (upgrade.playerClass == playerClass) {
                rows = Math.max(rows, upgrade.row + 1);
            }
        }
        return rows;
    }

    // Points spent in the tree it takes to open the upgrade
    public static int pointsNeeded(Upgrade upgrade) {
        return upgrade.points >= 0 ? upgrade.points : ROW_POINTS[upgrade.row];
    }

    // The rank the upgrade's parent needs before it opens
    public static int parentRank(Upgrade upgrade) {
        return upgrade.kind == Upgrade.Kind.VARIANT ? Upgrade.VARIANT_RANK : 1;
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

    // Whether points can go into the upgrade, from the points spent and its parent. Shared upgrades are always open
    public static boolean isOpen(PlayerClass playerClass, Upgrade upgrade, ToIntFunction<Upgrade> rank) {
        if (upgrade.playerClass == null) {
            return true;
        }
        if (upgrade.playerClass != playerClass || spent(playerClass, rank) < pointsNeeded(upgrade)) {
            return false;
        }
        return upgrade.parent == null || rank.applyAsInt(upgrade.parent) >= parentRank(upgrade);
    }

    // The variant or keystone picked instead of this one, or null
    public static Upgrade picked(Upgrade upgrade, ToIntFunction<Upgrade> rank) {
        for (Upgrade other : Upgrade.values()) {
            if (other != upgrade && upgrade.sameChoice(other) && rank.applyAsInt(other) > 0) {
                return other;
            }
        }
        return null;
    }

    // Whether the upgrade can be raised right now. Picking another variant or keystone instead of the one you have is
    // free, everything else costs a point
    public static boolean canRaise(PlayerClass playerClass, Upgrade upgrade, int points, ToIntFunction<Upgrade> rank) {
        if (rank.applyAsInt(upgrade) >= upgrade.maxRank() || !isOpen(playerClass, upgrade, rank)) {
            return false;
        }
        return points > 0 || picked(upgrade, rank) != null;
    }
}
