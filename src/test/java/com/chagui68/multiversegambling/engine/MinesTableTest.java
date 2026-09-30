package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinesTableTest {

    private static final double EDGE = 0.01;

    @Test
    void theChanceOfSurvivingFollowsTheHypergeometricDistribution() {
        // With 25 tiles and 3 mines the first pick is safe with probability 22/25.
        assertEquals(22.0 / 25.0, MinesTable.survivalChance(25, 3, 1), 1e-12);
        // The second one, given that you survived, is 21/24.
        assertEquals((22.0 / 25.0) * (21.0 / 24.0), MinesTable.survivalChance(25, 3, 2), 1e-12);
        // Clearing the whole board is as unlikely as hitting all 3 mines at once:
        // 1 / C(25,3) = 1/2300.
        assertEquals(1.0 / 2300.0, MinesTable.survivalChance(25, 3, 22), 1e-12);
    }

    @Test
    void withTwentyFourMinesOnlyOneGoodTileRemains() {
        assertEquals(1.0 / 25.0, MinesTable.survivalChance(25, 24, 1), 1e-12);
    }

    @Test
    void moreMinesMeanBetterPayForTheSameNumberOfHits() {
        double few = MinesTable.multiplier(25, 1, 5, EDGE);
        double average = MinesTable.multiplier(25, 5, 5, EDGE);
        double many = MinesTable.multiplier(25, 20, 5, EDGE);
        assertTrue(few < average, "1 mine should pay less than 5");
        assertTrue(average < many, "5 mines should pay less than 20");
    }

    @Test
    void theMultiplierIsAlwaysProfitableForTheHouse() {
        // For any configuration, multiplier * probability = 1 - edge.
        for (int mines = 1; mines <= 24; mines++) {
            int maxReveals = 25 - mines;
            for (int revealed = 1; revealed <= maxReveals; revealed++) {
                double chance = MinesTable.survivalChance(25, mines, revealed);
                double multiplier = MinesTable.multiplier(25, mines, revealed, EDGE);
                assertEquals(1.0 - EDGE, chance * multiplier, 1e-9,
                        "mines=" + mines + " destapadas=" + revealed);
            }
        }
    }

    @Test
    void revealingNothingWinsNothing() {
        assertEquals(1.0, MinesTable.multiplier(25, 3, 0, EDGE), 1e-12);
    }

    @Test
    void towersApplyTheMultiplierPerFloor() {
        // 4 tiles with 1 bomb: 3/4 per floor.
        assertEquals((1 - EDGE) / (0.75 * 0.75), MinesTable.towerMultiplier(2, 4, 1, EDGE), 1e-9);
        assertTrue(MinesTable.towerMultiplier(9, 4, 1, EDGE) > MinesTable.towerMultiplier(8, 4, 1, EDGE));
    }

    @Test
    void impossibleInputsReturnZero() {
        assertEquals(0.0, MinesTable.survivalChance(25, 25, 1));
        assertEquals(0.0, MinesTable.survivalChance(25, 3, 23));
        assertEquals(0.0, MinesTable.multiplier(25, 3, 23, EDGE));
    }
}
