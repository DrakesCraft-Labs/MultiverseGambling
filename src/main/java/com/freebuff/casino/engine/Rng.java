package com.freebuff.casino.engine;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Fuente de azar del casino. Es intencionadamente puro (sin Bukkit) para poder
 * probarlo con tests y para que todas las probabilidades vivan en un solo sitio.
 */
public final class Rng {

    private Rng() {
    }

    public static RandomGenerator generator() {
        return ThreadLocalRandom.current();
    }

    /** Uniforme en [0, 1). */
    public static double next() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** Entero uniforme en [min, max], ambos incluidos. */
    public static int intBetween(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max (" + max + ") < min (" + min + ")");
        }
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /** {@code true} con la probabilidad indicada (0..1). */
    public static boolean chance(double probability) {
        if (probability <= 0) {
            return false;
        }
        return probability >= 1 || next() < probability;
    }

    public static <T> T pick(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("No se puede elegir de una lista vacia");
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    public static <T> T pick(T[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("No se puede elegir de un array vacio");
        }
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    /** Mezcla in-place con Fisher-Yates. */
    public static <T> void shuffle(List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = ThreadLocalRandom.current().nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }

    /** Lanza {@code count} dados de {@code sides} caras. */
    public static int[] dice(int count, int sides) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = intBetween(1, sides);
        }
        return out;
    }
}
