package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A player alone in a room can take on the house instead of waiting. The payout of that
 * duel has to be a real bet and not a gift, so the odds it pays on are pinned here.
 */
class HouseDuelTableTest {

    @Test
    void thePlayerWinsExactlyWhenTheBulletSitsOnADealerTurn() {
        // Turns go player, dealer, player... with the cylinder spun once.
        assertEquals(0.5, HouseDuelTable.playerWinChance(6, 1), 1e-9);
        assertEquals(0.5, HouseDuelTable.playerWinChance(2, 1), 1e-9);
        assertEquals(0.4, HouseDuelTable.playerWinChance(5, 1), 1e-9);
        assertEquals(1.0 / 3, HouseDuelTable.playerWinChance(3, 2), 1e-9);
        assertEquals(1.0 / 6, HouseDuelTable.playerWinChance(6, 5), 1e-9);
    }

    @Test
    void theDefaultRevolverPaysJustUnderTwoForOne() {
        assertEquals(1.96, HouseDuelTable.multiplier(6, 1, 0.02), 1e-9,
                "a 50/50 with a 2% edge pays 1.96x");
        assertEquals(2.0, HouseDuelTable.multiplier(6, 1, 0.0), 1e-9,
                "without an edge the duel pays exactly the chances");
    }

    @Test
    void theDuelNeverPaysMoreThanTheHouseTakes() {
        for (int chambers = 2; chambers <= 12; chambers++) {
            for (int bullets = 1; bullets < chambers; bullets++) {
                for (double edge : new double[]{0.0, 0.02, 0.05, 0.1, 0.5}) {
                    double rtp = HouseDuelTable.rtp(chambers, bullets, edge);
                    assertTrue(rtp <= 1.0 - edge + 1e-9,
                            chambers + " chambers, " + bullets + " bullets, edge " + edge
                                    + " returns " + rtp);
                }
            }
        }
    }

    @Test
    void everyConfigurationIsWinnableAndPaysAtLeastTheStake() {
        for (int chambers = 2; chambers <= 12; chambers++) {
            for (int bullets = 1; bullets < chambers; bullets++) {
                double chance = HouseDuelTable.playerWinChance(chambers, bullets);
                assertTrue(chance > 0 && chance <= 1.0,
                        chambers + "/" + bullets + " gives a chance of " + chance);
                double multiplier = HouseDuelTable.multiplier(chambers, bullets, 0.5);
                assertTrue(multiplier >= 1.0, "a win must never pay less than the stake");
                assertTrue(HouseDuelTable.multiplier(chambers, bullets, 0.0)
                                <= HouseDuelTable.MAX_MULTIPLIER,
                        "the multiplier of " + chambers + "/" + bullets + " is not capped");
            }
        }
    }

    @Test
    void anOddCylinderFavoursTheDealerByOneTurn() {
        // With an odd number of chambers the player fires one more time than the dealer,
        // so their chance of meeting the bullet is the higher one. The payout grows with
        // the risk, which is what keeps the return the same.
        assertTrue(HouseDuelTable.playerWinChance(5, 1) < HouseDuelTable.playerWinChance(6, 1),
                "an odd cylinder is worse for whoever shoots first");
        assertTrue(HouseDuelTable.multiplier(5, 1, 0.02) > HouseDuelTable.multiplier(6, 1, 0.02),
                "a smaller chance has to pay more");
    }
}
