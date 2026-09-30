package com.chagui68.multiversegambling.world.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The board is the input surface of a round, so a cell landing on the wrong block
 * would send a click to the wrong tile: the mapping is checked here instead of in game.
 */
class BoardGridTest {

    @Test
    void anOddGridSitsSymmetricallyOnTheArena() {
        BoardGrid grid = BoardGrid.centered(5, 5);
        assertEquals(-2, grid.originX());
        assertEquals(-2, grid.originZ());
        assertEquals(0, grid.dx(12));
        assertEquals(0, grid.dz(12), "the twelfth cell of a 5x5 board is its middle");
    }

    @Test
    void cellsAreFilledRowByRow() {
        BoardGrid grid = BoardGrid.centered(4, 3);
        assertEquals(-1, grid.originX());
        assertEquals(-1, grid.originZ());
        // First row: cells 0..3 running west to east.
        assertEquals(-1, grid.dx(0));
        assertEquals(-1, grid.dz(0));
        assertEquals(2, grid.dx(3));
        // Second row starts back west and one block south.
        assertEquals(-1, grid.dx(4));
        assertEquals(0, grid.dz(4));
        assertEquals(12, grid.cellCount());
    }

    @Test
    void everyCellCanBeFoundByItsCoordinates() {
        BoardGrid grid = BoardGrid.centered(9, 4);
        for (int cell = 0; cell < grid.cellCount(); cell++) {
            assertEquals(cell, grid.indexAt(grid.dx(cell), grid.dz(cell)),
                    "cell " + cell + " must resolve to itself");
        }
    }

    @Test
    void aBoardOnlyFitsWhenItsFrameStaysInsideTheArena() {
        assertTrue(BoardGrid.centered(5, 5).fitsIn(12));
        assertTrue(BoardGrid.centered(9, 6).fitsIn(12));
        assertFalse(BoardGrid.centered(9, 25).fitsIn(12));
        assertFalse(BoardGrid.centered(25, 25).fitsIn(12));
    }

    @Test
    void coordinatesOutsideTheBoardHaveNoCell() {
        BoardGrid grid = BoardGrid.centered(3, 3);
        assertEquals(-1, grid.indexAt(-2, 0));
        assertEquals(-1, grid.indexAt(0, 2));
        assertEquals(-1, grid.indexAt(1, -2));
        assertEquals(-1, grid.indexAt(100, 100));
    }
}
