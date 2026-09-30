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
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.WheelShow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        UUID playerId = player.getUniqueId();

        ArenaStage stage = arenaFor(player);
        if (stage != null) {
            WheelShow show = new WheelShow(plugin, stage, sectors(wheel), winner, total);
            new TimedSession(plugin, player, id(), total) {

                @Override
                protected void onStart() {
                    show.start();
                }

                @Override
                protected void onFrame(int elapsed, int duration) {
                    show.tick();
                }

                @Override
                protected void onFinish() {
                    show.settle();
                    settleSpin(playerId, wager, winner);
                }

                @Override
                protected void onCancel() {
                    show.cancel();
                    refund(wager);
                }
            }.run();
            return;
        }

        // No arena to paint on: the action bar keeps the suspense.
        new TimedSession(plugin, player, id(), total) {

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
                    online.sendActionBar(Text.c(plugin.messages().forSender(online,
                            "panel.lucky-wheel.spinning",
                            "multiplier", Text.multiplier(wheel.multiplier(filler)))));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + (float) progress * 0.9f);
                }
            }

            @Override
            protected void onFinish() {
                settleSpin(playerId, wager, winner);
            }

            @Override
            protected void onCancel() {
                refund(wager);
            }
        }.run();
    }

    /**
     * Pays a spin whose winning tile was drawn when the round started.
     */
    private void settleSpin(UUID playerId, Wager wager, int winner) {
        Player online = plugin.getServer().getPlayer(playerId);
        if (online == null) {
            refund(wager);
            return;
        }
        double multiplier = wheel().multiplier(winner);
        double payout = settle(online, wager, multiplier);
        announceResult(online, payout > wager.amount(),
                multiplier > 0
                        ? plugin.messages().forSender(online, "panel.lucky-wheel.tile",
                        "multiplier", Text.multiplier(multiplier))
                        : plugin.messages().forSender(online, "panel.common.no-prize"));
        info(online, title(online));
        message(online, "panel.lucky-wheel.result",
                "tile", winner + 1, "multiplier", Text.multiplier(multiplier));
        showResult(online, wager.amount(), payout);
        sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, payout > wager.amount() ? 1.3f : 0.9f);
        offerReplay(online);
    }

    /**
     * Colour of each tile: grey when it pays nothing, gold for the top prize.
     */
    static List<Material> sectors(PrizeWheel wheel) {
        List<Material> sectors = new ArrayList<>(wheel.size());
        for (int index = 0; index < wheel.size(); index++) {
            double multiplier = wheel.multiplier(index);
            sectors.add(multiplier <= 0 ? Material.GRAY_CONCRETE
                    : multiplier >= wheel.best() ? Material.GOLD_BLOCK : Material.LIME_CONCRETE);
        }
        return sectors;
    }

    private final class WheelGui extends Gui {

        private final LuckyWheelGame game;
        private final Wager wager;
        private boolean armed;

        WheelGui(MultiverseGamblingPlugin plugin, Player player, LuckyWheelGame game, Wager wager) {
            super(plugin, player, 5, plugin.messages().forSender(player,
                    "panel.lucky-wheel.title", "game", displayName(player)));
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            PrizeWheel wheel = game.wheel();
            set(4, Items.of(Material.COMPASS)
                    .name(label(player(), "panel.common.bet",
                            "bet", plugin.economy().format(wager.amount())))
                    .lore(
                            label(player(), "panel.lucky-wheel.segments", "count", wheel.size()),
                            label(player(), "panel.lucky-wheel.top-prize",
                                    "multiplier", Text.multiplier(wheel.best())),
                            label(player(), "panel.lucky-wheel.rtp",
                                    "percent", Text.percent(wheel.rtp())),
                            label(player(), "panel.lucky-wheel.top-chance",
                                    "percent", Text.percent(wheel.bestChance())),
                            "",
                            label(player(), "panel.lucky-wheel.equally-likely"))
                    .glow(true)
                    .build());

            // The 12 tiles are drawn in two rows of six.
            int[] slots = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33};
            for (int i = 0; i < Math.min(slots.length, wheel.size()); i++) {
                double multiplier = wheel.multiplier(i);
                set(slots[i], Items.of(multiplier == 0 ? Material.GRAY_STAINED_GLASS_PANE
                                : multiplier >= wheel.best() ? Material.GOLD_BLOCK : Material.LIME_STAINED_GLASS_PANE)
                        .name(multiplier == 0
                                ? label(player(), "panel.lucky-wheel.no-prize")
                                : label(player(), "panel.lucky-wheel.tile",
                                "multiplier", Text.multiplier(multiplier)))
                        .lore(label(player(), "panel.lucky-wheel.chance",
                                        "percent", Text.percent(1.0 / wheel.size())),
                                label(player(), "panel.lucky-wheel.pays",
                                        "prize", plugin.economy().format(wager.amount() * multiplier)))
                        .glow(multiplier >= wheel.best())
                        .build());
            }

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name(label(player(), "panel.lucky-wheel.spin"))
                    .lore(label(player(), "panel.lucky-wheel.will-stake",
                                    "bet", plugin.economy().format(wager.amount())),
                            label(player(), "panel.lucky-wheel.all-in"), "",
                            label(player(), "panel.common.click-to-spin"))
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager);
            });

            set(36, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.cancel"))
                    .lore(label(player(), "panel.common.refund-lore"))
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
