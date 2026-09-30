package com.freebuff.casino.games.solo;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.MinesTable;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.game.AbstractSoloGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Torre: sube pisos eligiendo la casilla segura.
 *
 * <p>Cada piso tiene varias casillas y una bomba. El multiplicador es la inversa
 * exacta de la probabilidad de encadenar {@code piso} aciertos seguidos.</p>
 */
public final class TowersGame extends AbstractSoloGame {

    public TowersGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("torre", "La Torre", GameCategory.SOLO, Material.LADDER)
                .desc("&7Cada piso esconde una bomba entre",
                        "&7varias casillas. Sube todo lo que",
                        "&7puedas y retirate a tiempo.")
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

    private static final class TowerGui extends Gui {

        private final TowersGame game;
        private final Wager wager;
        /** Bomba de cada piso ya superado, para poder dibujarla al final. */
        private final java.util.Map<Integer, Integer> bombs = new java.util.HashMap<>();
        private int level;
        private int currentBomb = -1;
        private boolean resolved;

        TowerGui(CasinoPlugin plugin, Player player, TowersGame game, Wager wager) {
            super(plugin, player, 5, "&8La Torre &7· &6Elige casilla segura");
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
                    .name("&6Piso &f" + level + "&7/&f" + game.levels())
                    .lore(
                            "&7Apuesta: &f" + plugin.economy().format(wager.amount()),
                            "&7Multiplicador: &a" + Text.multiplier(current),
                            finished ? "&aTope de la torre alcanzado"
                                    : "&7Si aciertas el siguiente piso: &f" + Text.multiplier(next),
                            "&7Bombas por piso: &c" + game.bombs(),
                            "",
                            level == 0 ? "&7La bomba se sortea al elegir."
                                    : "&7Retirate antes de caer.")
                    .glow(true)
                    .build());

            // Casillas del piso actual.
            int first = 11 + (game.tiles() > 7 ? 0 : (7 - game.tiles()) / 2);
            for (int tile = 0; tile < game.tiles(); tile++) {
                final int chosen = tile;
                int slot = first + tile;
                if (finished) {
                    set(slot, Items.of(Material.GREEN_STAINED_GLASS_PANE)
                            .name("&aCima alcanzada")
                            .build());
                } else if (resolved) {
                    set(slot, Items.of(tile == currentBomb ? Material.TNT : Material.EMERALD)
                            .name(tile == currentBomb ? "&cLa bomba" : "&aCasilla segura")
                            .build());
                } else {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name("&7Casilla " + (tile + 1))
                            .lore("&7Acierta y subes a " + Text.multiplier(next))
                            .build(), e -> climb(chosen));
                }
            }

            // Pisos ya superados.
            int row = 26;
            StringBuilder trail = new StringBuilder("&7Camino: ");
            if (bombs.isEmpty() && level == 0) {
                trail.append("&8aun sin empezar");
            } else {
                for (int i = 0; i < level; i++) {
                    trail.append("&a✔ ");
                }
            }
            set(row, Items.of(Material.PAPER).name("&7Progreso").lore(trail.toString()).build());

            if (!resolved) {
                set(40, Items.of(level == 0 ? Material.CLOCK : Material.GOLD_BLOCK)
                        .name(level == 0 ? "&7Elige una casilla para empezar"
                                : "&a&lRETIRARSE &7(" + Text.multiplier(current) + ")")
                        .lore(level == 0
                                ? "&7El primer piso no paga nada todavia."
                                : "&7Cobras &f" + plugin.economy().format(wager.amount() * current))
                        .glow(level > 0)
                        .build(), e -> cashOut());
            }

            set(44, Items.of(Material.BARRIER)
                    .name("&cSalir")
                    .lore(resolved ? "&7Partida terminada." : "&7Abandonar pierde la apuesta.")
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
                game.announceResult(player(), false, "&cboom en el piso " + (level + 1));
                game.info(player(), game.title());
                game.info(player(), "&7Cayo la bomba en el piso &f" + (level + 1)
                        + "&7 y perdiste &f" + plugin.economy().format(wager.amount()));
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
            game.announceResult(player(), true, "&a" + Text.multiplier(multiplier));
            game.info(player(), game.title());
            game.info(player(), "&7Subiste &f" + level + " &7pisos y te retiraste en &f"
                    + Text.multiplier(multiplier));
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
            game.message(player(), "juegos.abandonada", "apuesta",
                    plugin.economy().format(wager.amount()));
        }

        @Override
        public String sessionId() {
            return "torre";
        }
    }
}
