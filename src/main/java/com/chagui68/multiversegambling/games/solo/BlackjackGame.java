package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.BlackjackHand;
import com.chagui68.multiversegambling.engine.Card;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Blackjack against the dealer.
 *
 * <p>Full rules: a natural pays 3:2, a push returns the stake, doubling is allowed and
 * the dealer hits up to 17 (configurable to hit on a soft 17). The exact rule lives in
 * {@link BlackjackHand}, which has its own tests.</p>
 */
public final class BlackjackGame extends AbstractSoloGame {

    private static final int CARD_SLOTS_PLAYER = 29;
    private static final int CARD_SLOTS_DEALER = 11;

    private final java.util.Map<UUID, Table> tables = new java.util.HashMap<>();

    public BlackjackGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("blackjack", "Blackjack", GameCategory.SOLO, Material.BOOK)
                .desc("&7Reach 21 without busting and beat",
                        "&7the dealer. A natural pays &f3:2&7.",
                        "&7Hit, stand or double down.")
                .build());
    }

    Table tableOf(UUID playerId) {
        return tables.get(playerId);
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        Table table = new Table(player, wager);
        tables.put(player.getUniqueId(), table);
        new BlackjackGui(plugin, player, this).show();
        // Initial deal, with a bit of suspense.
        dealOpening(player, table);
    }

    private void dealOpening(Player player, Table table) {
        TimedSession animation = new TimedSession(plugin, player, id(), 8) {
            @Override
            protected void onFrame(int elapsed, int duration) {
                sound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.0f + elapsed * 0.05f);
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(table.wager);
                    tables.remove(playerId());
                    return;
                }
                table.playerCards.add(table.deck.draw());
                table.dealerCards.add(table.deck.draw());
                table.playerCards.add(table.deck.draw());
                table.dealerCards.add(table.deck.draw());

                if (BlackjackHand.isBlackjack(table.dealerCards)
                        || BlackjackHand.isBlackjack(table.playerCards)) {
                    finish(online, table);
                    return;
                }
                online.sendActionBar(Text.c("&7Your hand: &f"
                        + BlackjackHand.value(table.playerCards) + " &8| &7Dealer: &f"
                        + table.dealerCards.get(0).display() + " &7? ?"));
                table.gui().ifPresent(Gui::refresh);
            }
        };
        animation.run();
    }

    /** Hits for the player. */
    void hit(Player player, Table table) {
        if (table.settled) {
            return;
        }
        table.hits++;
        table.playerCards.add(table.deck.draw());
        sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.3f);
        if (BlackjackHand.isBust(table.playerCards)) {
            finish(player, table);
            return;
        }
        table.gui().ifPresent(Gui::refresh);
    }

    /** Stands: the dealer plays. */
    void stand(Player player, Table table) {
        if (table.settled) {
            return;
        }
        finish(player, table);
    }

    /** Doubles down: takes another equal stake and deals one more card. */
    void doubleDown(Player player, Table table) {
        if (table.settled || table.hits > 0 || table.playerCards.size() != 2) {
            return;
        }
        Wager extra = stake(player, table.wager.amount());
        if (extra == null) {
            return;
        }
        table.extra = extra;
        table.doubled = true;
        table.playerCards.add(table.deck.draw());
        sound(player, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
        finish(player, table);
    }

    /** Plays the dealer and settles the hand. */
    private void finish(Player player, Table table) {
        if (table.settled) {
            return;
        }
        table.settled = true;
        boolean hitSoft17 = plugin.config().blackjackHitSoft17();
        boolean playerBlackjack = BlackjackHand.isBlackjack(table.playerCards);
        boolean dealerBlackjack = BlackjackHand.isBlackjack(table.dealerCards);

        if (!playerBlackjack && !BlackjackHand.isBust(table.playerCards)) {
            while (BlackjackHand.dealerMustHit(table.dealerCards, hitSoft17)) {
                table.dealerCards.add(table.deck.draw());
            }
        }
        boolean dealerBust = BlackjackHand.isBust(table.dealerCards);
        double multiplier = BlackjackHand.payout(table.playerCards, table.dealerCards,
                playerBlackjack, dealerBlackjack, dealerBust);
        if (table.doubled) {
            multiplier *= 2;
        }

        double payout = settle(player, table.wager, multiplier);
        if (table.extra != null) {
            // The second half of the double is paid or lost with the same result.
            if (multiplier > 0) {
                table.extra.payAbsolute(table.extra.amount() * multiplier);
            } else {
                table.extra.lose();
            }
        }
        tables.remove(player.getUniqueId());

        boolean won = payout > table.wager.amount() * (table.doubled ? 2 : 1);
        announceResult(player, won, payout > 0 ? "&a" + plugin.economy().format(payout) : "&cno prize");
        info(player, title());
        info(player, "&7Your hand: &f" + BlackjackHand.describe(table.playerCards)
                + " &7= &f" + BlackjackHand.value(table.playerCards));
        info(player, "&7Dealer: &f" + BlackjackHand.describe(table.dealerCards)
                + " &7= &f" + BlackjackHand.value(table.dealerCards));
        if (playerBlackjack && !dealerBlackjack) {
            info(player, "&6Natural blackjack: pays 3 to 2.");
        } else if (dealerBust) {
            info(player, "&aThe dealer busted.");
        }
        showResult(player, table.wager.amount() * (table.doubled ? 2 : 1), payout);
        sound(player, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, won ? 1.3f : 0.9f);
        table.gui().ifPresent(Gui::refresh);
        offerReplay(player);
    }

    /** State of one blackjack table. */
    final class Table {

        private final Card.Deck deck = new Card.Deck(BlackjackGame.this.plugin.config().blackjackDecks());
        private final List<Card> playerCards = new ArrayList<>();
        private final List<Card> dealerCards = new ArrayList<>();
        private final Wager wager;
        private Wager extra;
        private int hits;
        private boolean doubled;
        private boolean settled;
        private BlackjackGui gui;

        Table(Player player, Wager wager) {
            this.wager = wager;
        }

        double bet() {
            return wager.amount() * (doubled ? 2 : 1);
        }

        java.util.Optional<Gui> gui() {
            return java.util.Optional.ofNullable(gui);
        }

        void bind(BlackjackGui bound) {
            this.gui = bound;
        }

        boolean canDouble(Player player) {
            return !settled && !doubled && dealerCards.size() == 2 && playerCards.size() == 2
                    && BlackjackHand.value(playerCards) < 21
                    && BlackjackGame.this.plugin.economy().has(player.getUniqueId(), wager.amount());
        }

        boolean isSettled() {
            return settled;
        }

        List<Card> playerCards() {
            return playerCards;
        }

        List<Card> dealerCards() {
            return dealerCards;
        }
    }

    /** Table: cards on top and actions below. */
    private final class BlackjackGui extends Gui {

        private final BlackjackGame game;
        private final BlackjackGame.Table table;

        BlackjackGui(MultiverseGamblingPlugin plugin, Player player, BlackjackGame game) {
            super(plugin, player, 5, "&8Blackjack");
            this.game = game;
            this.table = game.tableOf(player.getUniqueId());
            if (table != null) {
                table.bind(this);
            }
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.GREEN_STAINED_GLASS_PANE).name(" ").build());

            if (table == null) {
                set(22, Items.of(Material.BARRIER).name("&7Mesa cerrada").build(), e -> close());
                return;
            }

            set(4, Items.of(Material.GOLD_INGOT)
                    .name("&6Bet: &f" + plugin.economy().format(table.bet()))
                    .lore(
                            "&7Your hand: &f" + BlackjackHand.value(table.playerCards()),
                            "&7Dealer: &f" + BlackjackHand.value(table.dealerCards()),
                            "&7Deck: &f" + table.deck.remaining() + " &7cards",
                            "",
                            "&7A natural pays &f3:2&7; a push returns the stake.")
                    .glow(true)
                    .build());

            renderCards(BlackjackGame.CARD_SLOTS_DEALER, table.dealerCards(), true);
            renderCards(BlackjackGame.CARD_SLOTS_PLAYER, table.playerCards(), false);

            boolean settled = table.isSettled();
            set(36, Items.of(settled ? Material.GRAY_DYE : Material.LIME_CONCRETE)
                    .name("&aHit")
                    .lore("&7Take another card.", "&7Busting over 21 loses.")
                    .build(), e -> {
                if (!settled) {
                    game.hit(player(), table);
                }
            });

            set(40, Items.of(settled ? Material.GRAY_DYE : Material.RED_CONCRETE)
                    .name("&cStand")
                    .lore("&7The dealer plays their hand.")
                    .build(), e -> {
                if (!settled) {
                    game.stand(player(), table);
                }
            });

            set(44, Items.of(table.canDouble(player()) ? Material.GOLD_BLOCK : Material.GRAY_DYE)
                    .name("&6Double down")
                    .lore("&7Double the bet and take",
                            "&7one more card, then you must stand.",
                            "",
                            table.canDouble(player()) ? "&eClick to double" : "&7Not available")
                    .build(), e -> {
                if (table.canDouble(player())) {
                    game.doubleDown(player(), table);
                }
            });
        }

        private void renderCards(int startSlot, List<Card> cards, boolean hideHole) {
            for (int i = 0; i < cards.size(); i++) {
                // The dealer's second card stays face down until the end.
                boolean hole = hideHole && i == 1 && !table.isSettled();
                Card card = cards.get(i);
                set(startSlot + i, Items.of(hole ? Material.GRAY_STAINED_GLASS_PANE : cardMaterial(card))
                        .name(hole ? "&8? ? ?" : "&f" + card.display())
                        .lore(hole ? "&7Face down card" : "&7Worth &f" + card.blackjackValue())
                        .build());
            }
        }

        private static Material cardMaterial(Card card) {
            if (card.suit() == Card.Suit.CORAZONES || card.suit() == Card.Suit.DIAMANTES) {
                return Material.RED_DYE;
            }
            return Material.BLACK_DYE;
        }

        @Override
        protected void onClose() {
            // Closing the table without resolving it means giving up the stake.
            if (table != null && !table.isSettled()) {
                game.stand(player(), table);
            }
        }

        @Override
        public String sessionId() {
            return "blackjack";
        }
    }
}
