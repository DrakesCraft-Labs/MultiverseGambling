package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.Card.Suit;

import java.util.List;

class BlackjackHandTest {

    private static Card c(int rank) {
        return Card.of(Suit.SPADES, rank);
    }

    @Test
    void anAceIsWorthElevenUntilTheHandBusts() {
        assertEquals(21, BlackjackHand.value(List.of(c(Card.ACE), c(10))));
        // 11 + 5 + 5 = 21: the ace is still worth 11 because the hand does not bust.
        assertEquals(21, BlackjackHand.value(List.of(c(Card.ACE), c(5), c(5))));
        // 11 + 6 + 5 = 22, it busts, so the ace drops to 1 and the hand is 12.
        assertEquals(12, BlackjackHand.value(List.of(c(Card.ACE), c(6), c(5))));
        // With two aces only one of them can be worth 11.
        assertEquals(12, BlackjackHand.value(List.of(c(Card.ACE), c(Card.ACE))));
        // 11 + 11 + 11 = 33 -> the algorithm drops two aces to 1.
        assertEquals(13, BlackjackHand.value(List.of(c(Card.ACE), c(Card.ACE), c(Card.ACE))));
    }

    @Test
    void faceCardsAreWorthTen() {
        assertEquals(20, BlackjackHand.value(List.of(c(Card.KING), c(Card.QUEEN))));
        assertEquals(20, BlackjackHand.value(List.of(c(Card.JACK), c(Card.KING))));
    }

    @Test
    void aSoftHandIsDetectedCorrectly() {
        assertTrue(BlackjackHand.isSoft(List.of(c(Card.ACE), c(6))));
        assertFalse(BlackjackHand.isSoft(List.of(c(Card.ACE), c(6), c(10))));
        assertFalse(BlackjackHand.isSoft(List.of(c(10), c(7))));
    }

    @Test
    void itIsOnlyANaturalBlackjackWithTwoCards() {
        assertTrue(BlackjackHand.isBlackjack(List.of(c(Card.ACE), c(Card.KING))));
        assertTrue(BlackjackHand.isBlackjack(List.of(c(10), c(Card.ACE))));
        // 21 on three cards is 21, but it is not a natural.
        assertFalse(BlackjackHand.isBlackjack(List.of(c(7), c(7), c(7))));
        assertFalse(BlackjackHand.isBlackjack(List.of(c(Card.ACE), c(Card.ACE))));
    }

    @Test
    void theDealerStandsOnSeventeen() {
        assertTrue(BlackjackHand.dealerMustHit(List.of(c(10), c(6)), false));
        assertFalse(BlackjackHand.dealerMustHit(List.of(c(10), c(7)), false));
        // The optional rule: the dealer hits a soft 17.
        assertTrue(BlackjackHand.dealerMustHit(List.of(c(Card.ACE), c(6)), true));
        assertFalse(BlackjackHand.dealerMustHit(List.of(c(Card.ACE), c(6)), false));
    }

    @Test
    void aNaturalPaysThreeToTwoOnlyWhenTheDealerHasNothing() {
        List<Card> natural = List.of(c(Card.ACE), c(Card.KING));
        List<Card> normal = List.of(c(10), c(9));
        List<Card> dealerNatural = List.of(c(Card.ACE), c(Card.QUEEN));
        assertEquals(2.5, BlackjackHand.payout(natural, normal, true, false, false));
        assertEquals(1.0, BlackjackHand.payout(natural, dealerNatural, true, true, false), "push on naturals");
    }

    @Test
    void losingWithMoreThanTwentyOnePaysNothing() {
        assertEquals(0.0, BlackjackHand.payout(List.of(c(10), c(10), c(5)), List.of(c(10), c(8)), false, false, false));
    }

    @Test
    void aPushReturnsTheStakeAndAWinDoubles() {
        assertEquals(1.0, BlackjackHand.payout(List.of(c(10), c(9)), List.of(c(10), c(9)), false, false, false));
        assertEquals(2.0, BlackjackHand.payout(List.of(c(10), c(10)), List.of(c(10), c(8)), false, false, false));
        assertEquals(2.0, BlackjackHand.payout(List.of(c(10), c(6)), List.of(c(10), c(5), c(5)), false, false, true));
        assertEquals(0.0, BlackjackHand.payout(List.of(c(10), c(6)), List.of(c(10), c(9)), false, false, false));
    }

    @Test
    void theMultiDeckShoeNeverRunsOutOfCards() {
        Card.Deck deck = new Card.Deck(6);
        for (int i = 0; i < 6 * 52 + 10; i++) {
            assertTrue(deck.draw() != null);
        }
    }
}
