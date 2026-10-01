package com.chagui68.multiversegambling.world.anim;

/**
 * Geometry of a horizontal wheel: where a sector sits and how far a spin has to travel
 * to stop on a chosen one.
 *
 * <p>Free of Bukkit on purpose, so the spin of the roulette can be checked by tests
 * instead of by eye. Angles grow clockwise seen from above, the first sector sitting
 * on the {@code +Z} axis.</p>
 */
public final class WheelMath {

    /**
     * A full turn.
     */
    public static final double TAU = Math.PI * 2;

    private WheelMath() {
    }

    /**
     * Angle of sector {@code index} of {@code count}, evenly spread around the wheel.
     */
    public static double angleOf(int index, int count) {
        if (count <= 0) {
            return 0.0;
        }
        return TAU * Math.floorMod(index, count) / count;
    }

    /**
     * Folds any angle into {@code [0, TAU)}.
     */
    public static double normalize(double angle) {
        double wrapped = angle % TAU;
        return wrapped < 0 ? wrapped + TAU : wrapped;
    }

    /**
     * Arc a spin has to cover to go from one angle to another after a whole number of
     * turns, so the wheel never spins backwards and always lands on the target.
     */
    public static double sweep(double fromAngle, double targetAngle, int turns) {
        return TAU * Math.max(0, turns) + normalize(targetAngle - fromAngle);
    }

    /**
     * Ease out cubic: the ball leaves at full speed and creeps to a stop.
     */
    public static double ease(double progress) {
        double clamped = Math.max(0, Math.min(1, progress));
        double left = 1 - clamped;
        return 1 - left * left * left;
    }

    /**
     * Ease out quadratic: gentler than {@link #ease(double)} at the start, which keeps
     * the first frames of a big wheel from turning too far in one step.
     */
    public static double easeQuad(double progress) {
        double clamped = Math.max(0, Math.min(1, progress));
        double left = 1 - clamped;
        return 1 - left * left;
    }

    /**
     * How many tiles of the wheel each sector gets.
     *
     * <p>A tile is a straight block, so a wheel needs plenty of them to look round: a
     * wheel with few sectors splits every sector in several tiles. Equal sectors always
     * get the same number of tiles; weighted sectors (a jackpot, where a bigger stake is
     * a bigger slice) share {@code minimumTiles} by largest remainder, never less than
     * one tile each, so even the smallest stake is visible.</p>
     *
     * @param weights      size of each sector, any positive scale; {@code null} for equal
     * @param sectors      number of sectors when {@code weights} is {@code null}
     * @param minimumTiles tiles the whole wheel should at least have
     */
    public static int[] allocate(double[] weights, int sectors, int minimumTiles) {
        int count = weights == null ? sectors : weights.length;
        if (count <= 0) {
            return new int[0];
        }
        int[] tiles = new int[count];
        boolean equal = weights == null;
        if (!equal) {
            equal = true;
            for (double weight : weights) {
                if (Math.abs(weight - weights[0]) > 1e-9) {
                    equal = false;
                    break;
                }
            }
        }
        if (equal) {
            int each = Math.max(1, (int) Math.ceil(minimumTiles / (double) count));
            java.util.Arrays.fill(tiles, each);
            return tiles;
        }
        int total = Math.max(minimumTiles, count);
        double sum = 0;
        for (double weight : weights) {
            sum += Math.max(0, weight);
        }
        // Everybody gets one tile first; the rest is shared by weight.
        java.util.Arrays.fill(tiles, 1);
        int left = total - count;
        double[] remainders = new double[count];
        int given = 0;
        for (int i = 0; i < count; i++) {
            double share = sum <= 0 ? left / (double) count : left * Math.max(0, weights[i]) / sum;
            int whole = (int) Math.floor(share);
            tiles[i] += whole;
            given += whole;
            remainders[i] = share - whole;
        }
        for (int extra = given; extra < left; extra++) {
            int best = 0;
            for (int i = 1; i < count; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            tiles[best]++;
            remainders[best] = -1;
        }
        return tiles;
    }

    /**
     * X offset of a point at that angle and radius.
     */
    public static double x(double angle, double radius) {
        return radius * Math.sin(angle);
    }

    /**
     * Z offset of a point at that angle and radius.
     */
    public static double z(double angle, double radius) {
        return radius * Math.cos(angle);
    }
}
