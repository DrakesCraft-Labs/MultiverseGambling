package com.chagui68.multiversegambling.world.board;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.world.CasinoLayout;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.BlockPaint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * A round being played on the blocks of one arena.
 *
 * <p>The world builder leaves a board of resting tiles standing in the arena; during a
 * round the game paints its state over it and the clicks on those blocks are the
 * input. Only one board per game can be live at a time, so two simultaneous rounds of
 * the same solo game never fight over the same arena: the second one falls back to its
 * menu instead.</p>
 *
 * <p>Everything painted is put back exactly as it was when the board closes, and a
 * finished board is left standing for a moment so the result can be seen.</p>
 */
public abstract class ArenaBoard {

    /**
     * Ticks a finished board stays standing after the result.
     */
    private static final int LINGER_TICKS = 25;

    /**
     * Blocks a player can walk away from the board before the round is abandoned.
     */
    private static final double ABANDON_DISTANCE = 48.0;

    /**
     * Live board of each game, keyed by game id.
     */
    private static final Map<String, ArenaBoard> ACTIVE = new HashMap<>();

    private final MultiverseGamblingPlugin plugin;
    private final String gameId;
    private final ArenaStage stage;
    private final BoardGrid grid;
    private final BlockPaint paint;
    private boolean open;
    private boolean lingering;
    private boolean closed;

    protected ArenaBoard(MultiverseGamblingPlugin plugin, String gameId, ArenaStage stage, BoardGrid grid) {
        this.plugin = plugin;
        this.gameId = gameId;
        this.stage = stage;
        this.grid = grid;
        this.paint = new BlockPaint(stage.world());
    }

    /**
     * Takes the arena for this game.
     *
     * <p>The only board that can be taken over is one left standing after a result;
     * a live round of the same game keeps the arena, and the newcomer falls back to its
     * menu.</p>
     *
     * @return false when a live round of the same game is already on it, in which case
     * nothing was painted
     */
    public final boolean open() {
        if (open || !grid.fitsIn(CasinoLayout.ARENA_RADIUS)) {
            return false;
        }
        ArenaBoard standing = ACTIVE.get(gameId);
        if (standing != null) {
            // A board left standing after a result can be taken over; a live round cannot.
            if (!standing.lingering) {
                return false;
            }
            standing.close();
        }
        ACTIVE.put(gameId, this);
        open = true;
        try {
            stage.load();
            onOpen();
        } catch (RuntimeException error) {
            close();
            throw error;
        }
        return true;
    }

    /**
     * Clears the board and puts every block back.
     */
    public final void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            onClose();
        } finally {
            paint.restore();
            if (ACTIVE.get(gameId) == this) {
                ACTIVE.remove(gameId);
            }
        }
    }

    /**
     * Leaves the board standing after the result, then clears it. Used when a round
     * finishes: the last frame is worth looking at.
     */
    public final void linger() {
        if (closed || lingering) {
            return;
        }
        lingering = true;
        if (!plugin.isEnabled()) {
            close();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::close, LINGER_TICKS);
    }

    /**
     * Takes down every board still standing. Called when the plug-in stops, because
     * the delayed cleanup of a lingering board never gets to run then.
     */
    public static void clearAll() {
        for (ArenaBoard board : new ArrayList<>(ACTIVE.values())) {
            board.close();
        }
        ACTIVE.clear();
    }

    /**
     * Live board of a game, or {@code null} when nobody is playing on its arena.
     */
    public static ArenaBoard of(String gameId) {
        return ACTIVE.get(gameId);
    }

    public final String gameId() {
        return gameId;
    }

    public final ArenaStage stage() {
        return stage;
    }

    public final BoardGrid grid() {
        return grid;
    }

    /**
     * Cell of the board a block belongs to, or {@code -1} when it is not one of its
     * tiles.
     */
    public final int cellAt(Block block) {
        if (block.getWorld() != stage.world() || block.getY() != stage.floorY() + 1) {
            return -1;
        }
        return grid.indexAt(block.getX() - stage.arena().centerX(),
                block.getZ() - stage.arena().centerZ());
    }

    /**
     * True when the block sits exactly on that offset of the board.
     */
    protected final boolean isAt(Block block, int dx, int dy, int dz) {
        return block.getWorld() == stage.world()
                && block.getY() == stage.floorY() + 1 + dy
                && block.getX() == stage.arena().centerX() + dx
                && block.getZ() == stage.arena().centerZ() + dz;
    }

    /**
     * True when the player has walked far enough from the arena for the round to count
     * as abandoned, which is the board's way of closing the menu.
     */
    public final boolean abandoned(Player player) {
        Location where = player.getLocation();
        if (where.getWorld() != stage.world()) {
            return true;
        }
        Location middle = stage.center();
        double dx = where.getX() - middle.getX();
        double dz = where.getZ() - middle.getZ();
        return dx * dx + dz * dz > ABANDON_DISTANCE * ABANDON_DISTANCE;
    }

    /**
     * Places the player on top of the board, in the middle, so every tile is within
     * reach. Only moves anybody when {@code world.animations.teleport-players} is on.
     */
    public final void bring(Player player) {
        if (player == null || !plugin.config().worldAnimationsTeleport()) {
            return;
        }
        stage.load();
        player.setFallDistance(0.0f);
        player.teleport(playSpot());
    }

    /**
     * Standing spot in the middle of the board, looking at it.
     */
    public final Location playSpot() {
        Location spot = stage.at(0, 1, 0);
        spot.setYaw(180.0f);
        spot.setPitch(30.0f);
        return spot;
    }

    /**
     * Paints one cell of the grid, relative to the middle of the arena.
     */
    protected final void paintCell(int cell, Material material) {
        if (cell < 0 || cell >= grid.cellCount()) {
            return;
        }
        paintAt(grid.dx(cell), 0, grid.dz(cell), material);
    }

    /**
     * Paints one block relative to the middle of the arena: {@code dy = 0} is the layer
     * of the board itself.
     */
    protected final void paintAt(int dx, int dy, int dz, Material material) {
        paint.set(stage.arena().centerX() + dx, stage.floorY() + 1 + dy,
                stage.arena().centerZ() + dz, material);
    }

    /**
     * True when that player is the one who can click the board right now.
     */
    public abstract boolean accepts(Player player);

    /**
     * Handles a click on one of the arena blocks.
     *
     * @param cell cell of the grid, or {@code -1} when the block is not a tile
     */
    public abstract void click(Player player, Block block, int cell);

    /**
     * Answers a click from somebody who cannot play right now.
     */
    public void hint(Player player) {
    }

    /**
     * First paint of the round.
     */
    protected void onOpen() {
    }

    /**
     * Board about to be cleared: reset whatever state the board owned.
     */
    protected void onClose() {
    }
}
