package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.world.board.BoardGrid;

import org.bukkit.Material;

/**
 * A game whose round is played by clicking blocks: the world builder lays its board
 * out on the arena and, during a round, the arena is the input surface instead of the
 * menu. Blackjack style pick-a-card rounds stay in their menus; this is for games
 * whose round really is a board of tiles.
 *
 * <p>The menu is never removed: it is what a player gets when the casino world is
 * disabled or when the board is busy with somebody else's round.</p>
 */
public interface BoardGame {

    /**
     * Geometry of the board, in blocks relative to the middle of the arena.
     */
    BoardGrid boardGrid();

    /**
     * How many cells of the grid the round uses. Any left over is built as padding,
     * so a grid is never left with a hole when the configuration does not fill it.
     */
    int boardCells();

    /**
     * Material of a tile waiting to be picked: the resting look of the board.
     */
    Material boardTile();

    /**
     * Material of the padding cells of a grid larger than the round needs.
     */
    default Material boardFiller() {
        return Material.POLISHED_BLACKSTONE;
    }
}
