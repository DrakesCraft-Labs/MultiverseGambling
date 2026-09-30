package com.chagui68.multiversegambling.engine;

/**
 * Dados con objetivo: se apuesta a que la tirada saldra por encima o por debajo
 * de un numero entre 0 y 100 con dos decimales, estilo casas de criptomonedas.
 */
public final class DiceTable {

    /** Las tiradas se mueven en [0, 100) con dos decimales. */
    public static final double MIN_TARGET = 0.01;
    public static final double MAX_TARGET = 99.99;

    private DiceTable() {
    }

    public static double roll() {
        return Math.floor(Rng.next() * 10000.0) / 100.0;
    }

    /** Probabilidad (en %) de ganar apostando por encima del objetivo. */
    public static double winChanceOver(double target) {
        return round2(100.0 - target);
    }

    /** Probabilidad (en %) de ganar apostando por debajo del objetivo. */
    public static double winChanceUnder(double target) {
        return round2(target);
    }

    /** Pago justo recortado: (1 - edge) * 100 / prob. */
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
