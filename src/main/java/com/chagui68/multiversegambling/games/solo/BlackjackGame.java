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
 * Blackjack contra el crupier.
 *
 * <p>Reglas completas: el natural paga 3:2, el empate devuelve la apuesta, se puede
 * doblar y el crupier pide hasta 17 (configurable para que pida con 17 blando).
 * La regla exacta esta en {@link BlackjackHand}, que tiene sus propios tests.</p>
 */
public final class BlackjackGame extends AbstractSoloGame {

    private static final int CARD_SLOTS_PLAYER = 29;
    private static final int CARD_SLOTS_DEALER = 11;

    private final java.util.Map<UUID, Table> tables = new java.util.HashMap<>();

    public BlackjackGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("blackjack", "Blackjack", GameCategory.SOLO, Material.BOOK)
                .desc("&7Llega a 21 sin pasarte y gana al",
                        "&7crupier. El natural paga &f3:2&7.",
                        "&7Pide, plantate o dobla.")
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
        // Reparto inicial con suspense.
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
                online.sendActionBar(Text.c("&7Tu mano: &f"
                        + BlackjackHand.value(table.playerCards) + " &8| &7Crupier: &f"
                        + table.dealerCards.get(0).display() + " &7? ?"));
                table.gui().ifPresent(Gui::refresh);
            }
        };
        animation.run();
    }

    /** Pide carta para el jugador. */
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

    /** Se planta: juega el crupier. */
    void stand(Player player, Table table) {
        if (table.settled) {
            return;
        }
        finish(player, table);
    }

    /** Dobla la apuesta: retira otra igual y reparte una carta mas. */
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

    /** Juega el crupier y liquida. */
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
            // La segunda mitad de la doblada se paga o se pierde con el mismo resultado.
            if (multiplier > 0) {
                table.extra.payAbsolute(table.extra.amount() * multiplier);
            } else {
                table.extra.lose();
            }
        }
        tables.remove(player.getUniqueId());

        boolean won = payout > table.wager.amount() * (table.doubled ? 2 : 1);
        announceResult(player, won, payout > 0 ? "&a" + plugin.economy().format(payout) : "&csin premio");
        info(player, title());
        info(player, "&7Tu mano: &f" + BlackjackHand.describe(table.playerCards)
                + " &7= &f" + BlackjackHand.value(table.playerCards));
        info(player, "&7Crupier: &f" + BlackjackHand.describe(table.dealerCards)
                + " &7= &f" + BlackjackHand.value(table.dealerCards));
        if (playerBlackjack && !dealerBlackjack) {
            info(player, "&6Blackjack natural: paga 3 a 2.");
        } else if (dealerBust) {
            info(player, "&aEl crupier se paso.");
        }
        showResult(player, table.wager.amount() * (table.doubled ? 2 : 1), payout);
        sound(player, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, won ? 1.3f : 0.9f);
        table.gui().ifPresent(Gui::refresh);
        offerReplay(player);
    }

    /** Estado de una mesa de blackjack. */
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

    /** Mesa: cartas arriba y acciones abajo. */
    private static final class BlackjackGui extends Gui {

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
                    .name("&6Apuesta: &f" + plugin.economy().format(table.bet()))
                    .lore(
                            "&7Tu mano: &f" + BlackjackHand.value(table.playerCards()),
                            "&7Crupier: &f" + BlackjackHand.value(table.dealerCards()),
                            "&7Baraja: &f" + table.deck.remaining() + " &7cartas",
                            "",
                            "&7Natural paga &f3:2&7; el empate devuelve.")
                    .glow(true)
                    .build());

            renderCards(BlackjackGame.CARD_SLOTS_DEALER, table.dealerCards(), true);
            renderCards(BlackjackGame.CARD_SLOTS_PLAYER, table.playerCards(), false);

            boolean settled = table.isSettled();
            set(36, Items.of(settled ? Material.GRAY_DYE : Material.LIME_CONCRETE)
                    .name("&aPedir carta")
                    .lore("&7Pide otra carta.", "&7Si pasas de 21 pierdes.")
                    .build(), e -> {
                if (!settled) {
                    game.hit(player(), table);
                }
            });

            set(40, Items.of(settled ? Material.GRAY_DYE : Material.RED_CONCRETE)
                    .name("&cPlantarse")
                    .lore("&7El crupier juega su mano.")
                    .build(), e -> {
                if (!settled) {
                    game.stand(player(), table);
                }
            });

            set(44, Items.of(table.canDouble(player()) ? Material.GOLD_BLOCK : Material.GRAY_DYE)
                    .name("&6Doblar")
                    .lore("&7Dobla la apuesta y recibes",
                            "&7una carta mas, obligado a plantarte.",
                            "",
                            table.canDouble(player()) ? "&ePulsa para doblar" : "&7No disponible")
                    .build(), e -> {
                if (table.canDouble(player())) {
                    game.doubleDown(player(), table);
                }
            });
        }

        private void renderCards(int startSlot, List<Card> cards, boolean hideHole) {
            for (int i = 0; i < cards.size(); i++) {
                // La segunda carta del crupier queda boca abajo hasta el final.
                boolean hole = hideHole && i == 1 && !table.isSettled();
                Card card = cards.get(i);
                set(startSlot + i, Items.of(hole ? Material.GRAY_STAINED_GLASS_PANE : cardMaterial(card))
                        .name(hole ? "&8? ? ?" : "&f" + card.display())
                        .lore(hole ? "&7Carta oculta" : "&7Vale &f" + card.blackjackValue())
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
            // Cerrar la mesa sin resolver equivale a abandonar la apuesta.
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
