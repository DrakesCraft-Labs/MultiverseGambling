package com.freebuff.casino.games.solo;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.Card;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.game.AbstractSoloGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Mayor o Menor.
 *
 * <p>El pago de cada paso no es fijo: sale de los rangos que realmente quedan por
 * encima o por debajo, recortado por la ventaja de la casa. Asi acertar "menor"
 * con un dos paga mucho mas que con un rey, y el juego no se puede explotar
 * eligiendo siempre el lado facil.</p>
 */
public final class HighLowGame extends AbstractSoloGame {

    private static final int MIN_RANK = 2;
    private static final int MAX_RANK = Card.ACE;

    public HighLowGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("mayor-menor", "Mayor o Menor", GameCategory.SOLO, Material.PAPER)
                .desc("&7Adivina si la siguiente carta es mayor",
                        "&7o menor. Encadena aciertos para",
                        "&7multiplicar y retirate cuando quieras.")
                .build());
    }

    static int rankCount(int rank, boolean higher) {
        int count = 0;
        for (int other = MIN_RANK; other <= MAX_RANK; other++) {
            if (other == rank) {
                continue;
            }
            if (higher ? other > rank : other < rank) {
                count++;
            }
        }
        return count;
    }

    /** Multiplicador de un paso, segun cuantos rangos favorables quedan. */
    double stepMultiplier(int rank, boolean higher, double houseEdge) {
        int favourable = rankCount(rank, higher);
        if (favourable <= 0) {
            return 0;
        }
        int pool = MAX_RANK - MIN_RANK;
        return (1.0 - houseEdge) * pool / favourable;
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        int rank = ThreadLocalRandom.current().nextInt(MIN_RANK, MAX_RANK + 1);
        new HighLowGui(plugin, player, this, wager, rank).show();
    }

    int nextRank(Player player) {
        return ThreadLocalRandom.current().nextInt(MIN_RANK, MAX_RANK + 1);
    }

    private static final class HighLowGui extends Gui {

        private final HighLowGame game;
        private final Wager wager;
        private int currentRank;
        private double chain = 1.0;
        private int steps;
        private boolean resolved;

        HighLowGui(CasinoPlugin plugin, Player player, HighLowGame game, Wager wager, int rank) {
            super(plugin, player, 5, "&8Mayor o Menor");
            this.game = game;
            this.wager = wager;
            this.currentRank = rank;
        }

        private static String rankLabel(int rank) {
            return switch (rank) {
                case 11 -> "J";
                case 12 -> "Q";
                case 13 -> "K";
                case 14 -> "A";
                default -> String.valueOf(rank);
            };
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            double higher = game.stepMultiplier(currentRank, true, plugin.config().houseEdge());
            double lower = game.stepMultiplier(currentRank, false, plugin.config().houseEdge());
            boolean maxSteps = steps >= plugin.config().highLowMaxChain();

            set(4, Items.of(Material.PAPER)
                    .name("&6Carta actual: &f" + rankLabel(currentRank))
                    .lore(
                            "&7Apuesta: &f" + plugin.economy().format(wager.amount()),
                            "&7Encadenado: &a" + Text.multiplier(chain),
                            "&7Pasos: &f" + steps + "&7/&f" + plugin.config().highLowMaxChain(),
                            steps > 0 ? "&7Retirarte paga &f"
                                    + plugin.economy().format(wager.amount() * chain) : "&7Aun sin aciertos")
                    .glow(true)
                    .build());

            set(21, Items.of(Material.LIME_CONCRETE)
                    .name("&aMAYOR")
                    .lore("&7La siguiente carta sera mas alta.",
                            "&7Paga &f" + Text.multiplier(higher) + " &7si aciertas.",
                            "&7Rangos favorables: &f" + rankCount(currentRank, true),
                            "", "&ePulsa para elegir mayor")
                    .build(), e -> guess(true));

            set(23, Items.of(Material.RED_CONCRETE)
                    .name("&cMENOR")
                    .lore("&7La siguiente carta sera mas baja.",
                            "&7Paga &f" + Text.multiplier(lower) + " &7si aciertas.",
                            "&7Rangos favorables: &f" + rankCount(currentRank, false),
                            "", "&ePulsa para elegir menor")
                    .build(), e -> guess(false));

            set(40, Items.of(steps > 0 && !resolved ? Material.GOLD_BLOCK : Material.GRAY_DYE)
                    .name(steps > 0 ? "&6&lRETIRARSE" : "&7Sin nada que retirar")
                    .lore(steps > 0 ? "&7Cobras &f" + plugin.economy().format(wager.amount() * chain)
                            : "&7Acierta al menos una vez.")
                    .glow(steps > 0 && !resolved)
                    .build(), e -> cashOut());

            set(36, Items.of(Material.BARRIER)
                    .name("&cSalir")
                    .lore(resolved ? "&7Partida terminada." : "&7Retirate para no perder el encadenado.")
                    .build(), e -> close());

            if (maxSteps) {
                set(44, Items.of(Material.NETHER_STAR)
                        .name("&6Racha maxima alcanzada")
                        .lore("&7Has llegado al tope de pasos.", "&7Retirate para cobrar.")
                        .build());
            }
        }

        private void guess(boolean higher) {
            if (resolved) {
                return;
            }
            double multiplier = game.stepMultiplier(currentRank, higher, plugin.config().houseEdge());
            int next = game.nextRank(player());
            // Un empate no hace perder el encadenado: se repite la carta.
            if (next == currentRank) {
                game.sound(player(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.0f);
                game.info(player(), "&7Salio la misma carta, se repite el turno.");
                return;
            }
            boolean won = higher ? next > currentRank : next < currentRank;
            if (!won) {
                resolved = true;
                double payout = game.settle(player(), wager, 0);
                render();
                game.announceResult(player(), false, "&c" + rankLabel(next));
                game.info(player(), game.title());
                game.info(player(), "&7Salio &f" + rankLabel(next) + "&7 y perdiste el encadenado de &f"
                        + Text.multiplier(chain));
                game.showResult(player(), wager.amount(), payout);
                game.sound(player(), Sound.ENTITY_VILLAGER_NO, 0.9f, 0.9f);
                game.offerReplay(player());
                return;
            }
            chain *= multiplier;
            steps++;
            currentRank = next;
            game.sound(player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f + steps * 0.1f);
            if (steps >= plugin.config().highLowMaxChain()) {
                cashOut();
                return;
            }
            refresh();
        }

        private void cashOut() {
            if (resolved || steps == 0) {
                return;
            }
            resolved = true;
            double payout = game.settle(player(), wager, chain);
            render();
            game.announceResult(player(), true, "&a" + Text.multiplier(chain));
            game.info(player(), game.title());
            game.info(player(), "&7Encadenaste &f" + steps + " &7aciertos hasta &f" + Text.multiplier(chain));
            game.showResult(player(), wager.amount(), payout);
            game.sound(player(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            game.offerReplay(player());
        }

        @Override
        protected void onClose() {
            if (resolved) {
                return;
            }
            if (steps == 0) {
                game.refund(wager);
                return;
            }
            // Con aciertos encadenados, cerrar cobra automaticamente: seria absurdo
            // regalar la apuesta a quien ya ha ganado.
            resolved = true;
            double payout = game.settle(player(), wager, chain);
            game.info(player(), "&7Cerraste el menu: se cobro tu encadenado en &f"
                    + Text.multiplier(chain) + " &7(" + plugin.economy().format(payout) + "&7).");
        }

        @Override
        public String sessionId() {
            return "mayor-menor";
        }
    }
}
