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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Mines: reveal safe tiles to raise the multiplier.
 *
 * <p>The mines are placed with a provably fair shuffle and the multiplier is the exact
 * inverse of the chance of surviving, so no number of mines is better than another
 * for the player.</p>
 */
public final class MinesGame extends AbstractSoloGame implements BoardGame {

    public MinesGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("mines", "Mines", GameCategory.SOLO, Material.STONE_BUTTON)
                .desc("&7Reveal safe tiles and cash out",
                        "&7whenever you want. The more bombs,",
                        "&7the more every safe pick pays.")
                .build());
    }

    int tiles() {
        return plugin.config().minesTiles();
    }

    int minMines() {
        return 1;
    }

    int maxMines() {
        return Math.min(plugin.config().minesMaxMines(), tiles() - 1);
    }

    double houseEdge() {
        return plugin.config().houseEdge();
    }

    @Override
    public BoardGrid boardGrid() {
        int columns = Math.min(5, tiles());
        return BoardGrid.centered(columns, (int) Math.ceil(tiles() / (double) columns));
    }

    @Override
    public int boardCells() {
        return tiles();
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
        MinesGui gui = new MinesGui(plugin, player, this, wager,
                Math.min(maxMines(), Math.max(minMines(), plugin.config().minesDefaultMines())));
        ArenaStage stage = boardStageFor(player);
        if (stage != null) {
            MinesBoard board = new MinesBoard(gui, stage);
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

    private final class MinesGui extends Gui {

        private final MinesGame game;
        private final Wager wager;
        private final Set<Integer> mines = new LinkedHashSet<>();
        private final Set<Integer> revealed = new LinkedHashSet<>();
        /** Arena board the round is painted on, or null when it uses this menu. */
        private MinesBoard board;
        private int minesCount;
        private boolean placed;
        private boolean resolved;

        MinesGui(MultiverseGamblingPlugin plugin, Player player, MinesGame game, Wager wager, int minesCount) {
            super(plugin, player, 6, plugin.messages().forSender(player, "panel.mines.title"));
            this.game = game;
            this.wager = wager;
            this.minesCount = minesCount;
        }

        private int tiles() {
            return game.tiles();
        }

        private double multiplier() {
            return MinesTable.multiplier(tiles(), minesCount, revealed.size(), game.houseEdge());
        }

        /**
         * Places the mines with the provably fair shuffle of the casino.
         */
        private void placeMines() {
            if (placed) {
                return;
            }
            placed = true;
            double[] rolls = plugin.fair().rolls(player().getUniqueId(), tiles());
            List<Integer> order = new ArrayList<>(tiles());
            for (int i = 0; i < tiles(); i++) {
                order.add(i);
            }
            // Sorted by the provably fair roll, so the position of every mine is auditable.
            order.sort((a, b) -> Double.compare(rolls[a], rolls[b]));
            for (int i = 0; i < Math.min(minesCount, order.size()); i++) {
                mines.add(order.get(i));
            }
        }

        /**
         * 5x5 grid inside the six rows of the menu.
         */
        private int slotOf(int index) {
            // Five columns in the middle of the menu, rows 0 to 4: the bottom row is left to
            // the buttons, so no control ever covers a tile.
            return (index / 5) * 9 + 2 + (index % 5);
        }

        @Override
        protected void render() {
            if (board != null) {
                board.paint();
                return;
            }
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
            for (int slot : new int[]{0, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52}) {
                set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
            }

            boolean canRevealMore = revealed.size() < tiles() - minesCount;
            double next = canRevealMore
                    ? MinesTable.multiplier(tiles(), minesCount, revealed.size() + 1, game.houseEdge())
                    : 0;
            double current = revealed.isEmpty() ? 0 : multiplier();

            set(9, Items.of(Material.GOLD_INGOT)
                    .name(label(player(), "panel.common.bet",
                            "bet", plugin.economy().format(wager.amount())))
                    .lore(
                            label(player(), "panel.mines.info",
                                    "count", minesCount, "tiles", tiles()),
                            label(player(), "panel.mines.revealed", "count", revealed.size()),
                            current > 0
                                    ? label(player(), "panel.mines.current",
                                    "multiplier", Text.multiplier(current))
                                    : label(player(), "panel.mines.nothing-revealed"),
                            label(player(), "panel.mines.next",
                                    "multiplier", Text.multiplier(next)),
                            "",
                            placed
                                    ? label(player(), "panel.mines.placed", "count", mines.size())
                                    : label(player(), "panel.mines.not-placed"))
                    .glow(true)
                    .build());

            for (int index = 0; index < tiles() && index < 25; index++) {
                final int cell = index;
                int slot = slotOf(index);
                if (revealed.contains(index)) {
                    set(slot, Items.of(Material.EMERALD)
                            .name(label(player(), "panel.mines.safe"))
                            .lore(label(player(), "panel.mines.safe-lore",
                                    "multiplier", Text.multiplier(multiplier())))
                            .build());
                } else if (resolved && mines.contains(index)) {
                    set(slot, Items.of(Material.TNT)
                            .name(label(player(), "panel.mines.bomb"))
                            .lore(label(player(), "panel.mines.bomb-lore"))
                            .build());
                } else if (revealed.isEmpty() && !placed && !resolved) {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name(label(player(), "panel.mines.tile", "number", index + 1))
                            .lore(label(player(), "panel.mines.click-reveal"),
                                    label(player(), "panel.mines.reveal-pays",
                                            "multiplier", Text.multiplier(next)))
                            .build(), e -> reveal(cell));
                } else if (resolved) {
                    set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE)
                            .name(label(player(), "panel.mines.closed")).build());
                } else {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name(label(player(), "panel.mines.tile", "number", index + 1))
                            .lore(label(player(), "panel.mines.click-reveal"),
                                    label(player(), "panel.mines.reveal-pays",
                                            "multiplier", Text.multiplier(next)))
                            .build(), e -> reveal(cell));
                }
            }

            // Mine controls, only before the round starts.
            if (!placed && !resolved) {
                set(17, Items.of(Material.RED_DYE)
                        .name(label(player(), "panel.mines.remove"))
                        .lore(label(player(), "panel.mines.count", "count", minesCount))
                        .build(), e -> adjustMines(-1));
                set(26, Items.of(Material.TNT)
                        .name(label(player(), "panel.mines.count", "count", minesCount))
                        .amount(Math.max(1, Math.min(64, minesCount)))
                        .build());
                set(35, Items.of(Material.LIME_DYE)
                        .name(label(player(), "panel.mines.add"))
                        .lore(label(player(), "panel.mines.count", "count", minesCount))
                        .build(), e -> adjustMines(1));
            }

            if (!resolved) {
                set(49, Items.of(revealed.isEmpty() ? Material.CLOCK : Material.GOLD_BLOCK)
                        .name(revealed.isEmpty()
                                ? label(player(), "panel.mines.start")
                                : label(player(), "panel.mines.cash-out",
                                "multiplier", Text.multiplier(current)))
                        .lore(revealed.isEmpty()
                                ? label(player(), "panel.mines.start-lore")
                                : label(player(), "panel.mines.cash-out-lore",
                                "prize", plugin.economy().format(wager.amount() * current)))
                        .glow(!revealed.isEmpty())
                        .build(), e -> cashOut());
            }

            set(53, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(resolved ? label(player(), "panel.mines.close-lore-done")
                            : label(player(), "panel.mines.close-lore-live"))
                    .build(), e -> close());
        }

        /**
         * Hands the round over to the arena board, or takes it back when the board
         * could not be taken.
         */
        private void attach(MinesBoard arenaBoard) {
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
            } else if (revealed.isEmpty()) {
                actionBarKey(player(), "board.mines-count", "count", minesCount, "tiles", tiles());
            } else {
                actionBarKey(player(), "board.cash-out",
                        "prize", plugin.economy().format(wager.amount() * multiplier()));
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

        private void adjustMines(int delta) {
            if (placed || resolved) {
                return;
            }
            minesCount = Math.max(game.minMines(), Math.min(game.maxMines(), minesCount + delta));
            refresh();
            boardHint();
        }

        private void reveal(int index) {
            if (resolved || revealed.contains(index)) {
                return;
            }
            placeMines();
            if (mines.contains(index)) {
                resolved = true;
                double payout = game.settle(player(), wager, 0);
                render();
                game.announceResult(player(), false,
                        plugin.messages().forSender(player(), "panel.mines.boom"));
                game.info(player(), game.title(player()));
                game.message(player(), "panel.mines.lost",
                        "tile", index + 1, "bet", plugin.economy().format(wager.amount()));
                game.showResult(player(), wager.amount(), payout);
                game.sound(player(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
                game.offerReplay(player());
                boardDone();
                return;
            }
            revealed.add(index);
            game.sound(player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
            if (revealed.size() >= tiles() - minesCount) {
                // Clean board: it is cashed out automatically.
                cashOut();
                return;
            }
            refresh();
            boardHint();
        }

        private void cashOut() {
            if (resolved || revealed.isEmpty()) {
                return;
            }
            resolved = true;
            double multiplier = multiplier();
            double payout = game.settle(player(), wager, multiplier);
            render();
            game.announceResult(player(), true,
                    plugin.messages().forSender(player(), "panel.mines.cashed-subtitle",
                            "multiplier", Text.multiplier(multiplier)));
            game.info(player(), game.title(player()));
            game.message(player(), "panel.mines.cashed",
                    "multiplier", Text.multiplier(multiplier),
                    "safe", revealed.size(), "total", tiles() - minesCount);
            game.showResult(player(), wager.amount(), payout);
            game.sound(player(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            game.offerReplay(player());
            boardDone();
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
            if (revealed.isEmpty()) {
                // They never revealed anything: the stake is refunded.
                game.refund(wager);
                return;
            }
            // With tiles already revealed the mines are placed and every revealed tile was
            // a real risk taken: leaving cashes out what was won, never a free peek.
            double multiplier = multiplier();
            double payout = game.settle(player(), wager, multiplier);
            game.message(player(), "games.closed-cashed",
                    "multiplier", Text.multiplier(multiplier),
                    "prize", plugin.economy().format(payout));
        }

        @Override
        public String sessionId() {
            return "mines";
        }
    }

    /**
     * The round painted on the arena: one tile per cell and the block on the frame
     * south of the board as the cashier, gold whenever there is something to collect.
     */
    private final class MinesBoard extends ArenaBoard {

        /** Blocks west and east of the cashier that raise and lower the mine count. */
        private static final int MARKER = 2;

        private final MinesGui gui;
        private final UUID owner;
        private final int collectDz;

        MinesBoard(MinesGui gui, ArenaStage stage) {
            super(plugin, "mines", stage, boardGrid());
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
            if (cell >= 0 && cell < tiles()) {
                gui.reveal(cell);
                return;
            }
            if (isAt(block, 0, 0, collectDz)) {
                if (gui.revealed.isEmpty()) {
                    hint(player);
                    return;
                }
                gui.cashOut();
                return;
            }
            if (!gui.placed && (isAt(block, -MARKER, 0, collectDz) || isAt(block, MARKER, 0, collectDz))) {
                gui.adjustMines(isAt(block, -MARKER, 0, collectDz) ? -1 : 1);
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
            } else if (gui.revealed.isEmpty()) {
                actionBarKey(player, "board.mines-count", "count", gui.minesCount, "tiles", tiles());
            } else {
                actionBarKey(player, "board.cash-out",
                        "prize", plugin.economy().format(gui.wager.amount() * gui.multiplier()));
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
         * Paints the whole round: tiles, the filler of a grid the configuration does
         * not fill, the cashier and the two blocks that choose how many mines there
         * are, both of them only before the first reveal.
         */
        void paint() {
            for (int cell = 0; cell < tiles(); cell++) {
                paintCell(cell, cellMaterial(cell));
            }
            for (int cell = tiles(); cell < grid().cellCount(); cell++) {
                paintCell(cell, boardFiller());
            }
            boolean choosing = !gui.placed && !gui.resolved;
            paintAt(0, 0, collectDz, collectMaterial());
            paintAt(-MARKER, 0, collectDz, choosing ? Material.RED_CONCRETE : Material.POLISHED_BLACKSTONE);
            paintAt(MARKER, 0, collectDz, choosing ? Material.LIME_CONCRETE : Material.POLISHED_BLACKSTONE);
            Player viewer = gui.owner();
            boolean canRevealMore = gui.revealed.size() < tiles() - gui.minesCount;
            double next = canRevealMore
                    ? MinesTable.multiplier(tiles(), gui.minesCount, gui.revealed.size() + 1, houseEdge())
                    : 0;
            double current = gui.revealed.isEmpty() ? 0 : gui.multiplier();
            status(Text.c(plugin.messages().forSender(viewer, choosing
                            ? "board.mines-status-choosing" : "board.mines-status",
                    "count", gui.minesCount, "tiles", tiles(),
                    "next", Text.multiplier(next),
                    "current", Text.multiplier(current),
                    "prize", plugin.economy().format(gui.wager.amount() * current))));
        }

        private Material cellMaterial(int cell) {
            if (gui.revealed.contains(cell)) {
                return Material.EMERALD_BLOCK;
            }
            if (gui.resolved && gui.mines.contains(cell)) {
                return Material.TNT;
            }
            return boardTile();
        }

        private Material collectMaterial() {
            return !gui.resolved && !gui.revealed.isEmpty()
                    ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE;
        }
    }
}
