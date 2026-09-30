package com.freebuff.casino.games.solo;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.PrizeWheel;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.game.AbstractSoloGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.session.TimedSession;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Ruleta de la suerte: doce casillas con multiplicadores distintos.
 *
 * <p>La casilla ganadora sale del generador verificable. El retorno es la media de
 * las casillas y esta fijado por un test, asi que editar la rueda en la
 * configuracion no puede romper la economia sin que se note.</p>
 */
public final class LuckyWheelGame extends AbstractSoloGame {

    public LuckyWheelGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("ruleta-suerte", "Ruleta de la Suerte", GameCategory.SOLO, Material.COMPASS)
                .desc("&7Doce casillas, muchas vacias y",
                        "&7golpes de hasta &f4x&7. Una vuelta",
                        "&7decide el premio.")
                .build());
    }

    PrizeWheel wheel() {
        List<Double> configured = plugin.config().luckyWheelSegments();
        if (configured == null || configured.size() < 2) {
            return PrizeWheel.defaultWheel();
        }
        double[] pockets = new double[configured.size()];
        for (int i = 0; i < pockets.length; i++) {
            pockets[i] = Math.max(0, configured.get(i));
        }
        return new PrizeWheel(pockets);
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new WheelGui(plugin, player, this, wager).show();
    }

    void spin(Player player, Wager wager) {
        PrizeWheel wheel = wheel();
        int winner = wheel.spin(() -> plugin.fair().roll(player.getUniqueId()));
        int total = 60;

        TimedSession animation = new TimedSession(plugin, player, id(), total) {

            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                double progress = (double) elapsed / duration;
                int wait = 1 + (int) (progress * progress * 10);
                if (elapsed % wait == 0) {
                    int filler = (int) (elapsed * 1.7) % wheel.size();
                    online.sendActionBar(Text.c("&7La ruleta gira... &f"
                            + Text.multiplier(wheel.multiplier(filler))));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + (float) progress * 0.9f);
                }
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(wager);
                    return;
                }
                double multiplier = wheel.multiplier(winner);
                double payout = settle(online, wager, multiplier);
                announceResult(online, payout > wager.amount(),
                        multiplier > 0 ? "&a" + Text.multiplier(multiplier) : "&csin premio");
                info(online, title());
                info(online, "&7La ruleta se paro en la casilla &f" + (winner + 1)
                        + "&7, que paga &f" + Text.multiplier(multiplier));
                showResult(online, wager.amount(), payout);
                sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, payout > wager.amount() ? 1.3f : 0.9f);
                offerReplay(online);
            }
        };
        animation.run();
    }

    private static final class WheelGui extends Gui {

        private final LuckyWheelGame game;
        private final Wager wager;
        private boolean armed;

        WheelGui(CasinoPlugin plugin, Player player, LuckyWheelGame game, Wager wager) {
            super(plugin, player, 5, "&8Ruleta de la Suerte");
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            PrizeWheel wheel = game.wheel();
            set(4, Items.of(Material.COMPASS)
                    .name("&6Apuesta: &f" + plugin.economy().format(wager.amount()))
                    .lore(
                            "&7Casillas: &f" + wheel.size(),
                            "&7Premio maximo: &f" + Text.multiplier(wheel.best()),
                            "&7Retorno teorico: &f" + Text.percent(wheel.rtp()),
                            "&7Probabilidad del premio maximo: &f"
                                    + Text.percent(wheel.bestChance()),
                            "",
                            "&7Todas las casillas son igual de probables.")
                    .glow(true)
                    .build());

            // Las 12 casillas se dibujan en dos filas de seis.
            int[] slots = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33};
            for (int i = 0; i < Math.min(slots.length, wheel.size()); i++) {
                double multiplier = wheel.multiplier(i);
                set(slots[i], Items.of(multiplier == 0 ? Material.GRAY_STAINED_GLASS_PANE
                                : multiplier >= wheel.best() ? Material.GOLD_BLOCK : Material.LIME_STAINED_GLASS_PANE)
                        .name(multiplier == 0 ? "&8Sin premio" : "&a" + Text.multiplier(multiplier))
                        .lore("&7Probabilidad: &f" + Text.percent(1.0 / wheel.size()),
                                "&7Pago: &f" + plugin.economy().format(wager.amount() * multiplier))
                        .glow(multiplier >= wheel.best())
                        .build());
            }

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name("&a&lGIRAR LA RULETA")
                    .lore("&7Apuestas &6" + plugin.economy().format(wager.amount()),
                            "&7Se juega todo a una vuelta.", "",
                            "&ePulsa para girar")
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager);
            });

            set(36, Items.of(Material.BARRIER)
                    .name("&cCancelar")
                    .lore("&7Recuperas tu apuesta.")
                    .build(), e -> close());
        }

        @Override
        protected void onClose() {
            if (!armed) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "ruleta-suerte";
        }
    }
}
