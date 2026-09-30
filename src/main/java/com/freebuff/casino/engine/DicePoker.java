package com.freebuff.casino.engine;

import java.util.Arrays;

/**
 * Poker de dados: cada jugador tira 5 dados y gana la mejor mano. La puntuacion
 * incluye desempates, de modo que dos manos iguales se comparan por sus valores.
 */
public final class DicePoker {

    public static final int DICE = 5;

    public enum Hand {
        CARTA_ALTA("Carta alta", 0),
        PAREJA("Pareja", 1),
        DOBLE_PAREJA("Doble pareja", 2),
        TRIO("Trio", 3),
        ESCALERA("Escalera", 4),
        FULL("Full", 5),
        POKER("Poker", 6),
        REPOKER("Repoker", 7);

        private final String label;
        private final int strength;

        Hand(String label, int strength) {
            this.label = label;
            this.strength = strength;
        }

        public String label() {
            return label;
        }

        public int strength() {
            return strength;
        }
    }

    private DicePoker() {
    }

    public static int[] roll() {
        return Rng.dice(DICE, 6);
    }

    /** Categoria de una tirada de 5 dados de 6 caras. */
    public static Hand handOf(int[] dice) {
        int[] counts = counts(dice);
        int max = 0;
        int pairs = 0;
        for (int count : counts) {
            max = Math.max(max, count);
            if (count == 2) {
                pairs++;
            }
        }
        if (max == 5) {
            return Hand.REPOKER;
        }
        if (max == 4) {
            return Hand.POKER;
        }
        if (max == 3) {
            return pairs >= 1 ? Hand.FULL : Hand.TRIO;
        }
        if (pairs == 2) {
            return Hand.DOBLE_PAREJA;
        }
        if (pairs == 1) {
            return Hand.PAREJA;
        }
        return isStraight(counts) ? Hand.ESCALERA : Hand.CARTA_ALTA;
    }

    /**
     * Puntuacion comparable: cuanto mas alta, mejor mano. Dos tiradas con la misma
     * puntuacion son un empate exacto.
     */
    public static int score(int[] dice) {
        Hand hand = handOf(dice);
        int[] counts = counts(dice);
        int score = hand.strength() * 1_000_000;
        int multiplier = 100_000;
        for (int face = 6; face >= 1; face--) {
            if (counts[face] > 0) {
                score += counts[face] * multiplier + face;
                multiplier /= 10;
                if (multiplier == 0) {
                    break;
                }
            }
        }
        return score;
    }

    public static String describe(int[] dice) {
        StringBuilder builder = new StringBuilder();
        for (int die : dice) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(die);
        }
        return builder.toString();
    }

    private static int[] counts(int[] dice) {
        int[] counts = new int[7];
        for (int die : dice) {
            if (die < 1 || die > 6) {
                throw new IllegalArgumentException("Dado fuera de rango: " + die);
            }
            counts[die]++;
        }
        return counts;
    }

    private static boolean isStraight(int[] counts) {
        int distinct = 0;
        for (int face = 1; face <= 6; face++) {
            if (counts[face] > 0) {
                distinct++;
            }
        }
        if (distinct != 5) {
            return false;
        }
        return counts[1] == 0 || counts[6] == 0;
    }

    /** Utilidad para depurar mano a mano. */
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            int[] dice = roll();
            System.out.println(Arrays.toString(dice) + " -> " + handOf(dice).label()
                    + " (" + score(dice) + ")");
        }
    }
}
