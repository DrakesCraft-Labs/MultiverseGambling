package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chagui68.multiversegambling.world.CasinoLayout;

import org.junit.jupiter.api.Test;

/**
 * The plinko wall has to stand up and fit an arena: the apex on top, the pyramid getting
 * wider as it comes down, the buckets on the shelf at the bottom and the ball stopping on
 * the bucket the game pays for.
 */
class PlinkoBoardTest {

    @Test
    void theWallStandsUpWithItsApexOnTop() {
        PlinkoBoard board = new PlinkoBoard(12);
        for (int level = 0; level < board.levels() - 1; level++) {
            assertTrue(board.levelY(level) > board.levelY(level + 1),
                    "level " + level + " is not above the next one");
        }
        assertTrue(board.bucketY() < board.levelY(board.levels() - 1),
                "the buckets have to hang below the last row of pegs");
        assertTrue(board.height() > board.levelY(0), "the wall needs room over its apex");
    }

    @Test
    void thePyramidGetsWiderOnTheWayDown() {
        PlinkoBoard board = new PlinkoBoard(12);
        assertEquals(1, board.pegs(0));
        assertEquals(12, board.pegs(11));
        for (int level = 0; level < board.levels(); level++) {
            assertEquals(-board.pegX(level, level), board.pegX(level, 0), 1e-9,
                    "the pyramid is not centred on the arena");
            if (level > 0) {
                assertTrue(board.pegX(level, level) - board.pegX(level, 0)
                                > board.pegX(level - 1, level - 1) - board.pegX(level - 1, 0),
                        "level " + level + " is not wider than the one above it");
            }
        }
    }

    @Test
    void everyBoardFitsAnArenaWhateverTheConfiguration() {
        for (int rows : new int[]{6, 12, 16, 20}) {
            PlinkoBoard board = new PlinkoBoard(rows);
            assertTrue(board.halfWidth() <= CasinoLayout.ARENA_RADIUS,
                    rows + " rows are " + board.halfWidth() + " blocks wide");
            assertTrue(board.height() <= PlinkoBoard.MAX_HEIGHT + PlinkoBoard.SHELF_HEIGHT,
                    rows + " rows stand " + board.height() + " blocks tall");
        }
    }

    @Test
    void theBallEndsOnTheBucketThatPays() {
        double[] rolls = {0.9, 0.1, 0.4, 0.8, 0.2, 0.7};
        PlinkoBoard board = new PlinkoBoard(rolls.length);
        int[] path = PlinkoBoard.path(rolls);
        int bucket = PlinkoBoard.bucketOf(rolls);
        assertEquals(3, bucket, "three of those rolls bounce to the right");
        assertEquals(path[path.length - 1], 2 * bucket - board.rows(),
                "the path does not end in the bucket the game pays for");
        assertEquals(board.bucketX(bucket), board.ballX(path, board.rows()), 1e-9,
                "the ball does not stop on the middle of its bucket");
        assertEquals(board.bucketY(), board.ballY(board.rows()), 1e-9,
                "the ball does not rest on the shelf");
    }

    @Test
    void theBallFallsOneColumnAtATime() {
        double[] rolls = {0.1, 0.1, 0.9, 0.1};
        int[] path = PlinkoBoard.path(rolls);
        for (int level = 1; level < path.length; level++) {
            assertEquals(1, Math.abs(path[level] - path[level - 1]),
                    "the ball jumped more than one column at level " + level);
        }
        PlinkoBoard board = new PlinkoBoard(rolls.length);
        assertTrue(board.ballY(0) > board.ballY(1), "the ball is not falling");
        assertTrue(board.ballY(1.5) < board.ballY(1) && board.ballY(1.5) > board.ballY(2),
                "the ball does not pass between two rows");
    }

    @Test
    void aBoardNeedsAtLeastOneRow() {
        assertThrows(IllegalArgumentException.class, () -> new PlinkoBoard(0));
    }
}
