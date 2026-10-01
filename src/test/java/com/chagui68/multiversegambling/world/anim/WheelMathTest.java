package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WheelMathTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void sectorsAreSpreadRoundTheWholeWheel() {
        assertEquals(0.0, WheelMath.angleOf(0, 37), EPSILON);
        assertEquals(WheelMath.TAU / 37, WheelMath.angleOf(1, 37), EPSILON);
        // The last sector stops one step short of a full turn.
        assertEquals(WheelMath.TAU * 36 / 37, WheelMath.angleOf(36, 37), EPSILON);
        assertEquals(0.0, WheelMath.angleOf(37, 37), EPSILON);
    }

    @Test
    void sectorsWrapRoundInsteadOfGoingOutOfRange() {
        assertEquals(WheelMath.angleOf(2, 5), WheelMath.angleOf(7, 5), EPSILON);
        assertEquals(WheelMath.angleOf(4, 5), WheelMath.angleOf(-1, 5), EPSILON);
        assertEquals(0.0, WheelMath.angleOf(3, 0), EPSILON);
    }

    @Test
    void anglesAreFoldedIntoASingleTurn() {
        assertEquals(0.0, WheelMath.normalize(0.0), EPSILON);
        assertEquals(1.0, WheelMath.normalize(1.0), EPSILON);
        assertEquals(0.5, WheelMath.normalize(WheelMath.TAU + 0.5), EPSILON);
        assertEquals(WheelMath.TAU - 0.5, WheelMath.normalize(-0.5), EPSILON);
    }

    @Test
    void aSpinAlwaysLandsOnTheWinnerGoingForwards() {
        int sectors = 37;
        double start = WheelMath.angleOf(0, sectors) + WheelMath.TAU * 0.42;
        for (int winner = 0; winner < sectors; winner++) {
            double target = WheelMath.angleOf(winner, sectors);
            double arc = WheelMath.sweep(start, target, 6);
            assertTrue(arc >= WheelMath.TAU * 6, "the wheel never spins backwards");
            assertEquals(WheelMath.normalize(target), WheelMath.normalize(start + arc), 1.0e-9,
                    "a spin from " + start + " must stop on " + target);
        }
    }

    @Test
    void theArcGrowsWithTheTurns() {
        assertEquals(WheelMath.TAU * 3 + 0.25, WheelMath.sweep(1.0, 1.25, 3), EPSILON);
        assertEquals(0.25, WheelMath.sweep(1.0, 1.25, 0), EPSILON);
    }

    @Test
    void theBallStartsFastAndArrivesAtTheWinner() {
        assertEquals(0.0, WheelMath.ease(0.0), EPSILON);
        assertEquals(1.0, WheelMath.ease(1.0), EPSILON);
        // Ease out: most of the ground is covered in the first half of the spin.
        assertTrue(WheelMath.ease(0.5) > 0.5, "ease(0.5) = " + WheelMath.ease(0.5));
        // And it never goes back.
        double previous = -1;
        for (int step = 0; step <= 100; step++) {
            double value = WheelMath.ease(step / 100.0);
            assertTrue(value >= previous, "ease must grow with the progress");
            previous = value;
        }
        // Out of range values are clamped, so a long frame cannot overshoot.
        assertEquals(1.0, WheelMath.ease(2.0), EPSILON);
        assertEquals(0.0, WheelMath.ease(-1.0), EPSILON);
    }

    @Test
    void aPointStartsOnThePositivZAxisAndTurnsClockwise() {
        assertEquals(0.0, WheelMath.x(0.0, 8.0), EPSILON);
        assertEquals(8.0, WheelMath.z(0.0, 8.0), EPSILON);
        // A quarter turn later the point sits on the positive X axis.
        assertEquals(8.0, WheelMath.x(WheelMath.TAU / 4, 8.0), 1.0e-9);
        assertEquals(0.0, WheelMath.z(WheelMath.TAU / 4, 8.0), 1.0e-9);
        assertEquals(8.0, Math.hypot(WheelMath.x(1.1, 8.0), WheelMath.z(1.1, 8.0)), 1.0e-9);
    }

    @Test
    void equalSectorsGetTheSameNumberOfTiles() {
        int[] tiles = WheelMath.allocate(null, 8, 36);
        assertEquals(8, tiles.length);
        for (int count : tiles) {
            assertEquals(5, count, "eight sectors need five tiles each to reach 36");
        }
        int[] roulette = WheelMath.allocate(null, 37, 36);
        for (int count : roulette) {
            assertEquals(1, count, "a roulette already has enough pockets");
        }
    }

    @Test
    void weightedSectorsShareTheWheelByWeight() {
        double[] stakes = {100, 300, 0.5};
        int[] tiles = WheelMath.allocate(stakes, stakes.length, 48);
        int total = 0;
        for (int count : tiles) {
            assertTrue(count >= 1, "every stake must be visible");
            total += count;
        }
        assertEquals(48, total, "the wheel keeps its number of tiles");
        assertTrue(tiles[1] > tiles[0] && tiles[0] > tiles[2], "bigger stakes get bigger slices");
        assertEquals(0, WheelMath.allocate(new double[0], 0, 36).length);
    }

    @Test
    void theQuadraticEaseStartsGentlerThanTheCubic() {
        assertEquals(0.0, WheelMath.easeQuad(0), EPSILON);
        assertEquals(1.0, WheelMath.easeQuad(1), EPSILON);
        assertTrue(WheelMath.easeQuad(0.05) < WheelMath.ease(0.05));
    }
}
