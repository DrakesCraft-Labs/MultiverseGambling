package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * One card of the French deck.
 */
public record Card(Suit suit, int rank) {

    public enum Suit {
        SPADES("\u2660"),
        HEARTS("\u2665"),
        DIAMONDS("\u2666"),
        CLUBS("\u2663");

        private final String glyph;

        Suit(String glyph) {
            this.glyph = glyph;
        }

        public String glyph() {
            return glyph;
        }
    }

    public static final int JACK = 11;
    public static final int QUEEN = 12;
    public static final int KING = 13;
    public static final int ACE = 14;

    public static Card of(Suit suit, int rank) {
        return new Card(suit, rank);
    }

    public String rankLabel() {
        return switch (rank) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(rank);
        };
    }

    /**
     * Blackjack value of this single card; an ace counts as 11 and is adjusted in the hand.
     */
    public int blackjackValue() {
        if (rank == ACE) {
            return 11;
        }
        return Math.min(rank, 10);
    }

    public boolean isAce() {
        return rank == ACE;
    }

    public String display() {
        return suit.glyph() + " " + rankLabel();
    }

    @Override
    public String toString() {
        return display();
    }

    /**
     * Multi deck shoe shuffled with Fisher-Yates.
     */
    public static final class Deck {

        private final List<Card> cards = new ArrayList<>();
        private int cursor;

        public Deck(int decks) {
            this(decks, Rng.generator());
        }

        public Deck(int decks, RandomGenerator generator) {
            for (int d = 0; d < Math.max(1, decks); d++) {
                for (Suit suit : Suit.values()) {
                    for (int rank = 2; rank <= ACE; rank++) {
                        cards.add(new Card(suit, rank));
                    }
                }
            }
            for (int i = cards.size() - 1; i > 0; i--) {
                int j = generator.nextInt(i + 1);
                Card tmp = cards.get(i);
                cards.set(i, cards.get(j));
                cards.set(j, tmp);
            }
        }

        public Card draw() {
            if (cursor >= cards.size()) {
                cursor = 0;
            }
            return cards.get(cursor++);
        }

        public int remaining() {
            return cards.size() - cursor;
        }
    }
}
