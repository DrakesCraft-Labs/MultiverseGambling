package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Every difficulty of the towers has to carry the same house edge on every floor: a
 * harder tower only grows faster, it is never a better deal.
 */
class TowerDifficultyTest {

    @Test
    void everyFloorOfEveryDifficultyReturnsTheSameShare() {
        double edge = 0.05;
        for (TowerDifficulty difficulty : TowerDifficulty.values()) {
            for (int level = 1; level <= 12; level++) {
                double chance = Math.pow(difficulty.safeChance(), level);
                assertEquals(1.0 - edge, chance * difficulty.multiplier(level, edge), 1e-9,
                        difficulty + " floor " + level + " does not return 1 - edge");
            }
        }
    }

    @Test
    void harderTowersGrowFaster() {
        double edge = 0.05;
        assertTrue(TowerDifficulty.MEDIUM.multiplier(5, edge) > TowerDifficulty.EASY.multiplier(5, edge));
        assertTrue(TowerDifficulty.HARD.multiplier(5, edge) > TowerDifficulty.MEDIUM.multiplier(5, edge));
        assertTrue(TowerDifficulty.MASTER.multiplier(2, edge) > TowerDifficulty.EXPERT.multiplier(2, edge));
        for (TowerDifficulty difficulty : TowerDifficulty.values()) {
            assertTrue(difficulty.bombs() < difficulty.tiles(), difficulty + " has no safe door");
        }
    }

    @Test
    void difficultiesAreReadFromTheConfiguration() {
        assertEquals(TowerDifficulty.HARD, TowerDifficulty.of(" Hard ", TowerDifficulty.EASY));
        assertEquals(TowerDifficulty.EASY, TowerDifficulty.of("nonsense", TowerDifficulty.EASY));
        assertEquals(TowerDifficulty.MEDIUM, TowerDifficulty.of(null, TowerDifficulty.MEDIUM));
    }
}
