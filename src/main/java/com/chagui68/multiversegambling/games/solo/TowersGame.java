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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Towers: climb the levels by picking the safe tile.
 *
 * <p>Every floor has several tiles and one bomb. The multiplier is the exact inverse
 * of the chance of chaining {@code floor} safe picks in a row.</p>
 */
public final class TowersGame extends AbstractSoloGame {

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
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new TowerGui(plugin, player, this, wager).show();
    }

    private final class TowerGui extends Gui {

        private final TowersGame game;
        private final Wager wager;
        /**
         * Bomb of each cleared floor, so it can be drawn at the end.
         */
        private final java.util.Map<Integer, Integer> bombs = new java.util.HashMap<>();
        private int level;
        private int currentBomb = -1;
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
        }

        @Override
        protected void onClose() {
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
}
