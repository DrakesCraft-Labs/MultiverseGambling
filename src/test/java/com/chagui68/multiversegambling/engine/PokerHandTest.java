package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The hand ranking decides who takes the pot, so every category, its order and the
 * tie breakers are pinned here.
 */
class PokerHandTest {

    /**
     * Cards written like "As Kh 10d 2c": rank then suit (s, h, d, c).
     */
    static List<Card> cards(String text) {
        List<Card> out = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            String rank = token.substring(0, token.length() - 1);
            char suit = token.charAt(token.length() - 1);
            int value = switch (rank) {
                case "A" -> Card.ACE;
                case "K" -> Card.KING;
                case "Q" -> Card.QUEEN;
                case "J" -> Card.JACK;
                default -> Integer.parseInt(rank);
            };
            Card.Suit s = switch (suit) {
                case 's' -> Card.Suit.SPADES;
                case 'h' -> Card.Suit.HEARTS;
                case 'd' -> Card.Suit.DIAMONDS;
                default -> Card.Suit.CLUBS;
            };
            out.add(new Card(s, value));
        }
        return out;
    }

    private static PokerHand.Result hand(String text) {
        return PokerHand.best(cards(text));
    }

    @Test
    void everyCategoryIsRecognised() {
        assertEquals(PokerHand.Category.ROYAL_FLUSH, hand("As Ks Qs Js 10s 2d 3c").category());
        assertEquals(PokerHand.Category.STRAIGHT_FLUSH, hand("9h 8h 7h 6h 5h Ad Ac").category());
        assertEquals(PokerHand.Category.FOUR_OF_A_KIND, hand("7s 7h 7d 7c Kd 2s 3s").category());
        assertEquals(PokerHand.Category.FULL_HOUSE, hand("Qs Qh Qd 4c 4d 9s 2h").category());
        assertEquals(PokerHand.Category.FLUSH, hand("Ac 9c 7c 4c 2c Kd Qd").category());
        assertEquals(PokerHand.Category.STRAIGHT, hand("10s 9h 8d 7c 6s 2h 2d").category());
        assertEquals(PokerHand.Category.THREE_OF_A_KIND, hand("5s 5h 5d Kc 9s 2h 3d").category());
        assertEquals(PokerHand.Category.TWO_PAIR, hand("Js Jh 4d 4c As 8h 2d").category());
        assertEquals(PokerHand.Category.PAIR, hand("10s 10h Kd 8c 4s 3h 2d").category());
        assertEquals(PokerHand.Category.HIGH_CARD, hand("As Jh 9d 7c 4s 3h 2d").category());
    }

    @Test
    void categoriesAreOrdered() {
        String[] ladder = {
                "As Jh 9d 7c 4s 3h 2d", "10s 10h Kd 8c 4s 3h 2d", "Js Jh 4d 4c As 8h 2d",
                "5s 5h 5d Kc 9s 2h 3d", "10s 9h 8d 7c 6s 2h 2d", "Ac 9c 7c 4c 2c Kd Qd",
                "Qs Qh Qd 4c 4d 9s 2h", "7s 7h 7d 7c Kd 2s 3s", "9h 8h 7h 6h 5h Ad Ac",
                "As Ks Qs Js 10s 2d 3c"};
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(hand(ladder[i]).score() > hand(ladder[i - 1]).score(), ladder[i]);
        }
    }

    @Test
    void theWheelIsTheLowestStraight() {
        PokerHand.Result wheel = hand("As 2h 3d 4c 5s Kh Kd");
        assertEquals(PokerHand.Category.STRAIGHT, wheel.category());
        assertTrue(hand("2s 3h 4d 5c 6s Kh Qd").score() > wheel.score());
        assertEquals(PokerHand.Category.STRAIGHT_FLUSH, hand("Ah 2h 3h 4h 5h 9c 9d").category());
    }

    @Test
    void kickersBreakTies() {
        assertTrue(hand("As Ah Kd 8c 4s 3h 2d").score() > hand("Ac Ad Qd 8c 4s 3h 2d").score());
        assertTrue(hand("Ks Kh 9d 9c 5s 3h 2d").score() > hand("Ks Kh 9d 9c 4s 3h 2d").score());
        assertTrue(hand("8s 8h 8d 2c 2s 3h 4d").score() > hand("7s 7h 7d Ac As 3h 4d").score());
        assertTrue(hand("Ac 9c 7c 4c 3c").score() > hand("Ac 9c 7c 4c 2c").score());
    }

    @Test
    void theBoardCanPlay() {
        // Both players play the straight on the board: a split.
        List<Card> board = cards("10s 9h 8d 7c 6s");
        assertEquals(PokerHand.best(cards("2c 2d"), board).score(), PokerHand.best(cards("3c 3d"), board).score());
        // A higher card completing a longer straight wins.
        assertTrue(PokerHand.best(cards("Jc 2d"), board).score() > PokerHand.best(cards("3c 3d"), board).score());
    }

    @Test
    void theBestFiveAreReturned() {
        PokerHand.Result result = hand("Qs Qh Qd 4c 4d 9s 2h");
        assertEquals(5, result.best().size());
        assertTrue(result.best().containsAll(cards("Qs Qh Qd 4c 4d")));
    }
}
