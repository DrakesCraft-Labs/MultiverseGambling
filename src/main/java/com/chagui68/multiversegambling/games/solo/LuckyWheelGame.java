package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.PrizeWheel;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Lucky wheel: twelve tiles with different multipliers.
 *
 * <p>The winning tile comes from the provably fair generator. The return is the average
 * of the tiles and is pinned by a test, so editing the wheel in the configuration
 * cannot break the economy unnoticed.</p>
 */
public final class LuckyWheelGame extends AbstractSoloGame {

    public LuckyWheelGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("lucky-wheel", "Lucky Wheel", GameCategory.SOLO, Material.COMPASS)
                .desc("&7Twelve segments, many of them empty",
                        "&7and hits of up to &f4x&7. One spin",
                        "&7decides the prize.")
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
                    online.sendActionBar(Text.c("&7The wheel spins... &f"
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
                        multiplier > 0 ? "&a" + Text.multiplier(multiplier) : "&cno prize");
                info(online, title());
                info(online, "&7The wheel stopped on tile &f" + (winner + 1)
                        + "&7, which pays &f" + Text.multiplier(multiplier));
                showResult(online, wager.amount(), payout);
                sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, payout > wager.amount() ? 1.3f : 0.9f);
                offerReplay(online);
            }
        };
        animation.run();
    }

    private final class WheelGui extends Gui {

        private final LuckyWheelGame game;
        private final Wager wager;
        private boolean armed;

        WheelGui(MultiverseGamblingPlugin plugin, Player player, LuckyWheelGame game, Wager wager) {
            super(plugin, player, 5, "&8" + displayName(player));
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            PrizeWheel wheel = game.wheel();
            set(4, Items.of(Material.COMPASS)
                    .name("&6Bet: &f" + plugin.economy().format(wager.amount()))
                    .lore(
                            "&7Segments: &f" + wheel.size(),
                            "&7Top prize: &f" + Text.multiplier(wheel.best()),
                            "&7Theoretical return: &f" + Text.percent(wheel.rtp()),
                            "&7Chance of the top prize: &f"
                                    + Text.percent(wheel.bestChance()),
                            "",
                            "&7Every tile is equally likely.")
                    .glow(true)
                    .build());

            // The 12 tiles are drawn in two rows of six.
            int[] slots = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33};
            for (int i = 0; i < Math.min(slots.length, wheel.size()); i++) {
                double multiplier = wheel.multiplier(i);
                set(slots[i], Items.of(multiplier == 0 ? Material.GRAY_STAINED_GLASS_PANE
                                : multiplier >= wheel.best() ? Material.GOLD_BLOCK : Material.LIME_STAINED_GLASS_PANE)
                        .name(multiplier == 0 ? "&8No prize" : "&a" + Text.multiplier(multiplier))
                        .lore("&7Probabilidad: &f" + Text.percent(1.0 / wheel.size()),
                                "&7Pago: &f" + plugin.economy().format(wager.amount() * multiplier))
                        .glow(multiplier >= wheel.best())
                        .build());
            }

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name("&a&lSPIN THE WHEEL")
                    .lore("&7You stake &6" + plugin.economy().format(wager.amount()),
                            "&7Everything rides on one spin.", "",
                            "&eClick to spin")
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager);
            });

            set(36, Items.of(Material.BARRIER)
                    .name("&cCancelar")
                    .lore("&7You get your stake back.")
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
            return "lucky-wheel";
        }
    }
}
