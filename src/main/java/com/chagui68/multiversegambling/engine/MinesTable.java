package com.chagui68.multiversegambling.engine;

/**
 * Math behind the "reveal tiles" games (Mines, Bomb Board, Towers). The multiplier is
 * always the exact inverse of the chance of surviving, trimmed by the house edge, so it
 * can never be exploited by choosing the number of mines.
 */
public final class MinesTable {

    private MinesTable() {
    }

    /**
     * Chance of revealing {@code revealed} safe tiles in a row without touching any of
     * the {@code mines} mines spread over {@code tiles}.
     */
    public static double survivalChance(int tiles, int mines, int revealed) {
        if (revealed < 0 || tiles <= 0 || mines < 0 || mines >= tiles) {
            return 0;
        }
        if (revealed > tiles - mines) {
            return 0;
        }
        double chance = 1.0;
        for (int i = 0; i < revealed; i++) {
            chance *= (double) (tiles - mines - i) / (tiles - i);
        }
        return chance;
    }

    /**
     * Cash out multiplier after revealing {@code revealed} safe tiles.
     */
    public static double multiplier(int tiles, int mines, int revealed, double houseEdge) {
        if (revealed <= 0) {
            return 1.0;
        }
        double chance = survivalChance(tiles, mines, revealed);
        if (chance <= 0) {
            return 0;
        }
        return (1.0 - houseEdge) / chance;
    }

    /**
     * Accumulated multiplier for the Towers game: every floor has {@code tiles} tiles of
     * which {@code bombs} are deadly.
     */
    public static double towerMultiplier(int level, int tiles, int bombs, double houseEdge) {
        if (level <= 0) {
            return 1.0;
        }
        double safePerLevel = (double) (tiles - bombs) / tiles;
        double chance = Math.pow(safePerLevel, level);
        if (chance <= 0) {
            return 0;
        }
        return (1.0 - houseEdge) / chance;
    }

    /**
     * Effective house edge when the rounding down is taken into account.
     */
    public static double truncate(double multiplier) {
        return Math.floor(multiplier * 100.0) / 100.0;
    }
}
