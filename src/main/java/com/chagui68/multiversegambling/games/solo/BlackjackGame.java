package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.engine.BlackjackHand;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Blackjack against the dealer.
 *
 * <p>The hand is dealt in two steps. First the player and the dealer get one card each,
 * face up, and the player decides: <b>continue</b> to the second card and play the hand,
 * or <b>give up</b> right there and get half of the stake back. After that it is classic
 * blackjack: hit, stand or double down, the dealer drawing to 17. The shoe is shuffled
 * with provably fair rolls, and the payout rules live in {@link BlackjackHand}, which
 * has its own tests.</p>
 *
 * <p>In the casino world the hand is dealt on the card table of the pavilion and played
 * with floating buttons; anywhere else it is played in a menu.</p>
 */
public final class BlackjackGame extends AbstractSoloGame {

    private static final int CARD_SLOTS_PLAYER = 29;
    private static final int CARD_SLOTS_DEALER = 11;
    /** What giving up after the first card gives back. */
    private static final double SURRENDER_RETURN = 0.5;

    private final Map<UUID, Table> tables = new HashMap<>();

    public BlackjackGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("blackjack", "Blackjack", GameCategory.SOLO, Material.BOOK)
                .desc("&7Reach 21 without busting and beat",
                        "&7the dealer. A natural pays &f3:2&7.",
                        "&7See your first card and stay or",
                        "&7give up for half of your stake.")
                .build());
    }

    @Override
    public boolean ownsPlayer(UUID playerId) {
        return tables.containsKey(playerId);
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        Table table = new Table(player, wager);
        tables.put(player.getUniqueId(), table);
        ArenaStage stage = arenaFor(player);
        if (stage != null) {
            table.show = new CardTableShow(plugin, stage,
                    Text.strip(plugin.messages().forSender(player, "panel.blackjack.table-dealer")),
                    player.getName(), 20 * 60 * 10);
            table.show.start();
            table.arena = new ArenaHand(player, table, stage);
            table.arena.begin();
        } else {
            new BlackjackGui(plugin, player, this, table).show();
        }
        dealFirstCards(player, table);
    }

    /**
     * One card for the player and one for the dealer, both face up, after a short pause.
     */
    private void dealFirstCards(Player player, Table table) {
        table.playerCards.add(table.deck.draw());
        table.dealerCards.add(table.deck.draw());
        sound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        message(player, "panel.blackjack.first-card",
                "card", table.playerCards.get(0).display(),
                "dealer", table.dealerCards.get(0).display());
        refresh(table);
    }

    /**
     * The player stays in: second card for each, and the hand is played.
     */
    void proceed(Player player, Table table) {
        if (table.settled || table.stage != Stage.OPENING) {
            return;
        }
        table.stage = Stage.PLAYING;
        table.playerCards.add(table.deck.draw());
        table.dealerCards.add(table.deck.draw());
        sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.3f);
        if (BlackjackHand.isBlackjack(table.dealerCards) || BlackjackHand.isBlackjack(table.playerCards)) {
            finish(player, table);
            return;
        }
        actionBarKey(player, "panel.blackjack.action",
                "hand", BlackjackHand.value(table.playerCards),
                "card", table.dealerCards.get(0).display());
        refresh(table);
    }

    /**
     * The player gives up after seeing the first card: half of the stake comes back.
     */
    void surrender(Player player, Table table) {
        if (table.settled || table.stage != Stage.OPENING) {
            return;
        }
        table.settled = true;
        table.stage = Stage.DONE;
        double payout = settle(player, table.wager, SURRENDER_RETURN);
        tables.remove(player.getUniqueId());
        message(player, "panel.blackjack.surrendered",
                "card", table.playerCards.get(0).display(),
                "refund", plugin.economy().format(payout));
        sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
        if (table.show != null) {
            table.show.result(plugin.messages().forSender(player, "panel.blackjack.table-surrender"), false);
            table.show.settle();
        }
        closeControls(table);
        refresh(table);
        offerReplay(player);
    }

    /**
     * Hits for the player.
     */
    void hit(Player player, Table table) {
        if (table.settled || table.stage != Stage.PLAYING) {
            return;
        }
        table.hits++;
        table.playerCards.add(table.deck.draw());
        sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.3f);
        if (BlackjackHand.isBust(table.playerCards) || BlackjackHand.value(table.playerCards) == 21) {
            finish(player, table);
            return;
        }
        refresh(table);
    }

    /**
     * Stands: the dealer plays.
     */
    void stand(Player player, Table table) {
        if (table.settled || table.stage != Stage.PLAYING) {
            return;
        }
        finish(player, table);
    }

    /**
     * Doubles down: takes another equal stake and deals one more card.
     */
    void doubleDown(Player player, Table table) {
        if (!table.canDouble(player)) {
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

    /**
     * Plays the dealer and settles the hand.
     */
    private void finish(Player player, Table table) {
        if (table.settled) {
            return;
        }
        table.settled = true;
        table.stage = Stage.DONE;
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

        double payout = settle(player, table.wager, multiplier);
        if (table.extra != null) {
            // The second half of the double is paid or lost with the same result.
            double extraPayout = table.extra.amount() * multiplier;
            if (extraPayout > 0) {
                table.extra.payAbsolute(extraPayout);
            } else {
                table.extra.lose();
            }
            payout += extraPayout;
        }
        tables.remove(player.getUniqueId());

        double staked = table.bet();
        boolean won = payout > staked;
        boolean push = payout > 0 && Math.abs(payout - staked) < 1e-9;
        announceResult(player, won, payout > 0
                ? plugin.messages().forSender(player, "panel.blackjack.prize-subtitle",
                "prize", plugin.economy().format(payout))
                : plugin.messages().forSender(player, "panel.common.no-prize"));
        info(player, title(player));
        message(player, "panel.blackjack.your-hand",
                "cards", BlackjackHand.describe(table.playerCards),
                "value", BlackjackHand.value(table.playerCards));
        message(player, "panel.blackjack.dealer-hand",
                "cards", BlackjackHand.describe(table.dealerCards),
                "value", BlackjackHand.value(table.dealerCards));
        if (playerBlackjack && !dealerBlackjack) {
            message(player, "panel.blackjack.natural");
        } else if (dealerBust) {
            message(player, "panel.blackjack.dealer-bust");
        }
        showResult(player, staked, payout);
        sound(player, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, won ? 1.3f : 0.9f);
        if (table.show != null) {
            paintTable(table);
            String banner = playerBlackjack && !dealerBlackjack ? "panel.blackjack.table-natural"
                    : won ? "panel.common.table-won" : push ? "panel.common.table-push" : "panel.common.table-lost";
            table.show.result(plugin.messages().forSender(player, banner), won);
            table.show.settle();
        }
        closeControls(table);
        refresh(table);
        offerReplay(player);
    }

    /**
     * The hand was left open: before the second card it counts as giving up, during the
     * hand as standing. A player who logs out is settled the same way, in silence.
     */
    private void abandon(Player player, Table table) {
        if (table.settled) {
            return;
        }
        if (table.stage == Stage.OPENING) {
            surrender(player, table);
        } else {
            finish(player, table);
        }
    }

    private void abandonOffline(UUID playerId, Table table) {
        if (table.settled) {
            return;
        }
        table.settled = true;
        tables.remove(playerId);
        if (table.stage == Stage.OPENING) {
            settleOffline(playerId, table.wager, SURRENDER_RETURN);
        } else {
            boolean hitSoft17 = plugin.config().blackjackHitSoft17();
            boolean playerBlackjack = BlackjackHand.isBlackjack(table.playerCards);
            boolean dealerBlackjack = BlackjackHand.isBlackjack(table.dealerCards);
            if (!playerBlackjack && !BlackjackHand.isBust(table.playerCards)) {
                while (BlackjackHand.dealerMustHit(table.dealerCards, hitSoft17)) {
                    table.dealerCards.add(table.deck.draw());
                }
            }
            double multiplier = BlackjackHand.payout(table.playerCards, table.dealerCards, playerBlackjack,
                    dealerBlackjack, BlackjackHand.isBust(table.dealerCards));
            settleOffline(playerId, table.wager, multiplier);
            if (table.extra != null) {
                settleOffline(playerId, table.extra, multiplier);
            }
        }
        table.stage = Stage.DONE;
        if (table.show != null) {
            table.show.cancel();
        }
    }

    // ---------------------------------------------------------------- painting

    /**
     * Brings the table, the buttons and the menu up to date.
     */
    private void refresh(Table table) {
        paintTable(table);
        if (table.arena != null) {
            table.arena.controls();
        }
        if (table.gui != null) {
            table.gui.refresh();
        }
    }

    private void paintTable(Table table) {
        if (table.show == null) {
            return;
        }
        boolean reveal = table.stage == Stage.DONE;
        String dealer = reveal ? String.valueOf(BlackjackHand.value(table.dealerCards))
                : table.dealerCards.isEmpty() ? "" : String.valueOf(table.dealerCards.get(0).blackjackValue());
        table.show.deal(table.dealerCards, !reveal, dealer,
                table.playerCards, String.valueOf(BlackjackHand.value(table.playerCards)));
    }

    private void closeControls(Table table) {
        if (table.arena != null) {
            table.arena.close();
        }
    }

    /**
     * Where the hand is.
     */
    private enum Stage {
        /** One card each: continue or give up. */
        OPENING,
        /** Hit, stand or double. */
        PLAYING,
        /** Settled. */
        DONE
    }

    /**
     * State of one blackjack table.
     */
    final class Table {

        private final Card.Deck deck;
        private final List<Card> playerCards = new ArrayList<>();
        private final List<Card> dealerCards = new ArrayList<>();
        private final Wager wager;
        private Wager extra;
        private Stage stage = Stage.OPENING;
        private int hits;
        private boolean doubled;
        private boolean settled;
        private CardTableShow show;
        private ArenaHand arena;
        private BlackjackGui gui;

        Table(Player player, Wager wager) {
            this.wager = wager;
            int decks = BlackjackGame.this.plugin.config().blackjackDecks();
            // The shoe is shuffled with provably fair rolls, like every other result.
            this.deck = Card.Deck.shuffled(decks,
                    BlackjackGame.this.plugin.fair().rolls(player.getUniqueId(), Card.Deck.rollsFor(decks)));
        }

        double bet() {
            return wager.amount() * (doubled ? 2 : 1);
        }

        boolean canDouble(Player player) {
            return !settled && stage == Stage.PLAYING && !doubled && hits == 0 && playerCards.size() == 2
                    && BlackjackHand.value(playerCards) < 21
                    && BlackjackGame.this.plugin.economy().has(player.getUniqueId(), wager.amount());
        }
    }

    // ------------------------------------------------------------------- arena

    /**
     * The hand played at the card table of the pavilion, with floating buttons in front
     * of the player.
     */
    private final class ArenaHand extends LiveRound {

        private final Table table;
        private final List<HoloButton> buttons = new ArrayList<>();
        private Stage drawn;
        private boolean couldDouble;

        ArenaHand(Player player, Table table, ArenaStage stage) {
            super(plugin, player, id(), stage);
            this.table = table;
        }

        /**
         * Puts up the buttons the hand needs right now.
         */
        void controls() {
            Player player = player();
            if (player == null || over() || table.settled) {
                return;
            }
            boolean canDouble = table.canDouble(player);
            if (drawn == table.stage && couldDouble == canDouble && !buttons.isEmpty()) {
                return;
            }
            drawn = table.stage;
            couldDouble = canDouble;
            clear();
            UUID owner = player.getUniqueId();
            if (table.stage == Stage.OPENING) {
                buttons.addAll(table.show.addControlRow(0.75, owner, List.of(
                        new HoloButton.Spec(label(player, "panel.blackjack.button-continue"),
                                HoloButton.GREEN, 1.3f, clicker -> proceed(clicker, table)),
                        new HoloButton.Spec(label(player, "panel.blackjack.button-surrender",
                                "refund", plugin.economy().format(table.wager.amount() * SURRENDER_RETURN)),
                                HoloButton.RED, 1.3f, clicker -> surrender(clicker, table)))));
                return;
            }
            List<HoloButton> row = table.show.addControlRow(0.75, owner, List.of(
                    new HoloButton.Spec(label(player, "panel.blackjack.button-hit"),
                            HoloButton.GREEN, 1.4f, clicker -> hit(clicker, table)),
                    new HoloButton.Spec(label(player, "panel.blackjack.button-stand"),
                            HoloButton.RED, 1.4f, clicker -> stand(clicker, table)),
                    new HoloButton.Spec(label(player, "panel.blackjack.button-double"),
                            HoloButton.GOLD, 1.4f, clicker -> doubleDown(clicker, table))));
            row.get(2).enabled(canDouble);
            buttons.addAll(row);
        }

        private net.kyori.adventure.text.Component label(Player viewer, String key, Object... replacements) {
            return Text.c(plugin.messages().forSender(viewer, key, replacements));
        }

        private void clear() {
            buttons.forEach(HoloButton::remove);
            buttons.clear();
        }

        void close() {
            finish();
            clear();
        }

        @Override
        protected void onAbandon(Player player) {
            clear();
            abandon(player, table);
        }

        @Override
        protected void onCancel() {
            clear();
            abandonOffline(playerId(), table);
        }
    }

    // -------------------------------------------------------------------- menu

    /**
     * Table in a menu: the dealer's cards on top, the player's below and the actions at
     * the bottom.
     */
    private final class BlackjackGui extends Gui {

        private final BlackjackGame game;
        private final Table table;

        BlackjackGui(MultiverseGamblingPlugin plugin, Player player, BlackjackGame game, Table table) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.blackjack.title"));
            this.game = game;
            this.table = table;
            table.gui = this;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.GREEN_STAINED_GLASS_PANE).name(" ").build());
            border(Items.of(Material.BROWN_STAINED_GLASS_PANE).name(" ").build());

            boolean revealed = table.stage == Stage.DONE;
            int dealerShown = revealed ? BlackjackHand.value(table.dealerCards)
                    : table.dealerCards.isEmpty() ? 0 : table.dealerCards.get(0).blackjackValue();
            set(4, Items.of(Material.GOLD_INGOT)
                    .name(label(player(), "panel.common.bet",
                            "bet", plugin.economy().format(table.bet())))
                    .lore(
                            label(player(), "panel.blackjack.hand",
                                    "value", BlackjackHand.value(table.playerCards)),
                            label(player(), "panel.blackjack.dealer", "value", dealerShown),
                            label(player(), "panel.blackjack.deck", "cards", table.deck.remaining()),
                            "",
                            label(player(), "panel.blackjack.rules"))
                    .glow(true)
                    .build());

            renderCards(CARD_SLOTS_DEALER, table.dealerCards, !revealed);
            renderCards(CARD_SLOTS_PLAYER, table.playerCards, false);

            if (table.stage == Stage.OPENING) {
                set(38, Items.of(Material.LIME_CONCRETE)
                        .name(label(player(), "panel.blackjack.continue"))
                        .lore(label(player(), "panel.blackjack.continue-lore"))
                        .glow(true)
                        .build(), e -> game.proceed(player(), table));
                set(42, Items.of(Material.RED_CONCRETE)
                        .name(label(player(), "panel.blackjack.surrender"))
                        .lore(label(player(), "panel.blackjack.surrender-lore",
                                "refund", plugin.economy().format(table.wager.amount() * SURRENDER_RETURN)))
                        .build(), e -> game.surrender(player(), table));
                return;
            }
            boolean playing = table.stage == Stage.PLAYING;
            set(38, Items.of(playing ? Material.LIME_CONCRETE : Material.GRAY_DYE)
                    .name(label(player(), "panel.blackjack.hit"))
                    .lore(label(player(), "panel.blackjack.hit-lore"),
                            label(player(), "panel.blackjack.hit-lore-2"))
                    .build(), e -> game.hit(player(), table));
            set(40, Items.of(playing ? Material.RED_CONCRETE : Material.GRAY_DYE)
                    .name(label(player(), "panel.blackjack.stand"))
                    .lore(label(player(), "panel.blackjack.stand-lore"))
                    .build(), e -> game.stand(player(), table));
            boolean canDouble = table.canDouble(player());
            set(42, Items.of(canDouble ? Material.GOLD_BLOCK : Material.GRAY_DYE)
                    .name(label(player(), "panel.blackjack.double"))
                    .lore(label(player(), "panel.blackjack.double-lore"),
                            label(player(), "panel.blackjack.double-lore-2"),
                            "",
                            canDouble ? label(player(), "panel.blackjack.click-double")
                                    : label(player(), "panel.blackjack.not-available"))
                    .build(), e -> game.doubleDown(player(), table));
        }

        private void renderCards(int startSlot, List<Card> cards, boolean hideHole) {
            for (int i = 0; i < cards.size() && i < 7; i++) {
                // The dealer's second card stays face down until the end.
                boolean hole = hideHole && i == 1;
                Card card = cards.get(i);
                set(startSlot + i, Items.of(hole ? Material.GRAY_STAINED_GLASS_PANE : cardMaterial(card))
                        .name(hole
                                ? label(player(), "panel.blackjack.hidden")
                                : label(player(), "panel.blackjack.card", "card", card.display()))
                        .lore(hole
                                ? label(player(), "panel.blackjack.face-down")
                                : label(player(), "panel.blackjack.worth",
                                "value", card.blackjackValue()))
                        .build());
            }
        }

        private static Material cardMaterial(Card card) {
            if (card.suit() == Card.Suit.HEARTS || card.suit() == Card.Suit.DIAMONDS) {
                return Material.RED_DYE;
            }
            return Material.BLACK_DYE;
        }

        @Override
        protected void onClose() {
            table.gui = null;
            // Closing the table before the end: give up before the second card, stand after.
            if (!table.settled) {
                if (player().isOnline()) {
                    game.abandon(player(), table);
                } else {
                    game.abandonOffline(player().getUniqueId(), table);
                }
            }
        }

        @Override
        public String sessionId() {
            return "blackjack";
        }
    }
}
