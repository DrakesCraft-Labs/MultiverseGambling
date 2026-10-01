package com.chagui68.multiversegambling.engine;

import java.util.Locale;

/**
 * How hard each floor of the tower is: how many tiles it has and how many of them hide a
 * bomb. Every difficulty is paid with the exact inverse of the chance of surviving, so no
 * difficulty is a better deal than another; they only change how fast the multiplier
 * grows and how likely the fall is.
 */
public enum TowerDifficulty {

    EASY(4, 1),
    MEDIUM(3, 1),
    HARD(2, 1),
    EXPERT(3, 2),
    MASTER(4, 3);

    private final int tiles;
    private final int bombs;

    TowerDifficulty(int tiles, int bombs) {
        this.tiles = tiles;
        this.bombs = bombs;
    }

    public int tiles() {
        return tiles;
    }

    public int bombs() {
        return bombs;
    }

    /** Lower case id, for configuration and language keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Chance of clearing one floor. */
    public double safeChance() {
        return (double) (tiles - bombs) / tiles;
    }

    /** Multiplier after clearing {@code level} floors. */
    public double multiplier(int level, double houseEdge) {
        return MinesTable.towerMultiplier(level, tiles, bombs, houseEdge);
    }

    /** The difficulty with that id, or {@code fallback} when there is none. */
    public static TowerDifficulty of(String id, TowerDifficulty fallback) {
        if (id != null) {
            for (TowerDifficulty difficulty : values()) {
                if (difficulty.id().equalsIgnoreCase(id.trim())) {
                    return difficulty;
                }
            }
        }
        return fallback;
    }
}
