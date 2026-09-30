package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.MinesTable;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Mines: reveal safe tiles to raise the multiplier.
 *
 * <p>The mines are placed with a provably fair shuffle and the multiplier is the exact
 * inverse of the chance of surviving, so no number of mines is better than another
 * for the player.</p>
 */
public final class MinesGame extends AbstractSoloGame {

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
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new MinesGui(plugin, player, this, wager,
                Math.min(maxMines(), Math.max(minMines(), plugin.config().minesDefaultMines()))).show();
    }

    private final class MinesGui extends Gui {

        private final MinesGame game;
        private final Wager wager;
        private final Set<Integer> mines = new LinkedHashSet<>();
        private final Set<Integer> revealed = new LinkedHashSet<>();
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
            return 10 + (index / 5) * 9 + (index % 5);
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

            boolean canRevealMore = revealed.size() < tiles() - minesCount;
            double next = canRevealMore
                    ? MinesTable.multiplier(tiles(), minesCount, revealed.size() + 1, game.houseEdge())
                    : 0;
            double current = revealed.isEmpty() ? 0 : multiplier();

            set(4, Items.of(Material.GOLD_INGOT)
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
                set(45, Items.of(Material.RED_DYE)
                        .name(label(player(), "panel.mines.remove"))
                        .lore(label(player(), "panel.mines.count", "count", minesCount))
                        .build(), e -> adjustMines(-1));
                set(47, Items.of(Material.LIME_DYE)
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

        private void adjustMines(int delta) {
            if (placed || resolved) {
                return;
            }
            minesCount = Math.max(game.minMines(), Math.min(game.maxMines(), minesCount + delta));
            refresh();
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
        }

        @Override
        protected void onClose() {
            if (resolved) {
                return;
            }
            resolved = true;
            if (revealed.isEmpty()) {
                // They never revealed anything: the stake is refunded.
                game.refund(wager);
                return;
            }
            // With tiles already revealed, leaving consumes the stake: otherwise a
            // player could peek at the board and come back in.
            game.settle(player(), wager, 0);
            game.message(player(), "games.abandoned", "bet",
                    plugin.economy().format(wager.amount()));
        }

        @Override
        public String sessionId() {
            return "mines";
        }
    }
}
