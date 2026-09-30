package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * El rasca y gana.
 *
 * <p>Se destapan tres de nueve casillas. Tres simbolos iguales pagan el premio del
 * simbolo y dos iguales devuelven parte de la apuesta. Los numeros no estan
 * elegidos a ojo: {@link #rtp()} calcula el retorno exacto y un test lo fija, asi
 * que cambiar un pago sin recalcular se nota enseguida.</p>
 */
public final class ScratchCardTable {

    /** Caras del carton y su frecuencia. */
    public enum Face {
        CEREZA("cereza", 40, 3.0, "&cCereza"),
        LIMON("limon", 30, 2.0, "&eLimon"),
        CAMPANA("campana", 18, 1.5, "&6Campana"),
        DIAMANTE("diamante", 8, 5.0, "&bDiamante"),
        SIETE("siete", 3, 10.0, "&aSiete"),
        CORONA("corona", 1, 50.0, "&dCorona");

        private final String id;
        private final double weight;
        private final double triple;
        private final String label;

        Face(String id, double weight, double triple, String label) {
            this.id = id;
            this.weight = weight;
            this.triple = triple;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public double weight() {
            return weight;
        }

        /** Pago por acertar tres iguales. */
        public double triple() {
            return triple;
        }

        public String label() {
            return label;
        }
    }

    /** Casillas del carton. */
    public static final int CELLS = 9;
    /** Destapes que hace el jugador. */
    public static final int PICKS = 3;
    /** Pago por acertar exactamente dos iguales. */
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

    /** Pago segun las tres casillas que el jugador haya destapado. */
    public double payout(List<Face> picked) {
        if (picked.size() != PICKS) {
            throw new IllegalArgumentException("Se destapan exactamente " + PICKS + " casillas");
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
        // Dos iguales de cualquier pareja.
        for (int i = 0; i < picked.size(); i++) {
            for (int j = i + 1; j < picked.size(); j++) {
                if (picked.get(i) == picked.get(j)) {
                    return PAIR_PAYOUT;
                }
            }
        }
        return 0;
    }

    /** Retorno teorico exacto al jugador. */
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

    /** Probabilidad de rascar algo, aunque sea la devolucion parcial. */
    public double hitChance() {
        double anyWin = 0;
        for (Face face : Face.values()) {
            double p = probabilityOf(face);
            anyWin += p * p * p + 3 * p * p * (1 - p);
        }
        return anyWin;
    }
}
