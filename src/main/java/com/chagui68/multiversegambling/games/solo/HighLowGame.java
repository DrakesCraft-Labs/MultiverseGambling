package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.engine.Card;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.LiveRound;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.CardTableShow;
import com.chagui68.multiversegambling.world.anim.HoloButton;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * High or Low.
 *
 * <p>The payout of each step is not fixed: it comes from the ranks that are really
 * left above or below, trimmed by the house edge. That way going lower with a three pays
 * far more than with a king, and the game cannot be exploited by always picking the
 * easy side. A side with no rank left (lower than a two, higher than an ace) cannot be
 * picked at all. Every card is drawn by the provably fair generator; a tie is drawn
 * again and never breaks the chain.</p>
 *
 * <p>In the casino world the cards are dealt on the table of the pavilion and the guesses
 * are floating buttons; anywhere else it is played in a menu.</p>
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

    /**
     * Multiplier of one step, according to how many favourable ranks are left; 0 when
     * that side cannot win at all.
     */
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
        Chain chain = new Chain(player, wager, nextRank(player));
        ArenaStage stage = arenaFor(player);
        if (stage != null) {
            chain.show = new CardTableShow(plugin, stage,
                    Text.strip(plugin.messages().forSender(player, "panel.high-low.table-history")),
                    Text.strip(plugin.messages().forSender(player, "panel.high-low.table-current")),
                    20 * 60 * 10);
            chain.show.start();
            chain.arena = new ArenaChain(player, chain, stage);
            chain.arena.begin();
            chain.paint();
            message(player, "panel.high-low.arena-hint");
            return;
        }
        new HighLowGui(plugin, player, this, chain).show();
    }

    /**
     * The next card, drawn by the provably fair generator like every other result.
     */
    int nextRank(Player player) {
        return MIN_RANK + plugin.fair().rollInt(player.getUniqueId(), MAX_RANK - MIN_RANK + 1);
    }

    static String rankLabel(int rank) {
        return switch (rank) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(rank);
        };
    }

    // -------------------------------------------------------------------- chain

    /**
     * One run of guesses, shared by the menu and the card table.
     */
    private final class Chain {

        private final Player owner;
        private final Wager wager;
        private final List<Card> history = new ArrayList<>();
        private int currentRank;
        private double multiplier = 1.0;
        private int steps;
        private boolean resolved;
        private CardTableShow show;
        private ArenaChain arena;
        private HighLowGui gui;

        Chain(Player owner, Wager wager, int firstRank) {
            this.owner = owner;
            this.wager = wager;
            this.currentRank = firstRank;
        }

        double step(boolean higher) {
            return stepMultiplier(currentRank, higher, plugin.config().houseEdge());
        }

        /**
         * A card for the table: the game only deals ranks, so the suit is just paint.
         */
        Card card(int rank, int position) {
            return Card.of(Card.Suit.values()[Math.floorMod(rank * 3 + position, 4)], rank);
        }

        void paint() {
            if (show != null) {
                show.deal(history, false, "", List.of(card(currentRank, history.size())),
                        Text.multiplier(multiplier));
            }
            if (arena != null) {
                arena.controls();
            }
            if (gui != null) {
                gui.refresh();
            }
        }

        void guess(boolean higher) {
            if (resolved) {
                return;
            }
            double step = step(higher);
            if (step <= 0) {
                // No rank left on that side: the button is off, but a stray click must
                // never be taken as a lost bet.
                return;
            }
            int next = nextRank(owner);
            // A tie does not break the chain: the card is drawn again.
            if (next == currentRank) {
                sound(owner, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.0f);
                message(owner, "panel.high-low.tie");
                return;
            }
            boolean won = higher ? next > currentRank : next < currentRank;
            history.add(card(currentRank, history.size()));
            if (!won) {
                resolved = true;
                if (show != null) {
                    show.deal(history, false, "", List.of(card(next, history.size())), Text.multiplier(0));
                    show.result(plugin.messages().forSender(owner, "panel.common.table-lost"), false);
                    show.settle();
                }
                close();
                double payout = settle(owner, wager, 0);
                announceResult(owner, false, "&c" + rankLabel(next));
                info(owner, title(owner));
                message(owner, "panel.high-low.lost", "card", rankLabel(next),
                        "multiplier", Text.multiplier(multiplier));
                showResult(owner, wager.amount(), payout);
                sound(owner, Sound.ENTITY_VILLAGER_NO, 0.9f, 0.9f);
                offerReplay(owner);
                if (gui != null) {
                    gui.refresh();
                }
                return;
            }
            multiplier *= step;
            steps++;
            currentRank = next;
            sound(owner, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f + steps * 0.1f);
            if (steps >= plugin.config().highLowMaxChain()) {
                paint();
                cashOut();
                return;
            }
            paint();
        }

        void cashOut() {
            if (resolved || steps == 0) {
                return;
            }
            resolved = true;
            if (show != null) {
                show.result(plugin.messages().forSender(owner, "panel.common.table-won"), true);
                show.settle();
            }
            close();
            double payout = settle(owner, wager, multiplier);
            announceResult(owner, true, plugin.messages().forSender(owner, "panel.high-low.cashed-subtitle",
                    "multiplier", Text.multiplier(multiplier)));
            info(owner, title(owner));
            message(owner, "panel.high-low.cashed", "steps", steps, "multiplier", Text.multiplier(multiplier));
            showResult(owner, wager.amount(), payout);
            sound(owner, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            offerReplay(owner);
            if (gui != null) {
                gui.refresh();
            }
        }

        /**
         * The run was left open: a chain with hits is cashed out, an untouched one
         * refunded.
         */
        void leave(boolean online) {
            if (resolved) {
                return;
            }
            resolved = true;
            close();
            if (steps == 0) {
                refund(wager);
                if (show != null) {
                    show.cancel();
                }
                return;
            }
            if (show != null) {
                show.result(plugin.messages().forSender(owner, "panel.common.table-won"), true);
                show.settle();
            }
            if (online) {
                double payout = settle(owner, wager, multiplier);
                message(owner, "panel.high-low.closed", "multiplier", Text.multiplier(multiplier),
                        "prize", plugin.economy().format(payout));
            } else {
                settleOffline(owner.getUniqueId(), wager, multiplier);
            }
        }

        private void close() {
            if (arena != null) {
                arena.close();
            }
        }
    }

    // ------------------------------------------------------------------- arena

    /**
     * The run played at the card table, with three floating buttons: higher, lower and
     * cash out, each one telling what it pays.
     */
    private final class ArenaChain extends LiveRound {

        private final Chain chain;
        private HoloButton higher;
        private HoloButton lower;
        private HoloButton cash;

        ArenaChain(Player player, Chain chain, ArenaStage stage) {
            super(plugin, player, id(), stage);
            this.chain = chain;
        }

        void controls() {
            Player player = player();
            if (player == null || over() || chain.resolved) {
                return;
            }
            double up = chain.step(true);
            double down = chain.step(false);
            if (higher == null) {
                // Created with their widest text, so the hitbox fits whatever they say later.
                List<HoloButton> row = chain.show.addControlRow(0.75, player.getUniqueId(), List.of(
                        new HoloButton.Spec(label(player, "panel.high-low.button-higher",
                                "multiplier", Text.multiplier(99.99)),
                                HoloButton.GREEN, 1.3f, clicker -> chain.guess(true)),
                        new HoloButton.Spec(label(player, "panel.high-low.button-lower",
                                "multiplier", Text.multiplier(99.99)),
                                HoloButton.RED, 1.3f, clicker -> chain.guess(false)),
                        new HoloButton.Spec(label(player, "panel.high-low.button-cash",
                                "multiplier", Text.multiplier(99.99),
                                "prize", plugin.economy().format(chain.wager.amount() * 20)),
                                HoloButton.GOLD, 1.3f, clicker -> chain.cashOut())));
                higher = row.get(0);
                lower = row.get(1);
                cash = row.get(2);
            }
            higher.text(label(player, "panel.high-low.button-higher", "multiplier", Text.multiplier(up)));
            higher.enabled(up > 0);
            lower.text(label(player, "panel.high-low.button-lower", "multiplier", Text.multiplier(down)));
            lower.enabled(down > 0);
            cash.text(chain.steps == 0
                    ? label(player, "panel.high-low.button-no-cash")
                    : label(player, "panel.high-low.button-cash", "multiplier", Text.multiplier(chain.multiplier),
                    "prize", plugin.economy().format(chain.wager.amount() * chain.multiplier)));
            cash.enabled(chain.steps > 0);
        }

        private Component label(Player viewer, String key, Object... replacements) {
            return Text.c(plugin.messages().forSender(viewer, key, replacements));
        }

        void close() {
            finish();
            for (HoloButton button : new HoloButton[]{higher, lower, cash}) {
                if (button != null) {
                    button.remove();
                }
            }
        }

        @Override
        protected void onAbandon(Player player) {
            chain.leave(true);
        }

        @Override
        protected void onCancel() {
            chain.leave(false);
        }
    }

    // -------------------------------------------------------------------- menu

    private final class HighLowGui extends Gui {

        private final HighLowGame game;
        private final Chain chain;

        HighLowGui(MultiverseGamblingPlugin plugin, Player player, HighLowGame game, Chain chain) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.high-low.title",
                    "game", displayName(player)));
            this.game = game;
            this.chain = chain;
            chain.gui = this;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
            border(Items.of(Material.GREEN_STAINED_GLASS_PANE).name(" ").build());

            double higher = chain.step(true);
            double lower = chain.step(false);
            boolean maxSteps = chain.steps >= plugin.config().highLowMaxChain();

            set(13, Items.of(Material.PAPER)
                    .name(label(player(), "panel.high-low.current", "card", rankLabel(chain.currentRank)))
                    .lore(
                            label(player(), "panel.common.bet", "bet", plugin.economy().format(chain.wager.amount())),
                            label(player(), "panel.high-low.chain", "multiplier", Text.multiplier(chain.multiplier)),
                            label(player(), "panel.high-low.steps",
                                    "steps", chain.steps, "max", plugin.config().highLowMaxChain()),
                            chain.steps > 0
                                    ? label(player(), "panel.high-low.cash-pays", "prize",
                                    plugin.economy().format(chain.wager.amount() * chain.multiplier))
                                    : label(player(), "panel.high-low.no-hits"))
                    .glow(true)
                    .build());

            if (!chain.resolved) {
                set(20, Items.of(higher > 0 ? Material.LIME_CONCRETE : Material.GRAY_DYE)
                        .name(label(player(), "panel.high-low.higher"))
                        .lore(label(player(), "panel.high-low.higher-lore"),
                                higher > 0 ? label(player(), "panel.high-low.pays-if-right",
                                        "multiplier", Text.multiplier(higher))
                                        : label(player(), "panel.high-low.impossible"),
                                label(player(), "panel.high-low.favourable",
                                        "count", rankCount(chain.currentRank, true)),
                                "", label(player(), "panel.high-low.click-higher"))
                        .build(), e -> chain.guess(true));

                set(24, Items.of(lower > 0 ? Material.RED_CONCRETE : Material.GRAY_DYE)
                        .name(label(player(), "panel.high-low.lower"))
                        .lore(label(player(), "panel.high-low.lower-lore"),
                                lower > 0 ? label(player(), "panel.high-low.pays-if-right",
                                        "multiplier", Text.multiplier(lower))
                                        : label(player(), "panel.high-low.impossible"),
                                label(player(), "panel.high-low.favourable",
                                        "count", rankCount(chain.currentRank, false)),
                                "", label(player(), "panel.high-low.click-lower"))
                        .build(), e -> chain.guess(false));

                set(31, Items.of(chain.steps > 0 ? Material.GOLD_BLOCK : Material.GRAY_DYE)
                        .name(chain.steps > 0 ? label(player(), "panel.high-low.cash-out")
                                : label(player(), "panel.high-low.nothing"))
                        .lore(chain.steps > 0
                                ? label(player(), "panel.high-low.cash-lore", "prize",
                                plugin.economy().format(chain.wager.amount() * chain.multiplier))
                                : label(player(), "panel.high-low.cash-first"))
                        .glow(chain.steps > 0)
                        .build(), e -> chain.cashOut());
            }

            set(40, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(chain.resolved ? label(player(), "panel.common.round-finished")
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

        @Override
        protected void onClose() {
            chain.gui = null;
            chain.leave(player().isOnline());
        }

        @Override
        public String sessionId() {
            return "high-low";
        }
    }
}
