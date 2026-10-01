package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.DiceTable;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.CoinFlipShow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Coin flip: the simplest game in the casino, double or nothing.
 */
public final class CoinFlipGame extends AbstractSoloGame {

    private static final String HEADS = "heads";
    private static final String TAILS = "tails";

    public CoinFlipGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("coin-flip", "Coin Flip", GameCategory.SOLO, Material.GOLD_INGOT)
                .desc("&7Pick heads or tails and double your bet.",
                        "&7Quick, clean and no excuses.")
                .build());
    }

    @Override
    public boolean supportsItemBets() {
        return true;
    }

    /** Guessing the side pays the trimmed fair payout; missing it loses the stake. */
    @Override
    public List<com.chagui68.multiversegambling.game.ItemOutcome> itemOutcomes(Player viewer) {
        return List.of(new com.chagui68.multiversegambling.game.ItemOutcome(
                plugin.messages().forSender(viewer, "items.outcome.coin"),
                DiceTable.payout(50.0, plugin.config().houseEdge()), 0.5));
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
        int total = 60;
        UUID playerId = player.getUniqueId();
        // The coin is tossed once, here, from the provably fair generator: the show
        // only paints the side that came up.
        boolean heads = plugin.fair().roll(playerId) < 0.5;

        ArenaStage stage = arenaFor(player);
        if (stage != null) {
            CoinFlipShow show = new CoinFlipShow(plugin, stage, heads, total)
                    .captions(Text.strip(plugin.messages().forSender(player, "panel.coin-flip.heads")),
                            Text.strip(plugin.messages().forSender(player, "panel.coin-flip.tails")));
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
                    settleFlip(playerId, wager, side, heads);
                }

                @Override
                protected void onCancel() {
                    show.cancel();
                    settleFlip(playerId, wager, side, heads);
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
                int wait = 1 + (int) (progress * progress * 8);
                if (elapsed % wait == 0) {
                    String shown = Rng.chance(0.5) ? HEADS : TAILS;
                    online.sendActionBar(Text.c(plugin.messages().forSender(online,
                            "panel.coin-flip.spinning", "side", sideName(online, shown))));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            1.0f + (float) progress);
                }
            }

            @Override
            protected void onFinish() {
                settleFlip(playerId, wager, side, heads);
            }

            @Override
            protected void onCancel() {
                settleFlip(playerId, wager, side, heads);
            }
        }.run();
    }

    /**
     * Pays a toss whose coin was decided when the round started.
     */
    private void settleFlip(UUID playerId, Wager wager, String side, boolean heads) {
        String result = heads ? HEADS : TAILS;
        boolean won = result.equals(side);
        double multiplier = DiceTable.payout(50.0, plugin.config().houseEdge());
        Player online = plugin.getServer().getPlayer(playerId);
        if (online == null) {
            // Gone before the end: the result was already drawn, so it is paid as drawn.
            // Refunding here would let anybody cancel a round they saw coming out badly.
            settleOffline(playerId, wager, multiplier);
            return;
        }
        // Careful: paying 2.0 with a fair coin would give the house a zero edge.
        // The trimmed fair payout is used, exactly like in the dice game.
        double payout = settle(online, wager, won ? multiplier : 0);

        announceResult(online, won, sideName(online, result));
        info(online, title(online));
        message(online, "panel.coin-flip.picked",
                "side", sideName(online, side), "result", sideName(online, result));
        message(online, "panel.coin-flip.odds",
                "multiplier", Text.multiplier(multiplier),
                "edge", Text.percent(plugin.config().houseEdge()));
        showResult(online, wager, payout);
        sound(online, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, won ? 1.3f : 0.9f);
        offerReplay(online);
    }

    /**
     * Display name of a coin side, in the language of the reader.
     */
    String sideName(Player viewer, String side) {
        return plugin.messages().forSender(viewer, "panel.coin-flip.side-" + side);
    }

    private final class SideGui extends Gui {

        private final CoinFlipGame game;
        private final Wager wager;
        private boolean armed;

        SideGui(MultiverseGamblingPlugin plugin, Player player, CoinFlipGame game, Wager wager) {
            super(plugin, player, 3, plugin.messages().forSender(player, "panel.coin-flip.title",
                    "game", game.displayName(player)));
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            frame(Material.YELLOW_STAINED_GLASS_PANE);

            double multiplier = DiceTable.payout(50.0, plugin.config().houseEdge());
            set(4, Items.of(Material.GOLD_INGOT)
                    .name(label(player(), "panel.common.bet",
                            "bet", stakeText(wager, wager.amount())))
                    .lore(label(player(), "panel.coin-flip.info",
                            "multiplier", Text.multiplier(multiplier),
                            "prize", stakeText(wager, wager.amount() * multiplier)))
                    .glow(true)
                    .build());

            set(11, Items.of(Material.GOLD_BLOCK)
                    .name(label(player(), "panel.coin-flip.heads"))
                    .lore(label(player(), "panel.coin-flip.heads-lore"), "",
                            label(player(), "panel.coin-flip.click-heads"))
                    .build(), e -> {
                armed = true;
                close();
                game.flip(player(), wager, HEADS);
            });

            set(15, Items.of(Material.IRON_BLOCK)
                    .name(label(player(), "panel.coin-flip.tails"))
                    .lore(label(player(), "panel.coin-flip.tails-lore"), "",
                            label(player(), "panel.coin-flip.click-tails"))
                    .build(), e -> {
                armed = true;
                close();
                game.flip(player(), wager, TAILS);
            });

            set(22, Items.of(Material.BARRIER)
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
            return "coin-flip";
        }
    }
}
