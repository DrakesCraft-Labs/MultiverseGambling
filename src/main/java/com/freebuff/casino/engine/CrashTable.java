package com.freebuff.casino.engine;

/**
 * El juego de Crash: la curva sube sola y hay que salirse antes de que reviente.
 *
 * <p>El punto de explosion se deriva de un uniforme {@code u} invirtiendo
 * {@code P(X >= x) = (1 - edge) / x}. Con eso, retirarse en cualquier objetivo
 * {@code t} tiene valor esperado exactamente {@code 1 - edge}: la ventaja de la
 * casa es la configurada y no depende del objetivo elegido. La probabilidad
 * {@code edge} se concentra en el estallido instantaneo a 1.00x.</p>
 */
public final class CrashTable {

    private CrashTable() {
    }

    /**
     * @param u          uniforme en [0,1), idealmente de una tirada verificable
     * @param houseEdge  ventaja de la casa, p. ej. 0.01
     * @return punto de explosion truncado a 2 decimales, nunca menor que 1.00
     */
    public static double crashPoint(double u, double houseEdge) {
        if (u < 0 || u >= 1) {
            throw new IllegalArgumentException("u debe estar en [0,1)");
        }
        if (u < houseEdge) {
            return 1.00;
        }
        double raw = (1.0 - houseEdge) / (1.0 - u);
        return Math.max(1.00, Math.floor(raw * 100.0) / 100.0);
    }

    /** Multiplicador visible tras {@code seconds} jugados. */
    public static double multiplierAt(double seconds, double growthPerSecond) {
        if (seconds <= 0) {
            return 1.0;
        }
        return Math.pow(growthPerSecond, seconds);
    }

    /** Segundos que tarda la curva en alcanzar {@code target}. */
    public static double secondsToReach(double target, double growthPerSecond) {
        if (target <= 1.0) {
            return 0;
        }
        return Math.log(target) / Math.log(growthPerSecond);
    }

    /**
     * Probabilidad teorica de que la curva alcance {@code target} antes de estallar.
     * Es el pilar del juego: si esto no cuadra, el jugador tendria ventaja.
     */
    public static double reachChance(double target, double houseEdge) {
        if (target <= 1.0) {
            return 1.0;
        }
        return (1.0 - houseEdge) / target;
    }

    /** Tasa de crecimiento para que la curva doble cada {@code doubleEverySeconds}. */
    public static double growthRate(double doubleEverySeconds) {
        return Math.pow(2.0, 1.0 / doubleEverySeconds);
    }
}
