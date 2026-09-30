package com.chagui68.multiversegambling.engine;

/**
 * The Crash game: the curve climbs on its own and you must get out before it bursts.
 *
 * <p>The crash point is derived from a uniform {@code u} by inverting
 * {@code P(X >= x) = (1 - edge) / x}. With that, cashing out at any target {@code t}
 * has an expected value of exactly {@code 1 - edge}: the house edge is the configured
 * one and does not depend on the chosen target. The probability {@code edge} is
 * concentrated in the instant burst at 1.00x.</p>
 */
public final class CrashTable {

    private CrashTable() {
    }

    /**
     * @param u         uniform in [0,1), ideally from a provably fair roll
     * @param houseEdge house edge, for example 0.01
     * @return crash point truncated to 2 decimals, never below 1.00
     */
    public static double crashPoint(double u, double houseEdge) {
        if (u < 0 || u >= 1) {
            throw new IllegalArgumentException("u must be in [0,1)");
        }
        if (u < houseEdge) {
            return 1.00;
        }
        double raw = (1.0 - houseEdge) / (1.0 - u);
        return Math.max(1.00, Math.floor(raw * 100.0) / 100.0);
    }

    /**
     * Multiplier visible after {@code seconds} played.
     */
    public static double multiplierAt(double seconds, double growthPerSecond) {
        if (seconds <= 0) {
            return 1.0;
        }
        return Math.pow(growthPerSecond, seconds);
    }

    /**
     * Seconds the curve takes to reach {@code target}.
     */
    public static double secondsToReach(double target, double growthPerSecond) {
        if (target <= 1.0) {
            return 0;
        }
        return Math.log(target) / Math.log(growthPerSecond);
    }

    /**
     * Theoretical probability that the curve reaches {@code target} before bursting.
     * It is the backbone of the game: if this does not add up, the player has an edge.
     */
    public static double reachChance(double target, double houseEdge) {
        if (target <= 1.0) {
            return 1.0;
        }
        return (1.0 - houseEdge) / target;
    }

    /**
     * Growth rate so the curve doubles every {@code doubleEverySeconds}.
     */
    public static double growthRate(double doubleEverySeconds) {
        return Math.pow(2.0, 1.0 / doubleEverySeconds);
    }
}
