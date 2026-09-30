package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.util.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Bomb board.
 *
 * <p>One single board with hidden bombs and players revealing tiles in turns.
 * Whoever hits a bomb is out and their money stays in the pot. Bombs are placed with
 * the provably fair mix of the casino, so the position of each one is
 * reproducible a posteriori.</p>
 */
public final class BombBoardGame extends AbstractGroupGame {

    private final Set<Integer> bombs = new LinkedHashSet<>();
    private final Set<Integer> revealed = new LinkedHashSet<>();
    private final List<UUID> order = new ArrayList<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private int turnIndex;
    private int turnTicks;
    private BombBoardGui board;
    private boolean counting;

    public BombBoardGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("bomb-board", "Bomb Board", GameCategory.GROUP, Material.GUNPOWDER)
                .desc("&7A board with hidden bombs.",
                        "&7Players take turns revealing one tile.",
                        "&7The last one standing takes the pot.")
                .players(2, 12)
                .build());
    }

    private int size() {
        return Math.min(54, plugin.config().bombBoardSize());
    }

    private int bombCount() {
        return Math.max(1, Math.min(size() - 1, plugin.config().bombBoardBombs()));
    }

    private UUID current() {
        if (order.isEmpty()) {
            return null;
        }
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        return order.get(turnIndex % order.size());
    }

    @Override
    protected void onRoundStart() {
        bombs.clear();
        revealed.clear();
        order.clear();
        order.addAll(pot.participants());
        alive.clear();
        alive.addAll(order);
        turnIndex = 0;
        turnTicks = 0;
        counting = true;
        timer = 0;

        // Provably fair placement: the tiles are shuffled with a casino roll.
        double[] rolls = plugin.fair().rolls(FairnessService.HOUSE, size());
        List<Integer> cells = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            cells.add(i);
        }
        cells.sort((a, b) -> Double.compare(rolls[a], rolls[b]));
        for (int i = 0; i < bombCount(); i++) {
            bombs.add(cells.get(i));
        }

        broadcastRoundHeader();
        broadcastPlain("group.bomb-board.board",
                "pot", plugin.economy().format(pot.total()),
                "tiles", size(), "bombs", bombCount());
        broadcastPlain("group.bomb-board.playing", "players", order.size());
    }

    @Override
    protected void tickRound() {
        if (alive.size() <= 1) {
            settle();
            return;
        }
        if (counting) {
            timer++;
            if (timer % 20 == 0) {
                actionBarAllKey("group.bomb-board.ready", "seconds", 3 - timer / 20);
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.3f);
            }
            if (timer >= 60) {
                counting = false;
                openBoard();
            }
            return;
        }

        UUID currentId = current();
        if (currentId == null) {
            return;
        }
        turnTicks++;
        int limit = plugin.config().bombBoardTurnSeconds() * 20;
        Player player = online(currentId);
        if (player != null) {
            int left = Math.max(0, (limit - turnTicks) / 20);
            actionBarKey(player, "group.bomb-board.your-turn",
                    "seconds", left, "pot", plugin.economy().format(pot.total()));
        }
        if (turnTicks >= limit) {
            // No decision made: a random tile is revealed.
            List<Integer> free = freeCells();
            if (free.isEmpty()) {
                settle();
                return;
            }
            broadcastPlain("group.bomb-board.out-of-time", "player", playerName(currentId));
            reveal(currentId, Rng.pick(free));
        }
    }

    private List<Integer> freeCells() {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            if (!revealed.contains(i)) {
                free.add(i);
            }
        }
        return free;
    }

    private void openBoard() {
        UUID currentId = current();
        if (currentId == null) {
            return;
        }
        Player player = online(currentId);
        if (player == null) {
            return;
        }
        if (board != null) {
            board.close();
        }
        board = new BombBoardGui(plugin, player, this);
        board.show();
        player.sendMessage(plugin.messages().componentPlainFor(player,
                "group.bomb-board.turn-hint", "bombs", bombCount()));
        soundAll(Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
    }

    /**
     * A player reveals a tile.
     */
    public void reveal(UUID playerId, int cell) {
        if (cell < 0 || cell >= size() || revealed.contains(cell)) {
            return;
        }
        UUID currentId = current();
        if (currentId == null || !currentId.equals(playerId)) {
            return;
        }
        revealed.add(cell);
        turnTicks = 0;

        if (bombs.contains(cell)) {
            alive.remove(playerId);
            soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            broadcastPlain("group.bomb-board.boom",
                    "player", playerName(playerId),
                    "tile", cell + 1,
                    "amount", plugin.economy().format(pot.amountOf(playerId)));
            if (board != null) {
                board.close();
                board = null;
            }
            order.remove(playerId);
            if (turnIndex >= order.size()) {
                turnIndex = 0;
            }
            if (alive.size() <= 1) {
                settle();
                return;
            }
            broadcastPlain("group.bomb-board.pot-now",
                    "pot", plugin.economy().format(pot.total()), "alive", alive.size());
            openBoard();
            return;
        }

        Player player = online(playerId);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        }
        broadcastPlain("group.bomb-board.revealed",
                "player", playerName(playerId),
                "tile", cell + 1,
                "revealed", revealed.size(), "tiles", size());
        turnIndex++;
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        if (freeCells().isEmpty()) {
            settle();
            return;
        }
        openBoard();
    }

    private void settle() {
        if (board != null) {
            board.close();
            board = null;
        }
        if (alive.isEmpty()) {
            broadcastPlain("group.bomb-board.no-survivors");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastPlain("group.bomb-board.banner");
        broadcastPlain("group.bomb-board.winner",
                "player", playerName(winner), "pot", plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        showTitle(online(winner), "group.bomb-board.title", "group.bomb-board.title-subtitle",
                "prize", plugin.economy().format(total));
        endRound();
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("reveal".equals(action) && args.length > 0) {
            try {
                reveal(player.getUniqueId(), Integer.parseInt(args[0]) - 1);
            } catch (NumberFormatException error) {
                message(player, "group.invalid-tile");
            }
            return;
        }
        super.handleAction(player, action, args);
    }

    @Override
    protected void onQuitDuringRound(UUID playerId) {
        alive.remove(playerId);
        order.remove(playerId);
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        if (board != null) {
            board.close();
            board = null;
        }
        if (alive.size() <= 1 && !alive.isEmpty()) {
            settle();
        }
    }

    @Override
    protected void onRoundEnd() {
        if (board != null) {
            board.close();
            board = null;
        }
        bombs.clear();
        revealed.clear();
        order.clear();
        alive.clear();
        turnIndex = 0;
        turnTicks = 0;
        counting = false;
    }

    /**
     * Shared board that only the player holding the turn can click.
     */
    private final class BombBoardGui extends Gui {

        private static final ItemStack FILLER = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

        private final BombBoardGame game;

        BombBoardGui(MultiverseGamblingPlugin plugin, Player player, BombBoardGame game) {
            super(plugin, player, 6, plugin.messages().forSender(player, "panel.bomb-board.title",
                    "game", displayName(player)));
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(FILLER);

            set(4, Items.of(Material.GUNPOWDER)
                    .name(label(player(), "panel.bomb-board.info"))
                    .lore(labelLore(player(), "panel.bomb-board.info-lore",
                            "pot", plugin.economy().format(game.pot.total()),
                            "bombs", game.bombCount(),
                            "tiles", game.size(),
                            "revealed", game.revealed.size(),
                            "alive", game.alive.size()))
                    .glow(true)
                    .build());

            for (int cell = 0; cell < game.size() && cell < 45; cell++) {
                final int index = cell;
                int slot = 9 + (cell / 9) * 9 + (cell % 9);
                if (game.revealed.contains(cell)) {
                    boolean bomb = game.bombs.contains(cell);
                    set(slot, Items.of(bomb ? Material.TNT : Material.EMERALD)
                            .name(label(player(), bomb
                                    ? "panel.mines.bomb" : "panel.mines.safe"))
                            .lore(label(player(), "panel.mines.tile", "number", cell + 1))
                            .build());
                } else {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name(label(player(), "panel.mines.tile", "number", cell + 1))
                            .lore(label(player(), "panel.mines.click-reveal"))
                            .build(), e -> {
                        close();
                        game.reveal(player().getUniqueId(), index);
                    });
                }
            }

            set(49, Items.of(Material.CLOCK)
                    .name(label(player(), "panel.bomb-board.turn",
                            "player", game.playerName(game.current())))
                    .lore(label(player(), "panel.bomb-board.turn-lore"))
                    .build());
        }

        @Override
        public String sessionId() {
            return "tablero-bombs";
        }
    }
}
