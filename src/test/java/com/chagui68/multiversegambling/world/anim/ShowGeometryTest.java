package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.chagui68.multiversegambling.world.CasinoLayout;
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
                int[] path = PlinkoBoard.path(rolls);
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
        int[] path = PlinkoBoard.path(rolls);
        for (int level = 1; level < path.length; level++) {
            assertEquals(1, Math.abs(path[level] - path[level - 1]),
                    "bounce " + level + " left the ball somewhere it cannot be");
        }
    }

    @Test
    void everyPlinkoBucketSitsInsideItsArena() {
        // The wall stands up, so it has to fit across the arena for every row count the
        // configuration allows, buckets included.
        for (int rows : new int[]{6, 12, 16, 20}) {
            PlinkoBoard board = new PlinkoBoard(rows);
            for (int bucket = 0; bucket <= rows; bucket++) {
                assertTrue(Math.abs(board.bucketX(bucket)) + board.column() <= CasinoLayout.ARENA_RADIUS,
                        "bucket " + bucket + " of a " + rows + " row wall is outside the arena");
            }
        }
    }

    // ------------------------------------------------------------------ crash

    @Test
    void theRocketClimbsAndMovesRightWhileTheMultiplierGrows() {
        double previousX = Double.NEGATIVE_INFINITY;
        double previousY = Double.NEGATIVE_INFINITY;
        for (double multiplier = 1.0; multiplier <= 5000; multiplier *= 1.15) {
            double x = CrashCurve.x(multiplier);
            double y = CrashCurve.y(multiplier);
            assertTrue(x > previousX, "the rocket went back at " + multiplier);
            assertTrue(y >= previousY, "the rocket came down at " + multiplier);
            previousX = x;
            previousY = y;
        }
    }

    @Test
    void theRocketNeverLeavesItsChart() {
        for (double multiplier : new double[]{0.0, 1.0, 1.5, 2, 10, 1000, 1e9}) {
            double x = CrashCurve.x(multiplier);
            double y = CrashCurve.y(multiplier);
            assertTrue(x >= CrashCurve.LEFT && x <= CrashCurve.LEFT + CrashCurve.WIDTH, "x " + x);
            assertTrue(y >= CrashCurve.BOTTOM && y <= CrashCurve.BOTTOM + CrashCurve.HEIGHT, "y " + y);
            assertTrue(Math.abs(x) + 1 <= CasinoLayout.STAGE_RADIUS, "the chart is wider than the stage");
        }
        assertEquals(0.5, CrashCurve.progress(2.0), 1e-9, "half the chart at 2x");
        assertEquals(0.0, CrashCurve.progress(0.5), 1e-9, "below 1x counts as the start");
    }

    @Test
    void theRocketTurnsTowardsTheSkyAsTheCurveSteepens() {
        double start = CrashCurve.heading(1.0);
        double late = CrashCurve.heading(50.0);
        assertTrue(start > late, "the nose must lift as the curve gets steeper");
        assertTrue(start <= Math.PI / 2 && late > 0, "the rocket always flies up and to the right");
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
