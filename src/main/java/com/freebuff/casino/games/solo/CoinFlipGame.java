package com.freebuff.casino.games.solo;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.DiceTable;
import com.freebuff.casino.engine.Rng;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.game.AbstractSoloGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.session.TimedSession;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Cara o cruz: lo mas simple del casino, doblar o perder. */
public final class CoinFlipGame extends AbstractSoloGame {

    private static final String CARA = "cara";
    private static final String CRUZ = "cruz";

    public CoinFlipGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("cara-o-cruz", "Cara o Cruz", GameCategory.SOLO, Material.GOLD_INGOT)
                .desc("&7Elige cara o cruz y dobla tu apuesta.",
                        "&7Rapido, limpio y sin excusas.")
                .build());
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new SideGui(plugin, player, this, wager).show();
    }

    void flip(Player player, Wager wager, String side) {
        int total = 40;
        TimedSession animation = new TimedSession(plugin, player, id(), total) {

            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                double progress = (double) elapsed / duration;
                int wait = 1 + (int) (progress * progress * 8);
                if (elapsed % wait == 0) {
                    String shown = Rng.chance(0.5) ? CARA : CRUZ;
                    online.sendActionBar(Text.c("&7La moneda gira... &f" + shown));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            1.0f + (float) progress);
                }
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(wager);
                    return;
                }
                boolean heads = plugin.fair().roll(online.getUniqueId()) < 0.5;
                String result = heads ? CARA : CRUZ;
                boolean won = result.equals(side);
                // Ojo: pagar 2.0 con una moneda justa daria ventaja cero a la casa.
                // Se usa el pago justo recortado, igual que en los dados.
                double multiplier = DiceTable.payout(50.0, plugin.config().houseEdge());
                double payout = settle(online, wager, won ? multiplier : 0);

                announceResult(online, won, "&f" + result);
                info(online, title());
                info(online, "&7Elegiste &f" + side + " &7y salio &f" + result);
                info(online, "&7Acertar paga &f" + Text.multiplier(multiplier)
                        + " &7(ventaja de la casa del "
                        + Text.percent(plugin.config().houseEdge()) + "&7).");
                showResult(online, wager.amount(), payout);
                sound(online, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, won ? 1.3f : 0.9f);
                offerReplay(online);
            }
        };
        animation.run();
    }

    private static final class SideGui extends Gui {

        private final CoinFlipGame game;
        private final Wager wager;
        private boolean armed;

        SideGui(CasinoPlugin plugin, Player player, CoinFlipGame game, Wager wager) {
            super(plugin, player, 3, "&8Cara o Cruz &7· &6Elige");
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            double multiplier = DiceTable.payout(50.0, plugin.config().houseEdge());
            set(4, Items.of(Material.GOLD_INGOT)
                    .name("&6Apuesta: &f" + plugin.economy().format(wager.amount()))
                    .lore("&7Acertar paga &f" + Text.multiplier(multiplier) + "&7, o sea &f"
                            + plugin.economy().format(wager.amount() * multiplier))
                    .glow(true)
                    .build());

            set(11, Items.of(Material.GOLD_BLOCK)
                    .name("&6CARA")
                    .lore("&7La cara del sol.", "", "&ePulsa para apostar a cara")
                    .build(), e -> {
                armed = true;
                close();
                game.flip(player(), wager, CARA);
            });

            set(15, Items.of(Material.IRON_BLOCK)
                    .name("&7CRUZ")
                    .lore("&7El sello real.", "", "&ePulsa para apostar a cruz")
                    .build(), e -> {
                armed = true;
                close();
                game.flip(player(), wager, CRUZ);
            });

            set(22, Items.of(Material.BARRIER)
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
            return "cara-o-cruz";
        }
    }
}
