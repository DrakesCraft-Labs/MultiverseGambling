package com.chagui68.multiversegambling.engine;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Randomness source of the casino. On purpose it is pure (no Bukkit) so it can be unit
 * tested and so every probability lives in a single place.
 */
public final class Rng {

    private Rng() {
    }

    public static RandomGenerator generator() {
        return ThreadLocalRandom.current();
    }

    /**
     * Uniform in [0, 1).
     */
    public static double next() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /**
     * Uniform integer in [min, max], both included.
     */
    public static int intBetween(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max (" + max + ") < min (" + min + ")");
        }
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /**
     * {@code true} with the given probability (0..1).
     */
    public static boolean chance(double probability) {
        if (probability <= 0) {
            return false;
        }
        return probability >= 1 || next() < probability;
    }

    public static <T> T pick(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick from an empty list");
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    public static <T> T pick(T[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Cannot pick from an empty array");
        }
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    /**
     * Shuffles in place with Fisher-Yates.
     */
    public static <T> void shuffle(List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = ThreadLocalRandom.current().nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }

    /**
     * Rolls {@code count} dice with {@code sides} faces.
     */
    public static int[] dice(int count, int sides) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = intBetween(1, sides);
        }
        return out;
    }
}
