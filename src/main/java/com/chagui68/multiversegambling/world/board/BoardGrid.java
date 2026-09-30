package com.chagui68.multiversegambling.world.board;

/**
 * Geometry of a rectangular board of blocks laid out on an arena floor.
 *
 * <p>Coordinates are relative to the middle of the arena: column {@code 0} sits at
 * {@link #originX()} along X and row {@code 0} at {@link #originZ()} along Z, so the
 * board never has to know where on the world grid its arena happens to be. The class
 * is deliberately free of Bukkit, which is what makes it unit tested.</p>
 */
public record BoardGrid(int columns, int rows, int originX, int originZ) {

    public BoardGrid {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("a board needs at least one cell, got "
                    + columns + "x" + rows);
        }
    }

    /**
     * A grid of that size centred on the arena, so an odd number of columns leaves a
     * cell right in the middle and an even one starts one block to the west.
     */
    public static BoardGrid centered(int columns, int rows) {
        return new BoardGrid(columns, rows, -((columns - 1) / 2), -((rows - 1) / 2));
    }

    /**
     * How many cells the board holds.
     */
    public int cellCount() {
        return columns * rows;
    }

    /**
     * Offset of a cell along X, in blocks from the middle of the arena.
     */
    public int dx(int cell) {
        return originX + cell % columns;
    }

    /**
     * Offset of a cell along Z, in blocks from the middle of the arena.
     */
    public int dz(int cell) {
        return originZ + cell / columns;
    }

    /**
     * True when the board, its one block frame included, fits inside an arena of that
     * radius without touching the platform border, the fence or the lamps.
     */
    public boolean fitsIn(int radius) {
        return originX - 1 >= -radius + 1 && originX + columns <= radius - 1
                && originZ - 1 >= -radius + 1 && originZ + rows <= radius - 1;
    }

    /**
     * Cell at those offsets, or {@code -1} when they fall outside the board.
     */
    public int indexAt(int dx, int dz) {
        int column = dx - originX;
        int row = dz - originZ;
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            return -1;
        }
        return row * columns + column;
    }
}
