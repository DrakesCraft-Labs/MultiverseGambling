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
            super(plugin, player, 6, "&8Mines &7· &6Reveal or cash out");
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

        /** Places the mines with the provably fair shuffle of the casino. */
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

        /** 5x5 grid inside the six rows of the menu. */
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
                    .name("&6Bet: &f" + plugin.economy().format(wager.amount()))
                    .lore(
                            "&7Mines: &c" + minesCount + " &7of &f" + tiles() + " &7tiles",
                            "&7Revealed: &f" + revealed.size(),
                            current > 0 ? "&7Current multiplier: &a" + Text.multiplier(current) : "&7Nothing revealed yet",
                            "&7If you reveal another: &f" + Text.multiplier(next),
                            "",
                            placed ? "&7Mined tiles: &c" + mines.size() : "&7Mines are placed on the first reveal.")
                    .glow(true)
                    .build());

            for (int index = 0; index < tiles() && index < 25; index++) {
                final int cell = index;
                int slot = slotOf(index);
                if (revealed.contains(index)) {
                    set(slot, Items.of(Material.EMERALD)
                            .name("&aSegura")
                            .lore("&7Pago acumulado: &f" + Text.multiplier(multiplier()))
                            .build());
                } else if (resolved && mines.contains(index)) {
                    set(slot, Items.of(Material.TNT)
                            .name("&cMine")
                            .lore("&7The bomb was here.")
                            .build());
                } else if (revealed.isEmpty() && !placed && !resolved) {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name("&7Casilla " + (index + 1))
                            .lore("&7Click to reveal", "&7Revealing it pays &f" + Text.multiplier(next))
                            .build(), e -> reveal(cell));
                } else if (resolved) {
                    set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE).name("&8Cerrada").build());
                } else {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name("&7Casilla " + (index + 1))
                            .lore("&7Click to reveal", "&7Revealing it pays &f" + Text.multiplier(next))
                            .build(), e -> reveal(cell));
                }
            }

            // Mine controls, only before the round starts.
            if (!placed && !resolved) {
                set(45, Items.of(Material.RED_DYE)
                        .name("&c-1 mine")
                        .lore("&7Mines: &f" + minesCount)
                        .build(), e -> adjustMines(-1));
                set(47, Items.of(Material.LIME_DYE)
                        .name("&a+1 mine")
                        .lore("&7Mines: &f" + minesCount)
                        .build(), e -> adjustMines(1));
            }

            if (!resolved) {
                set(49, Items.of(revealed.isEmpty() ? Material.CLOCK : Material.GOLD_BLOCK)
                        .name(revealed.isEmpty() ? "&7Reveal a tile to start"
                                : "&a&lCASH OUT &7(" + Text.multiplier(current) + ")")
                        .lore(revealed.isEmpty()
                                ? "&7The multiplier rises with every safe pick."
                                : "&7Cobras &f" + plugin.economy().format(wager.amount() * current))
                        .glow(!revealed.isEmpty())
                        .build(), e -> cashOut());
            }

            set(53, Items.of(Material.BARRIER)
                    .name("&cClose")
                    .lore(resolved ? "&7Round finished." : "&7Leaving loses the stake.")
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
                game.announceResult(player(), false, "&cboom");
                game.info(player(), game.title());
                game.info(player(), "&7You stepped on a mine on tile &f" + (index + 1)
                        + "&7 and you lost &f" + plugin.economy().format(wager.amount()));
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
            game.announceResult(player(), true, "&a" + Text.multiplier(multiplier));
            game.info(player(), game.title());
            game.info(player(), "&7Cashed out at &f" + Text.multiplier(multiplier)
                    + "&7 with &f" + revealed.size() + "&7 safe tiles out of &f"
                    + (tiles() - minesCount));
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
