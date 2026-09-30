package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Carrera de caballos: cada caballo recibe una "fuerza" oculta, de la que salen
 * tanto las probabilidades que se muestran como el resultado real. Asi el pago
 * nunca se puede separar del riesgo real del caballo.
 */
public final class HorseOdds {

    private HorseOdds() {
    }

    /** Un caballo con su fuerza (probabilidad de ganar) y el pago asociado. */
    public record Runner(int index, double strength, double odds) {
    }

    /**
     * Genera un campo de {@code count} caballos con fuerzas aleatorias.
     * Los pagos son justos recortados: {@code (1 - edge) / probabilidad}.
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

    /** Elige al ganador respetando las fuerzas de cada caballo. */
    public static Runner winner(List<Runner> field) {
        WeightedTable<Runner> table = WeightedTable.of();
        for (Runner runner : field) {
            table.add(runner, runner.strength());
        }
        return table.roll();
    }

    /**
     * Simula el avance de los caballos durante {@code steps} pasos. Devuelve, por
     * cada paso, la lista de posiciones.
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
