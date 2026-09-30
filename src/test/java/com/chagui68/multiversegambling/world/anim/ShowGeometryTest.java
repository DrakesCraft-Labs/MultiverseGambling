package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ShowGeometryTest {

    // ----------------------------------------------------------------- plinko

    @Test
    void thePlinkoBallEndsInTheBucketThatPays() {
        // A roll under 0.5 bounces right, exactly like the game counts the bucket.
        for (int rows = 4; rows <= 20; rows++) {
            for (int mask = 0; mask < 64; mask++) {
                double[] rolls = new double[rows];
                int bucket = 0;
                for (int i = 0; i < rows; i++) {
                    boolean right = ((mask >> (i % 6)) & 1) == 0;
                    rolls[i] = right ? 0.25 : 0.75;
                    if (right) {
                        bucket++;
                    }
                }
                int[] path = PlinkoShow.path(rolls);
                assertEquals(rows + 1, path.length);
                assertEquals(0, path[0], "the ball always starts over the middle");
                assertEquals(2 * bucket - rows, path[rows],
                        "the ball must stop in the bucket that pays");
            }
        }
    }

    @Test
    void everyPlinkoBounceMovesTheBallExactlyOnePlace() {
        double[] rolls = {0.1, 0.9, 0.2, 0.8, 0.3};
        int[] path = PlinkoShow.path(rolls);
        for (int level = 1; level < path.length; level++) {
            assertEquals(1, Math.abs(path[level] - path[level - 1]),
                    "bounce " + level + " left the ball somewhere it cannot be");
        }
    }

    @Test
    void aPlinkoBoardNeverGrowsWiderThanItsArena() {
        // The bucket row spans 2 * rows + 1 columns, and the arena is 25 wide.
        assertEquals(25, 2 * 12 + 1);
        for (double roll : new double[]{0.0, 0.49, 0.51, 0.99}) {
            double[] rolls = new double[12];
            java.util.Arrays.fill(rolls, roll);
            int[] path = PlinkoShow.path(rolls);
            for (int offset : path) {
                assertTrue(Math.abs(offset) <= 12, "the ball left the arena at " + offset);
            }
        }
    }

    // ------------------------------------------------------------------ crash

    @Test
    void theCrashTowerClimbsOneBlockPerDoubling() {
        assertEquals(1, CrashTowerShow.heightFor(1.0, 1000));
        assertEquals(2, CrashTowerShow.heightFor(2.0, 1000));
        assertEquals(3, CrashTowerShow.heightFor(4.0, 1000));
        assertEquals(4, CrashTowerShow.heightFor(8.0, 1000));
        assertEquals(10, CrashTowerShow.heightFor(512.0, 1000));
    }

    @Test
    void theCrashTowerNeverFallsAndNeverGrowsPastItsCap() {
        double previous = 0;
        for (double multiplier = 1.0; multiplier <= 2000; multiplier *= 1.2) {
            int height = CrashTowerShow.heightFor(multiplier, 1000);
            assertTrue(height >= previous, "the tower must never come down");
            assertTrue(height >= 1 && height <= 11, "height " + height + " is out of range");
            previous = height;
        }
        assertEquals(1, CrashTowerShow.heightFor(0.0, 1000));
    }

    // ------------------------------------------------------------------- dice

    @Test
    void everyDieFaceHasItsOwnNumberOfPips() {
        for (int face = 1; face <= 6; face++) {
            int[][] pips = DiceShow.pips(face);
            assertEquals(face, pips.length, "face " + face + " needs " + face + " pips");
            for (int[] pip : pips) {
                assertTrue(pip[0] >= -1 && pip[0] <= 1, "pip outside the die");
                assertTrue(pip[1] >= -1 && pip[1] <= 1, "pip outside the die");
            }
        }
    }

    @Test
    void noTwoPipsOfADieSitOnTheSameSquare() {
        for (int face = 1; face <= 6; face++) {
            int[][] pips = DiceShow.pips(face);
            for (int i = 0; i < pips.length; i++) {
                for (int j = i + 1; j < pips.length; j++) {
                    assertTrue(pips[i][0] != pips[j][0] || pips[i][1] != pips[j][1],
                            "face " + face + " paints two pips on the same square");
                }
            }
        }
    }

    @Test
    void outOfRangeFacesFallBackToTheNearestRealOne() {
        assertEquals(1, DiceShow.pips(0).length);
        assertEquals(6, DiceShow.pips(9).length);
    }
}
