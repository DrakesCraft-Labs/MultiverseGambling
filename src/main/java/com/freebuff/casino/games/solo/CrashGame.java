package com.freebuff.casino.games.solo;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.CrashTable;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.game.AbstractSoloGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.session.TimedSession;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Crash: la curva sube sola y hay que salir antes de que reviente.
 *
 * <p>El punto de explosion sale de una unica tirada verificable y la curva solo lo
 * representa. Por eso retirarse en cualquier objetivo tiene el mismo valor
 * esperado, propiedad que un test comprueba sobre 400.000 simulaciones.</p>
 */
public final class CrashGame extends AbstractSoloGame {

    private final Map<UUID, CrashRound> rounds = new HashMap<>();

    public CrashGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("crash", "Crash", GameCategory.SOLO, Material.FIREWORK_ROCKET)
                .desc("&7La curva sube y tu decides cuando salir.",
                        "&7Si no te sales a tiempo lo pierdes todo.",
                        "&7Ningun objetivo es mejor que otro.")
                .build());
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        double growth = CrashTable.growthRate(plugin.config().crashDoubleEverySeconds());
        double crashPoint = Math.min(plugin.config().crashMaxMultiplier(),
                CrashTable.crashPoint(plugin.fair().roll(player.getUniqueId()), plugin.config().houseEdge()));
        // La ronda dura exactamente lo que tarda la curva en llegar al estallido.
        int ticks = (int) Math.ceil(CrashTable.secondsToReach(crashPoint, growth) * 20.0) + 1;

        CrashRound round = new CrashRound(player, wager, crashPoint, growth, Math.max(2, ticks));
        rounds.put(player.getUniqueId(), round);
        round.run();
        new CrashGui(plugin, player, this).show();
    }

    CrashRound roundOf(UUID playerId) {
        return rounds.get(playerId);
    }

    /** Retira al jugador en el multiplicador actual. */
    void cashOut(Player player) {
        CrashRound round = rounds.get(player.getUniqueId());
        if (round != null) {
            round.cashOut();
        }
    }

    /** Una partida de crash: sube mientras no estalle. */
    final class CrashRound extends TimedSession {

        private final Wager wager;
        private final double crashPoint;
        private final double growth;
        private double current = 1.0;
        private boolean finished;

        CrashRound(Player player, Wager wager, double crashPoint, double growth, int durationTicks) {
            super(plugin, player, CrashGame.this.id(), durationTicks);
            this.wager = wager;
            this.crashPoint = crashPoint;
            this.growth = growth;
        }

        double bet() {
            return wager.amount();
        }

        boolean finished() {
            return finished;
        }

        private String colour() {
            if (current >= 10) {
                return "&c";
            }
            if (current >= 3) {
                return "&6";
            }
            return current >= 1.5 ? "&e" : "&a";
        }

        @Override
        protected void onFrame(int elapsed, int duration) {
            current = CrashTable.multiplierAt(elapsed / 20.0, growth);
            Player online = player();
            if (online == null) {
                return;
            }
            online.sendActionBar(Text.c("&7Multiplicador " + colour() + Text.multiplier(current)
                    + " &8| &7Retirarse ahora paga &f"
                    + plugin.economy().format(wager.amount() * Math.min(current, crashPoint))));
            if (elapsed % 4 == 0) {
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f,
                        1.0f + (float) Math.min(1.0, elapsed / 100.0));
            }
        }

        void cashOut() {
            if (finished) {
                return;
            }
            finished = true;
            double multiplier = Math.min(current, crashPoint);
            cancel();

            Player online = player();
            if (online == null) {
                refund(wager);
                rounds.remove(playerId());
                return;
            }
            double payout = settle(online, wager, multiplier);
            announceResult(online, true, "&a" + Text.multiplier(multiplier));
            info(online, title());
            info(online, "&7Te retiraste en &f" + Text.multiplier(multiplier)
                    + "&7 y el avion habria estallado en &f" + Text.multiplier(crashPoint));
            showResult(online, wager.amount(), payout);
            sound(online, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            rounds.remove(playerId());
            offerReplay(online);
        }

        @Override
        protected void onFinish() {
            finished = true;
            Player online = player();
            if (online == null) {
                refund(wager);
                rounds.remove(playerId());
                return;
            }
            // Nunca se retiro: el avion explota y se queda sin nada.
            double payout = settle(online, wager, 0);
            announceResult(online, false, "&cestallo en " + Text.multiplier(crashPoint));
            info(online, title());
            info(online, "&7El avion exploto en &f" + Text.multiplier(crashPoint)
                    + "&7 y perdiste &f" + plugin.economy().format(wager.amount()));
            showResult(online, wager.amount(), payout);
            sound(online, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
            rounds.remove(playerId());
            offerReplay(online);
        }

        @Override
        protected void onCancel() {
            if (!finished) {
                // Desconexion o apagado del servidor: se devuelve el dinero.
                finished = true;
                refund(wager);
                rounds.remove(playerId());
            }
        }
    }

    /** Panel de un solo boton: la curva se sigue en la barra de accion. */
    private static final class CrashGui extends Gui {

        private final CrashGame game;

        CrashGui(CasinoPlugin plugin, Player player, CrashGame game) {
            super(plugin, player, 3, "&8Crash &7· &6Sal antes del estallido");
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            CrashRound round = game.roundOf(player().getUniqueId());
            if (round == null || round.finished()) {
                set(13, Items.of(Material.BARRIER).name("&7La ronda ya termino").build());
                set(22, Items.of(Material.ARROW).name("&eCerrar").build(), e -> close());
                return;
            }

            set(4, Items.of(Material.FIREWORK_ROCKET)
                    .name("&6Crash en curso")
                    .lore(
                            "&7Apostado: &f" + plugin.economy().format(round.bet()),
                            "&7La curva se dobla cada &f"
                                    + (int) plugin.config().crashDoubleEverySeconds() + "s&7.",
                            "&7Mira la barra de accion para seguirla.",
                            "",
                            "&cSi no te retiras, pierdes todo.")
                    .glow(true)
                    .build());

            set(13, Items.of(Material.LIME_CONCRETE)
                    .name("&a&lRETIRARSE")
                    .lore("&7Cobra al multiplicador actual.", "", "&ePulsa rapido")
                    .glow(true)
                    .build(), e -> {
                close();
                game.cashOut(player());
            });

            set(22, Items.of(Material.RED_CONCRETE)
                    .name("&cCerrar sin retirarse")
                    .lore("&7La ronda sigue si cierras el menu.")
                    .build());
        }

        @Override
        public String sessionId() {
            return "crash";
        }
    }
}
