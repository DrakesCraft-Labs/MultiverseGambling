package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.SlotsTable.Symbol;
import java.util.List;

class SlotsTableTest {

    private final SlotsTable slots = SlotsTable.defaults();

    private Symbol symbol(String id) {
        return slots.symbols().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void threeOfAKindPayTheBiggestPrize() {
        Symbol crown = symbol("crown");
        assertEquals(crown.triple(), slots.payout(List.of(crown, crown, crown)), 1e-9);
    }

    @Test
    void twoCherriesPayTheSmallPrizeButNotWithOtherCoins() {
        Symbol cherry = symbol("cherry");
        Symbol lemon = symbol("lemon");
        assertEquals(cherry.pair(), slots.payout(List.of(cherry, cherry, lemon)), 1e-9);
        assertEquals(cherry.pair(), slots.payout(List.of(lemon, cherry, cherry)), 1e-9);
        assertEquals(0.0, slots.payout(List.of(lemon, lemon, cherry)), 1e-9);
    }

    @Test
    void withoutCombinationsNothingIsPaid() {
        Symbol cherry = symbol("cherry");
        Symbol lemon = symbol("lemon");
        Symbol bell = symbol("bell");
        assertEquals(0.0, slots.payout(List.of(cherry, lemon, bell)), 1e-9);
    }

    @Test
    void theTheoreticalReturnIsInTheRangeOfARealSlotMachine() {
        double rtp = slots.rtp();
        assertTrue(rtp > 0.90, "the RTP should not be ruinous, it was " + rtp);
        assertTrue(rtp < 0.99, "the RTP cannot approach 1 or the house never wins, it was " + rtp);
        assertEquals(0.9475, rtp, 0.005, "the default table is calibrated at 94.75%");
    }

    @Test
    void thePrizesAreOrderedByRarity() {
        // From most common to rarest: the prize must keep rising without exception.
        List<Symbol> sorted = slots.symbols().stream()
                .sorted((a, b) -> Double.compare(b.weight(), a.weight()))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i).triple() > sorted.get(i - 1).triple(),
                    sorted.get(i).id() + " (weight " + sorted.get(i).weight() + ") pays "
                            + sorted.get(i).triple() + " and it is rarer than " + sorted.get(i - 1).id()
                            + " (weight " + sorted.get(i - 1).weight() + ", pays "
                            + sorted.get(i - 1).triple() + ")");
        }
    }

    @Test
    void theJackpotIsReallyRare() {
        double chance = slots.chanceOf("crown");
        assertTrue(chance < 0.03, "the jackpot should come up less than 3% of the time");
        assertFalse(Double.isNaN(chance));
    }

    @Test
    void theSpinAlwaysReturnsThreeValidSymbols() {
        for (int i = 0; i < 2_000; i++) {
            List<Symbol> reels = slots.spin();
            assertEquals(3, reels.size());
            for (Symbol reel : reels) {
                assertTrue(slots.symbols().contains(reel));
            }
        }
    }
}
