package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Horse race: every horse gets a hidden "strength", which produces both the odds shown
 * and the real result. That way the payout can never be separated from the real risk of
 * the horse.
 */
public final class HorseOdds {

    private HorseOdds() {
    }

    /**
     * A horse with its strength (chance of winning) and the associated payout.
     */
    public record Runner(int index, double strength, double odds) {
    }

    /**
     * Builds a field of {@code count} horses with random strengths.
     * The payouts are fair, trimmed down: {@code (1 - edge) / probability}.
     */
    public static List<Runner> field(int count, double houseEdge) {
        List<Double> strengths = new ArrayList<>(count);
        double total = 0;
        for (int i = 0; i < count; i++) {
            double strength = 0.35 + Rng.next() * 1.65;
            strengths.add(strength);
            total += strength;
        }
        List<Runner> field = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double chance = strengths.get(i) / total;
            double odds = Math.max(1.05, Math.floor((1.0 - houseEdge) / chance * 100.0) / 100.0);
            field.add(new Runner(i, strengths.get(i), odds));
        }
        return field;
    }

    /**
     * Picks the winner respecting the strength of every horse.
     */
    public static Runner winner(List<Runner> field) {
        WeightedTable<Runner> table = WeightedTable.of();
        for (Runner runner : field) {
            table.add(runner, runner.strength());
        }
        return table.roll();
    }

    /**
     * Simulates the horses advancing for {@code steps} steps. Returns, for every step,
     * the list of positions.
     */
    public static List<double[]> simulate(List<Runner> field, int steps) {
        int runners = field.size();
        double[] positions = new double[runners];
        List<double[]> frames = new ArrayList<>(steps);
        for (int step = 0; step < steps; step++) {
            for (int i = 0; i < runners; i++) {
                positions[i] += field.get(i).strength() * (0.5 + Rng.next());
            }
            frames.add(positions.clone());
        }
        return frames;
    }
}
