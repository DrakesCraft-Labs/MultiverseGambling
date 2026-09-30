package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.MinesTable;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.BoardGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.board.ArenaBoard;
import com.chagui68.multiversegambling.world.board.BoardGrid;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Towers: climb the levels by picking the safe tile.
 *
 * <p>Every floor has several tiles and one bomb. The multiplier is the exact inverse
 * of the chance of chaining {@code floor} safe picks in a row.</p>
 */
public final class TowersGame extends AbstractSoloGame implements BoardGame {

    public TowersGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("towers", "Towers", GameCategory.SOLO, Material.LADDER)
                .desc("&7Every floor hides one bomb among",
                        "&7several tiles. Climb as high as",
                        "&7you dare and cash out in time.")
                .build());
    }

    int levels() {
        return plugin.config().towersLevels();
    }

    int tiles() {
        return plugin.config().towersTiles();
    }

    int bombs() {
        return plugin.config().towersBombs();
    }

    double houseEdge() {
        return plugin.config().houseEdge();
    }

    @Override
    public BoardGrid boardGrid() {
        return BoardGrid.centered(tiles(), levels());
    }

    @Override
    public int boardCells() {
        return tiles() * levels();
    }

    @Override
    public Material boardTile() {
        return Material.LIGHT_GRAY_CONCRETE;
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        TowerGui gui = new TowerGui(plugin, player, this, wager);
        ArenaStage stage = boardStageFor(player);
        if (stage != null) {
            TowerBoard board = new TowerBoard(gui, stage);
            gui.attach(board);
            if (board.open()) {
                board.bring(player);
                gui.showOnArena();
                return;
            }
            gui.attach(null);
        }
        gui.show();
    }

    private final class TowerGui extends Gui {

        private final TowersGame game;
        private final Wager wager;
        /**
         * Bomb of each cleared floor, so it can be drawn at the end.
         */
        private final java.util.Map<Integer, Integer> bombs = new java.util.HashMap<>();
        /** Arena board the round is painted on, or null when it uses this menu. */
        private TowerBoard board;
        private int level;
        private int currentBomb = -1;
        private boolean exploded;
        private boolean resolved;

        TowerGui(MultiverseGamblingPlugin plugin, Player player, TowersGame game, Wager wager) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.towers.title",
                    "game", displayName(player)));
            this.game = game;
            this.wager = wager;
            currentBomb = plugin.fair().rollInt(player.getUniqueId(), game.tiles());
        }

        private double multiplierAt(int reached) {
            return MinesTable.towerMultiplier(reached, game.tiles(), game.bombs(), game.houseEdge());
        }

        @Override
        protected void render() {
            if (board != null) {
                board.paint();
                return;
            }
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            double current = multiplierAt(level);
            double next = multiplierAt(level + 1);
            boolean finished = level >= game.levels();

            set(4, Items.of(Material.LADDER)
                    .name(label(player(), "panel.towers.floor",
                            "floor", level, "levels", game.levels()))
                    .lore(
                            label(player(), "panel.common.bet",
                                    "bet", plugin.economy().format(wager.amount())),
                            label(player(), "panel.towers.multiplier",
                                    "multiplier", Text.multiplier(current)),
                            finished ? label(player(), "panel.towers.top")
                                    : label(player(), "panel.towers.next",
                                    "multiplier", Text.multiplier(next)),
                            label(player(), "panel.towers.bombs", "count", game.bombs()),
                            "",
                            level == 0 ? label(player(), "panel.towers.first")
                                    : label(player(), "panel.towers.cash-hint"))
                    .glow(true)
                    .build());

            // Tiles of the current floor.
            int first = 11 + (game.tiles() > 7 ? 0 : (7 - game.tiles()) / 2);
            for (int tile = 0; tile < game.tiles(); tile++) {
                final int chosen = tile;
                int slot = first + tile;
                if (finished) {
                    set(slot, Items.of(Material.GREEN_STAINED_GLASS_PANE)
                            .name(label(player(), "panel.towers.top-tile"))
                            .build());
                } else if (resolved) {
                    set(slot, Items.of(tile == currentBomb ? Material.TNT : Material.EMERALD)
                            .name(label(player(), tile == currentBomb
                                    ? "panel.towers.bomb" : "panel.towers.safe"))
                            .build());
                } else {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name(label(player(), "panel.towers.tile", "number", tile + 1))
                            .lore(label(player(), "panel.towers.climb",
                                    "multiplier", Text.multiplier(next)))
                            .build(), e -> climb(chosen));
                }
            }

            // Floors already cleared.
            int row = 26;
            String trail = level == 0
                    ? label(player(), "panel.towers.trail")
                    : label(player(), "panel.towers.path", "marks", "&a✔ ".repeat(level));
            set(row, Items.of(Material.PAPER)
                    .name(label(player(), "panel.towers.progress"))
                    .lore(trail)
                    .build());

            if (!resolved) {
                set(40, Items.of(level == 0 ? Material.CLOCK : Material.GOLD_BLOCK)
                        .name(level == 0
                                ? label(player(), "panel.towers.start")
                                : label(player(), "panel.towers.cash-out",
                                "multiplier", Text.multiplier(current)))
                        .lore(level == 0
                                ? label(player(), "panel.towers.start-lore")
                                : label(player(), "panel.towers.cash-out-lore",
                                "prize", plugin.economy().format(wager.amount() * current)))
                        .glow(level > 0)
                        .build(), e -> cashOut());
            }

            set(44, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(resolved ? label(player(), "panel.common.round-finished")
                            : label(player(), "panel.common.leaving-loses"))
                    .build(), e -> close());
        }

        private void climb(int tile) {
            if (resolved || level >= game.levels()) {
                return;
            }
            if (tile == currentBomb) {
                resolved = true;
                exploded = true;
                double payout = game.settle(player(), wager, 0);
                render();
                game.announceResult(player(), false,
                        plugin.messages().forSender(player(), "panel.towers.boom",
                                "floor", level + 1));
                game.info(player(), game.title(player()));
                game.message(player(), "panel.towers.lost", "floor", level + 1,
                        "bet", plugin.economy().format(wager.amount()));
                game.showResult(player(), wager.amount(), payout);
                game.sound(player(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
                game.offerReplay(player());
                boardDone();
                return;
            }
            bombs.put(level, currentBomb);
            level++;
            game.sound(player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f + level * 0.1f);
            if (level >= game.levels()) {
                cashOut();
                return;
            }
            currentBomb = plugin.fair().rollInt(player().getUniqueId(), game.tiles());
            refresh();
            boardHint();
        }

        private void cashOut() {
            if (resolved || level == 0) {
                return;
            }
            resolved = true;
            double multiplier = multiplierAt(level);
            double payout = game.settle(player(), wager, multiplier);
            render();
            game.announceResult(player(), true,
                    plugin.messages().forSender(player(), "panel.towers.cashed-subtitle",
                            "multiplier", Text.multiplier(multiplier)));
            game.info(player(), game.title(player()));
            game.message(player(), "panel.towers.cashed",
                    "floors", level, "multiplier", Text.multiplier(multiplier));
            game.showResult(player(), wager.amount(), payout);
            game.sound(player(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            game.offerReplay(player());
            boardDone();
        }

        /**
         * Hands the round over to the arena board, or takes it back when the board
         * could not be taken.
         */
        private void attach(TowerBoard arenaBoard) {
            this.board = arenaBoard;
        }

        /** Player the round belongs to. */
        private Player owner() {
            return player();
        }

        /** Line the action bar shows while the round is being played on the board. */
        private void boardHint() {
            if (board == null) {
                return;
            }
            if (resolved) {
                actionBarKey(player(), "board.finished");
            } else if (level == 0) {
                actionBarKey(player(), "board.floor-pick", "floor", 1, "levels", game.levels());
            } else {
                actionBarKey(player(), "board.cash-out",
                        "prize", plugin.economy().format(wager.amount() * multiplierAt(level)));
            }
        }

        /** Closes the board view once the round is over. */
        private void boardDone() {
            if (board == null) {
                return;
            }
            boardHint();
            board.linger();
        }

        /** Leaving the arena ends the round like closing the menu would. */
        @Override
        protected void onTick() {
            if (board != null && !resolved && board.abandoned(player())) {
                close();
            }
        }

        @Override
        protected void onClose() {
            if (board != null) {
                board.close();
            }
            if (resolved) {
                return;
            }
            resolved = true;
            if (level == 0) {
                game.refund(wager);
                return;
            }
            game.settle(player(), wager, 0);
            game.message(player(), "games.abandoned", "bet",
                    plugin.economy().format(wager.amount()));
        }

        @Override
        public String sessionId() {
            return "towers";
        }
    }

    /**
     * The round painted on the arena: one row of tiles per floor, the floors already
     * climbed turning green, and the block on the frame south of the board as the
     * cashier, gold whenever there is something to collect.
     */
    private final class TowerBoard extends ArenaBoard {

        private final TowerGui gui;
        private final UUID owner;
        private final int collectDz;

        TowerBoard(TowerGui gui, ArenaStage stage) {
            super(plugin, "towers", stage, boardGrid());
            this.gui = gui;
            this.owner = gui.owner().getUniqueId();
            this.collectDz = grid().originZ() + grid().rows();
        }

        @Override
        public boolean accepts(Player player) {
            return player.getUniqueId().equals(owner);
        }

        @Override
        public void click(Player player, Block block, int cell) {
            if (gui.resolved) {
                hint(player);
                return;
            }
            if (cell >= 0) {
                int floor = cell / tiles();
                if (floor == gui.level) {
                    gui.climb(cell % tiles());
                } else {
                    hint(player);
                }
                return;
            }
            if (isAt(block, 0, 0, collectDz)) {
                gui.cashOut();
                return;
            }
            hint(player);
        }

        @Override
        public void hint(Player player) {
            if (!player.getUniqueId().equals(owner)) {
                actionBarKey(player, "board.other-player", "player", gui.owner().getName());
            } else if (gui.resolved) {
                actionBarKey(player, "board.finished");
            } else if (gui.level == 0) {
                actionBarKey(player, "board.floor-pick", "floor", 1, "levels", levels());
            } else {
                actionBarKey(player, "board.cash-out",
                        "prize", plugin.economy().format(gui.wager.amount() * gui.multiplierAt(gui.level)));
            }
        }

        @Override
        protected void onOpen() {
            paint();
            gui.boardHint();
        }

        @Override
        protected void onClose() {
            gui.close();
        }

        /**
         * Paints every floor of the tower: cleared ones green, the current one
         * clickable and the ones above it dark.
         */
        void paint() {
            for (int floor = 0; floor < levels(); floor++) {
                for (int tile = 0; tile < tiles(); tile++) {
                    paintCell(floor * tiles() + tile, material(floor, tile));
                }
            }
            paintAt(0, 0, collectDz, collectMaterial());
        }

        private Material material(int floor, int tile) {
            if (gui.level >= levels() || floor < gui.level) {
                return Material.LIME_CONCRETE;
            }
            if (floor > gui.level) {
                return Material.GRAY_CONCRETE;
            }
            if (gui.exploded) {
                return tile == gui.currentBomb ? Material.TNT : Material.EMERALD_BLOCK;
            }
            return boardTile();
        }

        private Material collectMaterial() {
            return gui.level > 0 && !gui.resolved
                    ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE;
        }
    }
}
