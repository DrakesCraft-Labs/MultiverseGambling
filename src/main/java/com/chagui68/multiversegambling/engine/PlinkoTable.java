package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Plinko: la bolita cae por una piramide y termina en un cubo. Los cubos no se
 * inventan, salen de la distribucion binomial real de los rebotes, asi que la
 * tabla siempre tiene sentido matematico.
 *
 * <p>Recortamos a la baja a dos decimales y aplicamos un tope configurable: ambas
 * cosas solo pueden <em>reducir</em> el retorno, nunca dar ventaja al jugador.</p>
 */
public final class PlinkoTable {

    private PlinkoTable() {
    }

    /** Probabilidad binomial de acabar en el cubo {@code bucket} con {@code rows} filas. */
    public static double bucketChance(int rows, int bucket) {
        if (rows <= 0 || bucket < 0 || bucket > rows) {
            throw new IllegalArgumentException("cubo fuera de rango");
        }
        double combinations = combinations(rows, bucket);
        return combinations / Math.pow(2.0, rows);
    }

    /**
     * Multiplicadores de pago por cubo, de izquierda a derecha.
     *
     * <p>El jugador no elige cubo: la bolita decide. Por eso el retorno se calcula
     * sobre la suma de todos los cubos y el pago justo lleva un divisor extra
     * {@code filas + 1}. Olvidarlo hace que la tabla pague (filas + 1) veces de mas,
     * que es exactamente el error que caza el test {@code laTablaNuncaDaVentajaAlJugador}.</p>
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
     * Simula la caida: en cada fila la bolita va a izquierda o derecha y el
     * ultimo cubo es el numero de desvios a la derecha.
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

    /** Secuencia de direcciones (false = izquierda, true = derecha) para animar la caida. */
    public static List<Boolean> path(int rows) {
        List<Boolean> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            out.add(Rng.chance(0.5));
        }
        return out;
    }

    /** Retorno teorico al jugador de una tabla concreta. */
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
