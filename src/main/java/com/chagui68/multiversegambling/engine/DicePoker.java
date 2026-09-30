package com.chagui68.multiversegambling.engine;

import java.util.Arrays;

/**
 * Dice poker: every player rolls 5 dice and the best hand wins. The score includes
 * tie breakers, so two hands of the same kind are compared by their values.
 */
public final class DicePoker {

    public static final int DICE = 5;

    public enum Hand {
        HIGH_CARD("High card", 0),
        PAIR("Pair", 1),
        TWO_PAIR("Two pair", 2),
        THREE_OF_A_KIND("Three of a kind", 3),
        STRAIGHT("Straight", 4),
        FULL_HOUSE("Full house", 5),
        FOUR_OF_A_KIND("Four of a kind", 6),
        FIVE_OF_A_KIND("Five of a kind", 7);

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

    /** Category of a roll of 5 six sided dice. */
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
            return Hand.FIVE_OF_A_KIND;
        }
        if (max == 4) {
            return Hand.FOUR_OF_A_KIND;
        }
        if (max == 3) {
            return pairs >= 1 ? Hand.FULL_HOUSE : Hand.THREE_OF_A_KIND;
        }
        if (pairs == 2) {
            return Hand.TWO_PAIR;
        }
        if (pairs == 1) {
            return Hand.PAIR;
        }
        return isStraight(counts) ? Hand.STRAIGHT : Hand.HIGH_CARD;
    }

    /**
     * Comparable score: the higher, the better the hand. Two rolls with the same score
     * are an exact tie.
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
                throw new IllegalArgumentException("Die out of range: " + die);
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

    /** Helper to debug hand against hand. */
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            int[] dice = roll();
            System.out.println(Arrays.toString(dice) + " -> " + handOf(dice).label()
                    + " (" + score(dice) + ")");
        }
    }
}
