package com.chagui68.multiversegambling.engine;

/**
 * Odds of a duel against the house.
 *
 * <p>The player and the dealer take turns pulling the trigger of a revolver with
 * {@code bullets} loaded chambers, the player always going first, until somebody meets a
 * loaded chamber. The cylinder is spun <b>once</b>, so the duel is decided by where the
 * loaded chambers sit and not by a fresh roll per pull. That is what makes the chance of
 * the player winning exactly computable, and it is the number this table pays on: with
 * {@code b} loaded chambers out of {@code c}, the first loaded chamber sits in position
 * {@code k} with probability {@code C(c - k, b - 1) / C(c, b)}, and the player wins when
 * that position is an even one, which is a dealer turn.</p>
 *
 * <p>Free of Bukkit on purpose: the odds of the duel are checked by tests.</p>
 */
public final class HouseDuelTable {

    /**
     * Ceiling of the multiplier, so a rare configuration cannot print money.
     */
    public static final double MAX_MULTIPLIER = 100.0;

    private HouseDuelTable() {
    }

    /**
     * Chance the dealer meets the bullet first, in {@code [0,1]}.
     */
    public static double playerWinChance(int chambers, int bullets) {
        int count = Math.max(2, chambers);
        int loaded = Math.max(1, Math.min(bullets, count - 1));
        double arrangements = combinations(count, loaded);
        if (arrangements <= 0) {
            return 0;
        }
        double chance = 0;
        for (int position = 2; position <= count - loaded + 1; position += 2) {
            chance += combinations(count - position, loaded - 1) / arrangements;
        }
        return chance;
    }

    /**
     * What a win pays: the house edge taken out of a fair bet.
     */
    public static double multiplier(int chambers, int bullets, double houseEdge) {
        double chance = playerWinChance(chambers, bullets);
        if (chance <= 0) {
            return 1.0;
        }
        double fair = (1.0 - edge(houseEdge)) / chance;
        return Math.max(1.0, Math.min(MAX_MULTIPLIER, fair));
    }

    /**
     * Theoretical return of the duel, which never goes above {@code 1 - house edge}.
     */
    public static double rtp(int chambers, int bullets, double houseEdge) {
        return playerWinChance(chambers, bullets) * multiplier(chambers, bullets, houseEdge);
    }

    private static double edge(double houseEdge) {
        if (Double.isNaN(houseEdge)) {
            return 0;
        }
        return Math.max(0.0, Math.min(0.5, houseEdge));
    }

    /**
     * Binomial coefficient, exact: the cylinder is small and the numbers stay whole.
     */
    static double combinations(int n, int k) {
        if (k < 0 || n < 0 || k > n) {
            return 0;
        }
        double result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }
        return Math.round(result);
    }
}
