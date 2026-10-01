package com.chagui68.multiversegambling.listener;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.game.BoardGame;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.world.CasinoLayout;
import com.chagui68.multiversegambling.world.board.ArenaBoard;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Playing with the blocks of an arena.
 *
 * <p>Every registered game has a platform in the casino world. For the four games
 * whose round is a sequence of picks, that platform is also the board: clicking any of
 * its blocks opens the game, and clicking a tile of a running round is the pick
 * itself. The click is always taken, so nobody can place a block on a board by
 * accident while playing on it.</p>
 */
public final class BoardListener implements Listener {

    private final MultiverseGamblingPlugin plugin;

    public BoardListener(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || plugin.world() == null || !plugin.world().ready()
                || block.getWorld() != plugin.world().world()) {
            return;
        }
        CasinoLayout.Arena arena = arenaAt(block);
        if (arena == null) {
            return;
        }
        Game game = plugin.games().byId(arena.gameId()).orElse(null);
        if (!(game instanceof BoardGame)) {
            return;
        }
        ArenaBoard board = ArenaBoard.of(game.id());
        // Only the stage in the middle of the pavilion is the board: the bleachers, the
        // walls and the gates stay ordinary blocks.
        int dx = block.getX() - arena.centerX();
        int dz = block.getZ() - arena.centerZ();
        int reach = CasinoLayout.STAGE_RADIUS + 1;
        boolean onStage = dx * dx + dz * dz <= reach * reach;
        if (!onStage && (board == null || board.cellAt(block) < 0)) {
            return;
        }
        // The click opens or drives the game; it never places a block.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (board == null) {
            game.open(player);
            return;
        }
        if (board.accepts(player)) {
            board.click(player, block, board.cellAt(block));
        } else {
            board.hint(player);
        }
    }

    private CasinoLayout.Arena arenaAt(Block block) {
        for (CasinoLayout.Arena arena : plugin.world().layout().arenas()) {
            if (arena.contains(block.getX(), block.getZ())) {
                return arena;
            }
        }
        return null;
    }
}
