package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.CrashTable;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
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

        CrashRound round = new CrashRound(player, wager, crashPoint, growth, Math.max(2, ticks));
        rounds.put(player.getUniqueId(), round);
        round.run();
        new CrashGui(plugin, player, this).show();
    }

    CrashRound roundOf(UUID playerId) {
        return rounds.get(playerId);
    }

    /** Cashes the player out at the current multiplier. */
    void cashOut(Player player) {
        CrashRound round = rounds.get(player.getUniqueId());
        if (round != null) {
            round.cashOut();
        }
    }

    /** A crash round: it climbs while it has not burst. */
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
            online.sendActionBar(Text.c("&7Multiplier " + colour() + Text.multiplier(current)
                    + " &8| &7Cashing out now pays &f"
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
            info(online, "&7You cashed out at &f" + Text.multiplier(multiplier)
                    + "&7 and the plane would have burst at &f" + Text.multiplier(crashPoint));
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
            // Never cashed out: the plane bursts and they are left with nothing.
            double payout = settle(online, wager, 0);
            announceResult(online, false, "&cburst at " + Text.multiplier(crashPoint));
            info(online, title());
            info(online, "&7The plane burst at &f" + Text.multiplier(crashPoint)
                    + "&7 and you lost &f" + plugin.economy().format(wager.amount()));
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
                refund(wager);
                rounds.remove(playerId());
            }
        }
    }

    /** Single button panel: the curve is followed on the action bar. */
    private final class CrashGui extends Gui {

        private final CrashGame game;

        CrashGui(MultiverseGamblingPlugin plugin, Player player, CrashGame game) {
            super(plugin, player, 3, "&8" + displayName(player) + " &7· &6Cash out before it bursts");
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            CrashRound round = game.roundOf(player().getUniqueId());
            if (round == null || round.finished()) {
                set(13, Items.of(Material.BARRIER).name("&7The round is already over").build());
                set(22, Items.of(Material.ARROW).name("&eClose").build(), e -> close());
                return;
            }

            set(4, Items.of(Material.FIREWORK_ROCKET)
                    .name("&6Crash in progress")
                    .lore(
                            "&7Apostado: &f" + plugin.economy().format(round.bet()),
                            "&7The curve doubles every &f"
                                    + (int) plugin.config().crashDoubleEverySeconds() + "s&7.",
                            "&7Follow it on the action bar.",
                            "",
                            "&cIf you do not cash out, you lose everything.")
                    .glow(true)
                    .build());

            set(13, Items.of(Material.LIME_CONCRETE)
                    .name("&a&lCASH OUT")
                    .lore("&7Collect at the current multiplier.", "", "&eClick fast")
                    .glow(true)
                    .build(), e -> {
                close();
                game.cashOut(player());
            });

            set(22, Items.of(Material.RED_CONCRETE)
                    .name("&cClose without cashing out")
                    .lore("&7The round keeps running if you close the menu.")
                    .build());
        }

        @Override
        public String sessionId() {
            return "crash";
        }
    }
}
