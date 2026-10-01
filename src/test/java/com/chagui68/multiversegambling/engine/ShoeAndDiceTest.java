package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The blackjack shoe is shuffled with provably fair rolls and the dice pay exactly the
 * chances they announce.
 */
class ShoeAndDiceTest {

    private static double[] rolls(long seed, int count) {
        Random random = new Random(seed);
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = random.nextDouble();
        }
        return out;
    }

    private static List<Card> drawAll(Card.Deck deck, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(deck.draw());
        }
        return cards;
    }

    @Test
    void aShuffledShoeHoldsEveryCardOfEveryDeck() {
        int decks = 6;
        Card.Deck deck = Card.Deck.shuffled(decks, rolls(7, Card.Deck.rollsFor(decks)));
        Map<Card, Integer> seen = new HashMap<>();
        for (Card card : drawAll(deck, 52 * decks)) {
            seen.merge(card, 1, Integer::sum);
        }
        assertEquals(52, seen.size(), "a card is missing from the shoe");
        seen.values().forEach(count -> assertEquals(decks, count, "a card is repeated or missing"));
    }

    @Test
    void theSameRollsGiveTheSameShoe() {
        int count = Card.Deck.rollsFor(2);
        List<Card> first = drawAll(Card.Deck.shuffled(2, rolls(42, count)), 104);
        List<Card> again = drawAll(Card.Deck.shuffled(2, rolls(42, count)), 104);
        List<Card> other = drawAll(Card.Deck.shuffled(2, rolls(43, count)), 104);
        assertEquals(first, again, "the shoe must be reproducible from its rolls");
        assertNotEquals(first, other, "different rolls must shuffle differently");
    }

    @Test
    void theDiceWinExactlyAsOftenAsTheyPay() {
        // Every roll from 0.00 to 99.99 once: the counts must match the announced chances.
        for (double target : new double[]{0.01, 1.5, 50.0, 75.25, 99.99}) {
            int over = 0;
            int under = 0;
            for (int i = 0; i < 10000; i++) {
                double roll = i / 100.0;
                if (DiceTable.wins(roll, target, true)) {
                    over++;
                }
                if (DiceTable.wins(roll, target, false)) {
                    under++;
                }
            }
            assertEquals(DiceTable.winChanceOver(target), over / 100.0, 1e-9, "over " + target);
            assertEquals(DiceTable.winChanceUnder(target), under / 100.0, 1e-9, "under " + target);
        }
    }
}
