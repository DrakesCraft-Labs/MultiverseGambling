package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.ScratchCardTable;
import com.chagui68.multiversegambling.engine.ScratchCardTable.Face;
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
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Scratch card.
 *
 * <p>All the math lives in {@link ScratchCardTable}, which is pure Java and covered by
 * tests: this class only paints the card.</p>
 */
public final class ScratchCardGame extends AbstractSoloGame implements BoardGame {

    private final ScratchCardTable table = new ScratchCardTable();

    public ScratchCardGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("scratch", "Scratch Card", GameCategory.SOLO, Material.MAP)
                .desc("&7Reveal three tiles out of nine.",
                        "&7Three of a kind pay the symbol prize",
                        "&7and two of a kind give part of it back.")
                .build());
    }

    @Override
    public BoardGrid boardGrid() {
        return BoardGrid.centered(3, 3);
    }

    @Override
    public int boardCells() {
        return ScratchCardTable.CELLS;
    }

    @Override
    public Material boardTile() {
        return Material.WHITE_CONCRETE;
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        List<Face> card = table.newCard(() -> plugin.fair().roll(player.getUniqueId()));
        ScratchGui gui = new ScratchGui(plugin, player, this, wager, card);
        ArenaStage stage = boardStageFor(player);
        if (stage != null) {
            ScratchBoard board = new ScratchBoard(gui, stage);
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

    /**
     * Pays the card according to the player's picks.
     */
    public double resolve(Player player, Wager wager, List<Face> picked) {
        return settle(player, wager, table.payout(picked));
    }

    private static Material iconOf(Face face) {
        return switch (face) {
            case CHERRY -> Material.RED_DYE;
            case LEMON -> Material.YELLOW_DYE;
            case BELL -> Material.BELL;
            case DIAMOND -> Material.DIAMOND;
            case SEVEN -> Material.GOLD_INGOT;
            case CROWN -> Material.NETHER_STAR;
        };
    }

    /**
     * Block a card face is painted with on the arena board, one colour each.
     */
    static Material blockOf(Face face) {
        return switch (face) {
            case CHERRY -> Material.RED_CONCRETE;
            case LEMON -> Material.YELLOW_CONCRETE;
            case BELL -> Material.GOLD_BLOCK;
            case DIAMOND -> Material.DIAMOND_BLOCK;
            case SEVEN -> Material.EMERALD_BLOCK;
            case CROWN -> Material.NETHERITE_BLOCK;
        };
    }

    /**
     * Name of a card face in the language of the reader.
     */
    String faceName(Player viewer, Face face) {
        return plugin.messages().forSenderOr(viewer, "panel.scratch.face." + face.id(), face.id());
    }

    private final class ScratchGui extends Gui {

        private final ScratchCardGame game;
        private final Wager wager;
        private final List<Face> card;
        private final List<Integer> picked = new ArrayList<>();
        /** Arena board the round is painted on, or null when it uses this menu. */
        private ScratchBoard board;
        private boolean resolved;

        ScratchGui(MultiverseGamblingPlugin plugin, Player player, ScratchCardGame game, Wager wager, List<Face> card) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.scratch.title",
                    "game", displayName(player)));
            this.game = game;
            this.wager = wager;
            this.card = card;
        }

        @Override
        protected void render() {
            if (board != null) {
                board.paint();
                return;
            }
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            set(4, Items.of(Material.MAP)
                    .name(label(player(), "panel.scratch.card"))
                    .lore(labelLore(player(), "panel.scratch.card-lore",
                            "bet", plugin.economy().format(wager.amount()),
                            "picked", picked.size(),
                            "picks", ScratchCardTable.PICKS,
                            "pair", Text.multiplier(ScratchCardTable.PAIR_PAYOUT),
                            "rtp", Text.percent(game.table.rtp())))
                    .glow(true)
                    .build());

            int[] slots = {11, 12, 13, 20, 21, 22, 29, 30, 31};
            for (int index = 0; index < ScratchCardTable.CELLS; index++) {
                final int cell = index;
                int slot = slots[cell];
                boolean shown = picked.contains(cell) || resolved;
                Face face = card.get(cell);
                if (!shown) {
                    set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE)
                            .name(label(player(), "panel.scratch.hidden"))
                            .lore(label(player(), "panel.scratch.click-reveal"))
                            .build(), e -> reveal(cell));
                } else {
                    set(slot, Items.of(iconOf(face))
                            .name(game.faceName(player(), face))
                            .lore(picked.contains(cell)
                                            ? label(player(), "panel.scratch.picked")
                                            : label(player(), "panel.scratch.not-picked"),
                                    label(player(), "panel.scratch.triple-pays",
                                            "multiplier", Text.multiplier(face.triple())))
                            .glow(picked.contains(cell))
                            .build());
                }
            }

            int remaining = ScratchCardTable.PICKS - picked.size();
            set(40, Items.of(remaining > 0 ? Material.CLOCK : Material.EMERALD_BLOCK)
                    .name(remaining > 0
                            ? label(player(), "panel.scratch.picks-left", "picks", remaining)
                            : label(player(), "panel.scratch.complete"))
                    .lore(label(player(), "panel.scratch.complete-lore"))
                    .build());

            set(36, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(resolved ? label(player(), "panel.scratch.done-lore")
                            : label(player(), "panel.common.refund-lore"))
                    .build(), e -> close());
        }

        private void reveal(int index) {
            if (resolved || picked.contains(index) || picked.size() >= ScratchCardTable.PICKS) {
                return;
            }
            picked.add(index);
            game.sound(player(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.1f + picked.size() * 0.25f);
            if (picked.size() < ScratchCardTable.PICKS) {
                refresh();
                boardHint();
                return;
            }

            resolved = true;
            List<Face> chosen = picked.stream().map(card::get).toList();
            double payout = game.resolve(player(), wager, chosen);
            render();

            game.announceResult(player(), payout > wager.amount(),
                    payout > 0 ? "&f" + plugin.economy().format(payout)
                            : plugin.messages().forSender(player(), "panel.common.no-prize"));
            game.info(player(), game.title(player()));
            game.message(player(), "panel.scratch.scratched",
                    "faces", chosen.stream().map(face -> game.faceName(player(), face))
                            .reduce((a, b) -> a + "&7, " + b).orElse("-"));
            game.showResult(player(), wager.amount(), payout);
            game.sound(player(), payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                    0.9f, payout > wager.amount() ? 1.3f : 0.9f);
            game.offerReplay(player());
            boardDone();
        }

        /**
         * Hands the round over to the arena board, or takes it back when the board
         * could not be taken.
         */
        private void attach(ScratchBoard arenaBoard) {
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
            } else {
                actionBarKey(player(), "panel.scratch.picks-left",
                        "picks", ScratchCardTable.PICKS - picked.size());
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
            if (!resolved) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "scratch";
        }
    }

    /**
     * The card painted on the arena: hidden tiles in grey and every scratch turning
     * its block into the colour of the symbol underneath, so the whole card is read
     * at a glance from the middle of the board.
     */
    private final class ScratchBoard extends ArenaBoard {

        private final ScratchGui gui;
        private final UUID owner;

        ScratchBoard(ScratchGui gui, ArenaStage stage) {
            super(plugin, "scratch", stage, boardGrid());
            this.gui = gui;
            this.owner = gui.owner().getUniqueId();
        }

        @Override
        public boolean accepts(Player player) {
            return player.getUniqueId().equals(owner);
        }

        @Override
        public void click(Player player, Block block, int cell) {
            if (cell >= 0 && !gui.resolved) {
                gui.reveal(cell);
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
            } else {
                actionBarKey(player, "panel.scratch.picks-left",
                        "picks", ScratchCardTable.PICKS - gui.picked.size());
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
         * Paints the card: tiles still to scratch in grey, the rest with the symbol
         * that came up, and the whole card once it is settled.
         */
        void paint() {
            for (int cell = 0; cell < ScratchCardTable.CELLS; cell++) {
                boolean shown = gui.picked.contains(cell) || gui.resolved;
                paintCell(cell, shown ? blockOf(gui.card.get(cell)) : Material.LIGHT_GRAY_CONCRETE);
            }
        }
    }
}
