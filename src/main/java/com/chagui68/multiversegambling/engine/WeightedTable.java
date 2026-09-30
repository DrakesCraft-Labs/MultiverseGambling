package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Reusable weighted probability table: it serves the slot reels as well as the shared
 * jackpot, the lucky wheel or any prize draw. The weights do not have to add up to 1:
 * they normalise themselves.
 */
public final class WeightedTable<T> {

    private record Entry<T>(T value, double weight, double cumulative) {
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private double total;

    public static <T> WeightedTable<T> of() {
        return new WeightedTable<>();
    }

    /**
     * Adds a value with its weight. Weights <= 0 are ignored.
     */
    public WeightedTable<T> add(T value, double weight) {
        if (weight <= 0 || Double.isNaN(weight)) {
            return this;
        }
        total += weight;
        entries.add(new Entry<>(value, weight, total));
        return this;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public double totalWeight() {
        return total;
    }

    public List<T> values() {
        List<T> out = new ArrayList<>(entries.size());
        for (Entry<T> entry : entries) {
            out.add(entry.value());
        }
        return Collections.unmodifiableList(out);
    }

    public double weightOf(T value) {
        for (Entry<T> entry : entries) {
            if (entry.value().equals(value)) {
                return entry.weight();
            }
        }
        return 0;
    }

    /**
     * Exact chance (0..1) of {@code value} coming up.
     */
    public double probability(T value) {
        return total <= 0 ? 0 : weightOf(value) / total;
    }

    public T roll() {
        return roll(Rng.generator());
    }

    /**
     * Returns the value matching a roll already computed in [0,1). This is what lets the
     * provably fair randomness feed these tables.
     */
    public T roll(double uniform) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("Tabla ponderada vacia");
        }
        double target = Math.max(0, Math.min(0.999999999, uniform)) * total;
        for (Entry<T> entry : entries) {
            if (target < entry.cumulative()) {
                return entry.value();
            }
        }
        return entries.get(entries.size() - 1).value();
    }

    public T roll(RandomGenerator generator) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("Tabla ponderada vacia");
        }
        double target = generator.nextDouble() * total;
        for (Entry<T> entry : entries) {
            if (target < entry.cumulative()) {
                return entry.value();
            }
        }
        return entries.get(entries.size() - 1).value();
    }

    public Optional<T> tryRoll() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(roll());
    }
}
