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
 * Minas: destapa casillas seguras para subir el multiplicador.
 *
 * <p>Las minas se colocan con una mezcla verificable y el multiplicador es la
 * inversa exacta de la probabilidad de sobrevivir, asi que ningun numero de minas
 * es mejor que otro para el jugador.</p>
 */
public final class MinesGame extends AbstractSoloGame {

    public MinesGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("minas", "Minas", GameCategory.SOLO, Material.STONE_BUTTON)
                .desc("&7Destapa casillas seguras y retirate",
                        "&7cuando quieras. Con mas minas,",
                        "&7mas paga cada acierto.")
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

    private static final class MinesGui extends Gui {

        private final MinesGame game;
        private final Wager wager;
        private final Set<Integer> mines = new LinkedHashSet<>();
        private final Set<Integer> revealed = new LinkedHashSet<>();
        private int minesCount;
        private boolean placed;
        private boolean resolved;

        MinesGui(MultiverseGamblingPlugin plugin, Player player, MinesGame game, Wager wager, int minesCount) {
            super(plugin, player, 6, "&8Minas &7· &6Destapa o retire");
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

        /** Coloca las minas con la mezcla verificable del casino. */
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
            // Ordena por la tirada verificable: la posicion de cada mina es auditable.
            order.sort((a, b) -> Double.compare(rolls[a], rolls[b]));
            for (int i = 0; i < Math.min(minesCount, order.size()); i++) {
                mines.add(order.get(i));
            }
        }

        /** Rejilla de 5x5 dentro de las seis filas del menu. */
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
                    .name("&6Apuesta: &f" + plugin.economy().format(wager.amount()))
                    .lore(
                            "&7Minas: &c" + minesCount + " &7de &f" + tiles() + " &7casillas",
                            "&7Destapadas: &f" + revealed.size(),
                            current > 0 ? "&7Multiplicador actual: &a" + Text.multiplier(current) : "&7Aun sin destapar",
                            "&7Si destapas otra: &f" + Text.multiplier(next),
                            "",
                            placed ? "&7Casillas minadas: &c" + mines.size() : "&7Las minas se colocan al primer destape.")
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
                            .name("&cMina")
                            .lore("&7Aqui estaba la bomba.")
                            .build());
                } else if (revealed.isEmpty() && !placed && !resolved) {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name("&7Casilla " + (index + 1))
                            .lore("&7Pulsa para destapar", "&7Destaparla paga &f" + Text.multiplier(next))
                            .build(), e -> reveal(cell));
                } else if (resolved) {
                    set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE).name("&8Cerrada").build());
                } else {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name("&7Casilla " + (index + 1))
                            .lore("&7Pulsa para destapar", "&7Destaparla paga &f" + Text.multiplier(next))
                            .build(), e -> reveal(cell));
                }
            }

            // Controles de minas, solo antes de empezar.
            if (!placed && !resolved) {
                set(45, Items.of(Material.RED_DYE)
                        .name("&c-1 mina")
                        .lore("&7Minas: &f" + minesCount)
                        .build(), e -> adjustMines(-1));
                set(47, Items.of(Material.LIME_DYE)
                        .name("&a+1 mina")
                        .lore("&7Minas: &f" + minesCount)
                        .build(), e -> adjustMines(1));
            }

            if (!resolved) {
                set(49, Items.of(revealed.isEmpty() ? Material.CLOCK : Material.GOLD_BLOCK)
                        .name(revealed.isEmpty() ? "&7Destapa una casilla para empezar"
                                : "&a&lRETIRARSE &7(" + Text.multiplier(current) + ")")
                        .lore(revealed.isEmpty()
                                ? "&7El multiplicador sube con cada acierto."
                                : "&7Cobras &f" + plugin.economy().format(wager.amount() * current))
                        .glow(!revealed.isEmpty())
                        .build(), e -> cashOut());
            }

            set(53, Items.of(Material.BARRIER)
                    .name("&cSalir")
                    .lore(resolved ? "&7Partida terminada." : "&7Abandonar pierde la apuesta.")
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
                game.info(player(), "&7Pisaste una mina en la casilla &f" + (index + 1)
                        + "&7 y perdiste &f" + plugin.economy().format(wager.amount()));
                game.showResult(player(), wager.amount(), payout);
                game.sound(player(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
                game.offerReplay(player());
                return;
            }
            revealed.add(index);
            game.sound(player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
            if (revealed.size() >= tiles() - minesCount) {
                // Tablero limpio: se cobra automaticamente.
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
            game.info(player(), "&7Retirada en &f" + Text.multiplier(multiplier)
                    + "&7 con &f" + revealed.size() + "&7 casillas seguras de &f"
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
                // No llego a destapar nada: se le devuelve la apuesta.
                game.refund(wager);
                return;
            }
            // Con casillas ya destapadas, abandonar consume la apuesta: si no, se
            // podria espiar el tablero y volver a entrar.
            game.settle(player(), wager, 0);
            game.message(player(), "juegos.abandonada", "apuesta",
                    plugin.economy().format(wager.amount()));
        }

        @Override
        public String sessionId() {
            return "minas";
        }
    }
}
