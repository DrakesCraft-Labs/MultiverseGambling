package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.CrashTable;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.CrashTowerShow;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Crash: the curve climbs on its own and you must cash out before it bursts.
 *
 * <p>The crash point comes from a single provably fair roll and the curve only
 * represents it. That is why cashing out at any target has the same expected value,
 * a property a test checks over 400,000 simulations.</p>
 */
public final class CrashGame extends AbstractSoloGame {

    private final Map<UUID, CrashRound> rounds = new HashMap<>();

    public CrashGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("crash", "Crash", GameCategory.SOLO, Material.FIREWORK_ROCKET)
                .desc("&7The curve climbs and you decide when to cash out.",
                        "&7If you are too slow you lose everything.",
                        "&7No target is better than another.")
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
        // The round lasts exactly as long as the curve takes to reach the crash point.
        int ticks = (int) Math.ceil(CrashTable.secondsToReach(crashPoint, growth) * 20.0) + 1;

        ArenaStage stage = arenaFor(player, ArenaStage.BOARD_PITCH);
        CrashTowerShow show = stage == null ? null : new CrashTowerShow(plugin, stage,
                plugin.config().crashMaxMultiplier(), Math.max(2, ticks));
        if (show != null) {
            show.start();
        }

        CrashRound round = new CrashRound(player, wager, crashPoint, growth, Math.max(2, ticks), show);
        rounds.put(player.getUniqueId(), round);
        round.run();

        if (show == null) {
            new CrashGui(plugin, player, this).show();
            return;
        }
        // The tower is watched in the arena, so the cash out button waits in the chat
        // instead of covering it with a menu.
        player.sendMessage(plugin.messages().componentPlainFor(player, "panel.crash.follow")
                .append(Text.c(" "))
                .append(Text.button(
                        plugin.messages().forSender(player, "panel.crash.cash-out"),
                        "/mvgam action cashout",
                        plugin.messages().forSender(player, "panel.crash.click-fast"))));
    }

    @Override
    public boolean ownsPlayer(UUID playerId) {
        return rounds.containsKey(playerId);
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("cashout".equals(action)) {
            cashOut(player);
            return;
        }
        super.handleAction(player, action, args);
    }

    CrashRound roundOf(UUID playerId) {
        return rounds.get(playerId);
    }

    /**
     * Cashes the player out at the current multiplier.
     */
    void cashOut(Player player) {
        CrashRound round = rounds.get(player.getUniqueId());
        if (round != null) {
            round.cashOut();
        }
    }

    /**
     * A crash round: it climbs while it has not burst.
     */
    final class CrashRound extends TimedSession {

        private final Wager wager;
        private final double crashPoint;
        private final double growth;
        private final CrashTowerShow show;
        private double current = 1.0;
        private boolean finished;

        CrashRound(Player player, Wager wager, double crashPoint, double growth,
                   int durationTicks, CrashTowerShow show) {
            super(plugin, player, CrashGame.this.id(), durationTicks);
            this.wager = wager;
            this.crashPoint = crashPoint;
            this.growth = growth;
            this.show = show;
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
            if (show != null) {
                show.climb(current);
            }
            Player online = player();
            if (online == null) {
                return;
            }
            online.sendActionBar(Text.c(plugin.messages().forSender(online, "panel.crash.bar",
                    "colour", colour(),
                    "multiplier", Text.multiplier(current),
                    "prize", plugin.economy().format(
                            wager.amount() * Math.min(current, crashPoint)))));
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
            if (show != null) {
                show.cashOut();
                show.settle();
            }

            Player online = player();
            if (online == null) {
                refund(wager);
                rounds.remove(playerId());
                return;
            }
            double payout = settle(online, wager, multiplier);
            announceResult(online, true,
                    plugin.messages().forSender(online, "panel.crash.cashed-subtitle",
                            "multiplier", Text.multiplier(multiplier)));
            info(online, title(online));
            message(online, "panel.crash.cashed",
                    "multiplier", Text.multiplier(multiplier),
                    "crash", Text.multiplier(crashPoint));
            showResult(online, wager.amount(), payout);
            sound(online, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            rounds.remove(playerId());
            offerReplay(online);
        }

        @Override
        protected void onFinish() {
            finished = true;
            if (show != null) {
                show.burst();
                show.settle();
            }
            Player online = player();
            if (online == null) {
                refund(wager);
                rounds.remove(playerId());
                return;
            }
            // Never cashed out: the plane bursts and they are left with nothing.
            double payout = settle(online, wager, 0);
            announceResult(online, false,
                    plugin.messages().forSender(online, "panel.crash.burst-subtitle",
                            "multiplier", Text.multiplier(crashPoint)));
            info(online, title(online));
            message(online, "panel.crash.burst",
                    "multiplier", Text.multiplier(crashPoint),
                    "bet", plugin.economy().format(wager.amount()));
            showResult(online, wager.amount(), payout);
            sound(online, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
            rounds.remove(playerId());
            offerReplay(online);
        }

        @Override
        protected void onCancel() {
            if (!finished) {
                // Disconnect or server shutdown: the money is refunded.
                finished = true;
                if (show != null) {
                    show.cancel();
                }
                refund(wager);
                rounds.remove(playerId());
            }
        }
    }

    /**
     * Single button panel: the curve is followed on the action bar.
     */
    private final class CrashGui extends Gui {

        private final CrashGame game;

        CrashGui(MultiverseGamblingPlugin plugin, Player player, CrashGame game) {
            super(plugin, player, 3, plugin.messages().forSender(player, "panel.crash.title",
                    "game", displayName(player)));
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            CrashRound round = game.roundOf(player().getUniqueId());
            if (round == null || round.finished()) {
                set(13, Items.of(Material.BARRIER)
                        .name(label(player(), "panel.crash.over")).build());
                set(22, Items.of(Material.ARROW)
                        .name(label(player(), "panel.crash.close")).build(), e -> close());
                return;
            }

            set(4, Items.of(Material.FIREWORK_ROCKET)
                    .name(label(player(), "panel.crash.in-progress"))
                    .lore(
                            label(player(), "panel.crash.staked",
                                    "bet", plugin.economy().format(round.bet())),
                            label(player(), "panel.crash.doubles",
                                    "seconds", (int) plugin.config().crashDoubleEverySeconds()),
                            label(player(), "panel.crash.follow"),
                            "",
                            label(player(), "panel.crash.warning"))
                    .glow(true)
                    .build());

            set(13, Items.of(Material.LIME_CONCRETE)
                    .name(label(player(), "panel.crash.cash-out"))
                    .lore(label(player(), "panel.crash.collect"), "",
                            label(player(), "panel.crash.click-fast"))
                    .glow(true)
                    .build(), e -> {
                close();
                game.cashOut(player());
            });

            set(22, Items.of(Material.RED_CONCRETE)
                    .name(label(player(), "panel.crash.close-button"))
                    .lore(label(player(), "panel.crash.close-lore"))
                    .build());
        }

        @Override
        public String sessionId() {
            return "crash";
        }
    }
}
