package com.chagui68.multiversegambling.engine;

/**
 * The colour wheel: the star bet of the group casino.
 *
 * <p>It is the European wheel seen as three colours. What matters is that the payment
 * of each colour comes from its real pockets, not from an invented number: with 37
 * pockets and a single green one, green <em>has</em> to pay around 36 times for the bet
 * to keep the same edge as red or black. That way the green button is exciting without
 * being a trap.</p>
 */
public final class ColorWheel {

    public enum Outcome {
        RED, BLACK, GREEN
    }

    private final int red;
    private final int black;
    private final int green;
    private final double houseEdge;

    public ColorWheel(int red, int black, int green, double houseEdge) {
        if (red < 1 || black < 1 || green < 1) {
            throw new IllegalArgumentException("Every colour needs at least one pocket");
        }
        if (houseEdge < 0 || houseEdge >= 1) {
            throw new IllegalArgumentException("Invalid house edge: " + houseEdge);
        }
        this.red = red;
        this.black = black;
        this.green = green;
        this.houseEdge = houseEdge;
    }

    /** 18 red, 18 black and 1 green: the European wheel. */
    public static ColorWheel european() {
        return new ColorWheel(18, 18, 1, 0.027);
    }

    public int pockets() {
        return red + black + green;
    }

    public int pockets(Outcome outcome) {
        return switch (outcome) {
            case RED -> red;
            case BLACK -> black;
            case GREEN -> green;
        };
    }

    /** Real probability of each colour. */
    public double chance(Outcome outcome) {
        return (double) pockets(outcome) / pockets();
    }

    /** Payment of each colour, rounded down to 2 decimals. */
    public double payout(Outcome outcome) {
        double fair = (1.0 - houseEdge) / chance(outcome);
        return Math.floor(fair * 100.0) / 100.0;
    }

    /** The return is the same backing any colour. */
    public double rtp(Outcome outcome) {
        return chance(outcome) * payout(outcome);
    }

    public Outcome spin() {
        return spin(Rng.generator()::nextDouble);
    }

    /**
     * Spins with a roll already computed in [0,1). This is what lets the wheel use the
     * provably fair randomness of the casino instead of an internal generator.
     */
    public Outcome spin(java.util.function.DoubleSupplier uniforms) {
        double uniform = Math.max(0, Math.min(0.999999999, uniforms.getAsDouble()));
        int target = 1 + (int) Math.floor(uniform * pockets());
        if (target <= red) {
            return Outcome.RED;
        }
        return target <= red + black ? Outcome.BLACK : Outcome.GREEN;
    }

    public static String label(Outcome outcome) {
        return switch (outcome) {
            case RED -> "Red";
            case BLACK -> "Black";
            case GREEN -> "Green";
        };
    }
}
