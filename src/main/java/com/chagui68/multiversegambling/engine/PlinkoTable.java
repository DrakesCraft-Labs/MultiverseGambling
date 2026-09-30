package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Plinko: the ball falls down a pyramid and ends in a bucket. The buckets are not
 * invented, they come from the real binomial distribution of the bounces, so the table
 * always makes mathematical sense.
 *
 * <p>We round down to two decimals and apply a configurable cap: both things can only
 * <em>reduce</em> the return, never give the player an edge.</p>
 */
public final class PlinkoTable {

    private PlinkoTable() {
    }

    /**
     * Binomial chance of ending in {@code bucket} with {@code rows} rows.
     */
    public static double bucketChance(int rows, int bucket) {
        if (rows <= 0 || bucket < 0 || bucket > rows) {
            throw new IllegalArgumentException("bucket out of range");
        }
        double combinations = combinations(rows, bucket);
        return combinations / Math.pow(2.0, rows);
    }

    /**
     * Payment multipliers per bucket, left to right.
     *
     * <p>The player does not pick a bucket: the ball decides. That is why the return is
     * computed over the sum of every bucket and the fair payout carries an extra
     * {@code rows + 1} divisor. Forgetting it makes the table overpay (rows + 1) times,
     * which is exactly the bug the test {@code theTableNeverFavoursThePlayer} catches.</p>
     */
    public static double[] multipliers(int rows, double houseEdge, double cap) {
        double[] out = new double[rows + 1];
        double buckets = rows + 1.0;
        for (int bucket = 0; bucket <= rows; bucket++) {
            double chance = bucketChance(rows, bucket);
            double fair = (1.0 - houseEdge) / (buckets * chance);
            out[bucket] = Math.min(cap, Math.floor(fair * 100.0) / 100.0);
        }
        return out;
    }

    /**
     * Simulates the fall: on every row the ball goes left or right and the final bucket
     * is the number of bounces to the right.
     */
    public static int drop(int rows) {
        int bucket = 0;
        for (int i = 0; i < rows; i++) {
            if (Rng.chance(0.5)) {
                bucket++;
            }
        }
        return bucket;
    }

    /**
     * Sequence of directions (false = left, true = right) to animate the fall.
     */
    public static List<Boolean> path(int rows) {
        List<Boolean> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            out.add(Rng.chance(0.5));
        }
        return out;
    }

    /**
     * Theoretical return to the player of a given table.
     */
    public static double rtp(double[] table, int rows, double cap) {
        double expected = 0;
        for (int bucket = 0; bucket < table.length; bucket++) {
            expected += bucketChance(rows, bucket) * table[bucket];
        }
        return expected;
    }

    static double combinations(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        k = Math.min(k, n - k);
        double result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }
        return Math.round(result);
    }
}
