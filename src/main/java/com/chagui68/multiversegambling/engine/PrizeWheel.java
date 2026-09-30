package com.chagui68.multiversegambling.engine;

import java.util.function.DoubleSupplier;

/**
 * Lucky wheel: a physical wheel of tiles.
 *
 * <p>Every tile has its own multiplier and all of them are equally likely, so the
 * return is simply the average. That makes it impossible to sneak in a table that
 * overpays: {@link #rtp()} gives it away and a test pins it.</p>
 */
public final class PrizeWheel {

    /**
     * Default wheel: 12 tiles, many of them zero and a couple of big hits.
     * The average is 0.95, that is a 95% return.
     */
    private static final double[] DEFAULT = {0, 1.3, 0, 2.0, 0, 4.0, 0, 1.6, 0, 2.5, 0, 0};

    private final double[] pockets;

    public PrizeWheel(double... pockets) {
        if (pockets.length < 2) {
            throw new IllegalArgumentException("The wheel needs at least 2 tiles");
        }
        for (double pocket : pockets) {
            if (pocket < 0) {
                throw new IllegalArgumentException("A negative multiplier makes no sense: " + pocket);
            }
        }
        this.pockets = pockets.clone();
    }

    public static PrizeWheel defaultWheel() {
        return new PrizeWheel(DEFAULT);
    }

    public int size() {
        return pockets.length;
    }

    public double multiplier(int index) {
        return pockets[Math.floorMod(index, pockets.length)];
    }

    public double[] pockets() {
        return pockets.clone();
    }

    /** Winning index: every tile weighs the same. */
    public int spin(DoubleSupplier uniforms) {
        double uniform = uniforms.getAsDouble();
        int index = (int) Math.floor(uniform * pockets.length);
        return Math.max(0, Math.min(pockets.length - 1, index));
    }

    public int spin() {
        return spin(Rng.generator()::nextDouble);
    }

    public double rtp() {
        double sum = 0;
        for (double pocket : pockets) {
            sum += pocket;
        }
        return sum / pockets.length;
    }

    public double best() {
        double best = 0;
        for (double pocket : pockets) {
            best = Math.max(best, pocket);
        }
        return best;
    }

    /** Chance of landing on the highest tile. */
    public double bestChance() {
        int count = 0;
        double best = best();
        for (double pocket : pockets) {
            if (pocket == best) {
                count++;
            }
        }
        return (double) count / pockets.length;
    }
}
