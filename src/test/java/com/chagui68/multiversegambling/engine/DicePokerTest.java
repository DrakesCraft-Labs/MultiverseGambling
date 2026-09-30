package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

class DicePokerTest {

    @Test
    void detectsEveryCategory() {
        assertEquals(DicePoker.Hand.FIVE_OF_A_KIND, DicePoker.handOf(new int[]{4, 4, 4, 4, 4}));
        assertEquals(DicePoker.Hand.FOUR_OF_A_KIND, DicePoker.handOf(new int[]{4, 4, 4, 4, 2}));
        assertEquals(DicePoker.Hand.FULL_HOUSE, DicePoker.handOf(new int[]{4, 4, 4, 2, 2}));
        assertEquals(DicePoker.Hand.STRAIGHT, DicePoker.handOf(new int[]{2, 3, 4, 5, 6}));
        assertEquals(DicePoker.Hand.STRAIGHT, DicePoker.handOf(new int[]{1, 2, 3, 4, 5}));
        assertEquals(DicePoker.Hand.THREE_OF_A_KIND, DicePoker.handOf(new int[]{4, 4, 4, 2, 6}));
        assertEquals(DicePoker.Hand.TWO_PAIR, DicePoker.handOf(new int[]{4, 4, 2, 2, 6}));
        assertEquals(DicePoker.Hand.PAIR, DicePoker.handOf(new int[]{4, 4, 2, 5, 6}));
        assertEquals(DicePoker.Hand.HIGH_CARD, DicePoker.handOf(new int[]{1, 3, 4, 5, 6}));
    }

    @Test
    void straightsWithRepeatsDoNotCount() {
        // 1,2,3,4,6 is not a straight; neither is 1,2,3,4,4.
        assertEquals(DicePoker.Hand.HIGH_CARD, DicePoker.handOf(new int[]{1, 2, 3, 4, 6}));
        assertEquals(DicePoker.Hand.PAIR, DicePoker.handOf(new int[]{1, 2, 3, 4, 4}));
    }

    @Test
    void betterHandsScoreHigher() {
        int fiveOfAKind = DicePoker.score(new int[]{6, 6, 6, 6, 6});
        int fourOfAKind = DicePoker.score(new int[]{6, 6, 6, 6, 5});
        int fullHouse = DicePoker.score(new int[]{6, 6, 6, 5, 5});
        int straight = DicePoker.score(new int[]{2, 3, 4, 5, 6});
        int threeOfAKind = DicePoker.score(new int[]{6, 6, 6, 2, 3});
        int twoPair = DicePoker.score(new int[]{6, 6, 5, 5, 2});
        int pair = DicePoker.score(new int[]{6, 6, 4, 3, 2});
        // Careful: 2-3-4-5-6 is a straight to the six, not a high card.
        int highCard = DicePoker.score(new int[]{6, 5, 4, 3, 1});

        assertTrue(fiveOfAKind > fourOfAKind);
        assertTrue(fourOfAKind > fullHouse);
        assertTrue(fullHouse > straight);
        assertTrue(straight > threeOfAKind);
        assertTrue(threeOfAKind > twoPair);
        assertTrue(twoPair > pair);
        assertTrue(pair > highCard);
    }

    @Test
    void tiesAreBrokenByDiceValues() {
        int highPair = DicePoker.score(new int[]{6, 6, 4, 3, 2});
        int lowPair = DicePoker.score(new int[]{5, 5, 4, 3, 2});
        assertTrue(highPair > lowPair, "the pair of sixes must beat the pair of fives");

        int sixesTriple = DicePoker.score(new int[]{6, 6, 6, 2, 3});
        int fivesTriple = DicePoker.score(new int[]{5, 5, 5, 6, 2});
        assertTrue(sixesTriple > fivesTriple, "the triple decides before the kickers");
        // Watch out: 5-5-5-6-6 is already a full house, not a triple.
        assertEquals(DicePoker.Hand.FULL_HOUSE, DicePoker.handOf(new int[]{5, 5, 5, 6, 6}));
    }

    @Test
    void identicalRollsTieExactly() {
        assertEquals(DicePoker.score(new int[]{3, 3, 5, 5, 1}), DicePoker.score(new int[]{1, 5, 3, 5, 3}));
    }

    @Test
    void theDiceAreAlwaysInRange() {
        for (int i = 0; i < 20_000; i++) {
            int[] dice = DicePoker.roll();
            assertEquals(5, dice.length);
            for (int die : dice) {
                assertTrue(die >= 1 && die <= 6, "invalid die in " + Arrays.toString(dice));
            }
            DicePoker.handOf(dice);
            DicePoker.score(dice);
        }
    }

    @Test
    void aDieOutOfRangeIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DicePoker.handOf(new int[]{1, 2, 3, 4, 7}));
    }
}
