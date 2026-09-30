package com.chagui68.multiversegambling.engine;

/**
 * Dice with a target: you bet that the roll comes over or under a number between 0 and
 * 100 with two decimals, in the style of the crypto casinos.
 */
public final class DiceTable {

    /**
     * Rolls move in [0, 100) with two decimals.
     */
    public static final double MIN_TARGET = 0.01;
    public static final double MAX_TARGET = 99.99;

    private DiceTable() {
    }

    public static double roll() {
        return Math.floor(Rng.next() * 10000.0) / 100.0;
    }

    /**
     * Chance (in %) of winning when betting over the target.
     */
    public static double winChanceOver(double target) {
        return round2(100.0 - target);
    }

    /**
     * Chance (in %) of winning when betting under the target.
     */
    public static double winChanceUnder(double target) {
        return round2(target);
    }

    /**
     * Trimmed fair payout: (1 - edge) * 100 / probability.
     */
    public static double payout(double winChancePercent, double houseEdge) {
        if (winChancePercent <= 0) {
            return 0;
        }
        return (1.0 - houseEdge) * 100.0 / winChancePercent;
    }

    public static boolean wins(double roll, double target, boolean over) {
        return over ? roll > target : roll < target;
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
