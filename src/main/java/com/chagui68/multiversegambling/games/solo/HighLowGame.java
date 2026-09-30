package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Card;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * High or Low.
 *
 * <p>The payout of each step is not fixed: it comes from the ranks that are really
 * left above or below, trimmed by the house edge. That way going lower with a two pays
 * far more than with a king, and the game cannot be exploited by always picking the
 * easy side.</p>
 */
public final class HighLowGame extends AbstractSoloGame {

    private static final int MIN_RANK = 2;
    private static final int MAX_RANK = Card.ACE;

    public HighLowGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("high-low", "High or Low", GameCategory.SOLO, Material.PAPER)
                .desc("&7Guess whether the next card is higher",
                        "&7or lower. Chain hits to multiply",
                        "&7and cash out whenever you want.")
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

    /** Multiplier of one step, according to how many favourable ranks are left. */
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

    private final class HighLowGui extends Gui {

        private final HighLowGame game;
        private final Wager wager;
        private int currentRank;
        private double chain = 1.0;
        private int steps;
        private boolean resolved;

        HighLowGui(MultiverseGamblingPlugin plugin, Player player, HighLowGame game, Wager wager, int rank) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.high-low.title",
                    "game", displayName(player)));
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
                    .name(label(player(), "panel.high-low.current",
                            "card", rankLabel(currentRank)))
                    .lore(
                            label(player(), "panel.common.bet",
                                    "bet", plugin.economy().format(wager.amount())),
                            label(player(), "panel.high-low.chain",
                                    "multiplier", Text.multiplier(chain)),
                            label(player(), "panel.high-low.steps",
                                    "steps", steps, "max", plugin.config().highLowMaxChain()),
                            steps > 0
                                    ? label(player(), "panel.high-low.cash-pays", "prize",
                                            plugin.economy().format(wager.amount() * chain))
                                    : label(player(), "panel.high-low.no-hits"))
                    .glow(true)
                    .build());

            set(21, Items.of(Material.LIME_CONCRETE)
                    .name(label(player(), "panel.high-low.higher"))
                    .lore(label(player(), "panel.high-low.higher-lore"),
                            label(player(), "panel.high-low.pays-if-right",
                                    "multiplier", Text.multiplier(higher)),
                            label(player(), "panel.high-low.favourable",
                                    "count", rankCount(currentRank, true)),
                            "", label(player(), "panel.high-low.click-higher"))
                    .build(), e -> guess(true));

            set(23, Items.of(Material.RED_CONCRETE)
                    .name(label(player(), "panel.high-low.lower"))
                    .lore(label(player(), "panel.high-low.lower-lore"),
                            label(player(), "panel.high-low.pays-if-right",
                                    "multiplier", Text.multiplier(lower)),
                            label(player(), "panel.high-low.favourable",
                                    "count", rankCount(currentRank, false)),
                            "", label(player(), "panel.high-low.click-lower"))
                    .build(), e -> guess(false));

            set(40, Items.of(steps > 0 && !resolved ? Material.GOLD_BLOCK : Material.GRAY_DYE)
                    .name(steps > 0 ? label(player(), "panel.high-low.cash-out")
                            : label(player(), "panel.high-low.nothing"))
                    .lore(steps > 0
                            ? label(player(), "panel.high-low.cash-lore", "prize",
                                    plugin.economy().format(wager.amount() * chain))
                            : label(player(), "panel.high-low.cash-first"))
                    .glow(steps > 0 && !resolved)
                    .build(), e -> cashOut());

            set(36, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(resolved ? label(player(), "panel.common.round-finished")
                            : label(player(), "panel.high-low.close-lore"))
                    .build(), e -> close());

            if (maxSteps) {
                set(44, Items.of(Material.NETHER_STAR)
                        .name(label(player(), "panel.high-low.max-chain"))
                        .lore(label(player(), "panel.high-low.max-lore"),
                                label(player(), "panel.high-low.max-collect"))
                        .build());
            }
        }

        private void guess(boolean higher) {
            if (resolved) {
                return;
            }
            double multiplier = game.stepMultiplier(currentRank, higher, plugin.config().houseEdge());
            int next = game.nextRank(player());
            // A tie does not break the chain: the card is drawn again.
            if (next == currentRank) {
                game.sound(player(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.0f);
                game.message(player(), "panel.high-low.tie");
                return;
            }
            boolean won = higher ? next > currentRank : next < currentRank;
            if (!won) {
                resolved = true;
                double payout = game.settle(player(), wager, 0);
                render();
                game.announceResult(player(), false, "&c" + rankLabel(next));
                game.info(player(), game.title(player()));
                game.message(player(), "panel.high-low.lost", "card", rankLabel(next),
                        "multiplier", Text.multiplier(chain));
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
            game.announceResult(player(), true,
                    plugin.messages().forSender(player(), "panel.high-low.cashed-subtitle",
                            "multiplier", Text.multiplier(chain)));
            game.info(player(), game.title(player()));
            game.message(player(), "panel.high-low.cashed",
                    "steps", steps, "multiplier", Text.multiplier(chain));
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
            // With a chain of hits, closing cashes out automatically: it would be absurd
            // to give the stake away to somebody who has already won.
            resolved = true;
            double payout = game.settle(player(), wager, chain);
            game.message(player(), "panel.high-low.closed",
                    "multiplier", Text.multiplier(chain),
                    "prize", plugin.economy().format(payout));
        }

        @Override
        public String sessionId() {
            return "high-low";
        }
    }
}
