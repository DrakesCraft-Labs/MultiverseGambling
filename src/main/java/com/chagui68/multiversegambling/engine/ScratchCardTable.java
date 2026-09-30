package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * The scratch card.
 *
 * <p>Three tiles out of nine are revealed. Three equal symbols pay the prize of the
 * symbol and two equal ones give part of the stake back. The numbers are not chosen by
 * eye: {@link #rtp()} computes the exact return and a test pins it, so changing a
 * payout without recomputing shows up immediately.</p>
 */
public final class ScratchCardTable {

    /** Faces of the card and how often they come up. */
    public enum Face {
        CHERRY("cherry", 40, 3.0),
        LEMON("lemon", 30, 2.0),
        BELL("bell", 18, 1.5),
        DIAMOND("diamond", 8, 5.0),
        SEVEN("seven", 3, 10.0),
        CROWN("crown", 1, 50.0);

        private final String id;
        private final double weight;
        private final double triple;

        Face(String id, double weight, double triple) {
            this.id = id;
            this.weight = weight;
            this.triple = triple;
        }

        public String id() {
            return id;
        }

        public double weight() {
            return weight;
        }

        /** Payout for hitting three of a kind. */
        public double triple() {
            return triple;
        }
    }

    /** Tiles on the card. */
    public static final int CELLS = 9;
    /** Reveals the player makes. */
    public static final int PICKS = 3;
    /** Payout for hitting exactly two of a kind. */
    public static final double PAIR_PAYOUT = 1.15;

    private final WeightedTable<Face> table = build();

    private static WeightedTable<Face> build() {
        WeightedTable<Face> weighted = new WeightedTable<>();
        for (Face face : Face.values()) {
            weighted.add(face, face.weight());
        }
        return weighted;
    }

    public List<Face> newCard() {
        return newCard(Rng.generator()::nextDouble);
    }

    public List<Face> newCard(DoubleSupplier uniforms) {
        List<Face> card = new ArrayList<>(CELLS);
        for (int i = 0; i < CELLS; i++) {
            card.add(table.roll(uniforms.getAsDouble()));
        }
        return card;
    }

    public double probabilityOf(Face face) {
        return table.probability(face);
    }

    /** Payout according to the three tiles the player revealed. */
    public double payout(List<Face> picked) {
        if (picked.size() != PICKS) {
            throw new IllegalArgumentException("Exactly " + PICKS + " tiles are revealed");
        }
        int counts = 0;
        Face first = picked.get(0);
        for (Face face : picked) {
            if (face == first) {
                counts++;
            }
        }
        if (counts == PICKS) {
            return first.triple();
        }
        // Two of a kind with any pair.
        for (int i = 0; i < picked.size(); i++) {
            for (int j = i + 1; j < picked.size(); j++) {
                if (picked.get(i) == picked.get(j)) {
                    return PAIR_PAYOUT;
                }
            }
        }
        return 0;
    }

    /** Exact theoretical return to the player. */
    public double rtp() {
        double expected = 0;
        for (Face face : Face.values()) {
            double p = probabilityOf(face);
            expected += p * p * p * face.triple();
        }
        for (Face face : Face.values()) {
            double p = probabilityOf(face);
            expected += 3 * p * p * (1 - p) * PAIR_PAYOUT;
        }
        return expected;
    }

    /** Chance of scratching something, even if it is only a partial refund. */
    public double hitChance() {
        double anyWin = 0;
        for (Face face : Face.values()) {
            double p = probabilityOf(face);
            anyWin += p * p * p + 3 * p * p * (1 - p);
        }
        return anyWin;
    }
}
