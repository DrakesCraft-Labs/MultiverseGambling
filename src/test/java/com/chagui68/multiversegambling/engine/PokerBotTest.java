package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The house player must only ever take legal decisions and must play its cards with
 * some sense: aces are worth more than seven deuce, and a monster does not fold.
 */
class PokerBotTest {

    @Test
    void equityMatchesKnownMatchups() {
        SplittableRandom random = new SplittableRandom(1);
        double aces = PokerBot.equity(PokerHandTest.cards("As Ah"), List.of(), 1, 20000, random);
        double sevenDeuce = PokerBot.equity(PokerHandTest.cards("7c 2d"), List.of(), 1, 20000, random);
        // Pocket aces win about 85% heads up, seven deuce offsuit about 35%.
        assertEquals(0.85, aces, 0.02);
        assertEquals(0.35, sevenDeuce, 0.03);
        double nuts = PokerBot.equity(PokerHandTest.cards("As Ks"), PokerHandTest.cards("Qs Js 10s 2d 3c"), 3, 2000,
                random);
        assertEquals(1.0, nuts, 1e-9, "a royal flush cannot lose");
    }

    @Test
    void theBotOnlyTakesLegalDecisions() {
        SplittableRandom random = new SplittableRandom(7);
        UUID[] ids = {new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), new UUID(0, 4)};
        for (int game = 0; game < 40; game++) {
            PokerTable table = new PokerTable(8, 5, 10);
            long total = 0;
            for (int p = 0; p < ids.length; p++) {
                long stack = 50 + random.nextInt(1500);
                table.sit(p * 2, ids[p], stack);
                total += stack;
            }
            for (int hand = 0; hand < 5 && table.ready() >= 2; hand++) {
                table.startHand(new Card.Deck(1, new SplittableRandom(random.nextLong())));
                int guard = 0;
                while (table.phase() != PokerTable.Phase.FINISHED && guard++ < 300) {
                    if (table.phase() == PokerTable.Phase.STREET_DONE) {
                        table.nextStreet();
                        continue;
                    }
                    int seat = table.toAct();
                    PokerBot.Decision decision = PokerBot.decide(table, seat, random);
                    assertTrue(table.act(seat, decision.action(), decision.raiseTo()),
                            "illegal decision " + decision + " with " + table.options(seat));
                }
                long chips = table.pot();
                for (int i = 0; i < table.size(); i++) {
                    if (table.seat(i) != null) {
                        chips += table.seat(i).stack();
                    }
                }
                assertEquals(total, chips);
            }
        }
    }

    @Test
    void theBotDoesNotFoldTheNuts() {
        int folds = 0;
        for (int run = 0; run < 50; run++) {
            PokerTable table = new PokerTable(8, 5, 10);
            table.sit(0, new UUID(0, 1), 1000);
            table.sit(1, new UUID(0, 2), 1000);
            // Seat 0 gets the aces (cards are dealt seat 0, seat 1, seat 0, seat 1).
            table.startHand(deckStartingWith("As Kd Ah Kc"));
            // Seat 0 is the button and acts first heads up: facing a call of 5 it must play.
            PokerBot.Decision decision = PokerBot.decide(table, 0, new SplittableRandom(run));
            if (decision.action() == PokerTable.Action.FOLD) {
                folds++;
            }
        }
        assertEquals(0, folds);
    }

    @Test
    void theBotFoldsRubbishToAHugeBet() {
        int folds = 0;
        for (int run = 0; run < 50; run++) {
            PokerTable table = new PokerTable(8, 5, 10);
            table.sit(0, new UUID(0, 1), 5000);
            table.sit(1, new UUID(0, 2), 5000);
            table.startHand(deckStartingWith("Kd 7c Kc 2h"));
            // The button (seat 0) shoves; the big blind holds seven deuce.
            table.act(0, PokerTable.Action.ALL_IN, 0);
            PokerBot.Decision decision = PokerBot.decide(table, 1, new SplittableRandom(run));
            if (decision.action() == PokerTable.Action.FOLD) {
                folds++;
            }
        }
        assertTrue(folds >= 45, "folded seven deuce to a shove only " + folds + " times out of 50");
        assertNotEquals(0, folds);
    }

    private static Card.Deck deckStartingWith(String cards) {
        List<Card> first = PokerHandTest.cards(cards);
        // Rebuild the order Card.Deck uses and feed it the swaps that bring these cards to
        // the top, as PokerTableTest does.
        java.util.List<Card> order = new java.util.ArrayList<>();
        for (Card.Suit suit : Card.Suit.values()) {
            for (int rank = 2; rank <= Card.ACE; rank++) {
                order.add(new Card(suit, rank));
            }
        }
        java.util.List<Card> target = new java.util.ArrayList<>(first);
        for (Card card : order) {
            if (!target.contains(card)) {
                target.add(card);
            }
        }
        java.util.List<Card> working = new java.util.ArrayList<>(order);
        int[] picks = new int[working.size()];
        for (int i = working.size() - 1; i > 0; i--) {
            int j = working.indexOf(target.get(i));
            picks[i] = j;
            Card tmp = working.get(i);
            working.set(i, working.get(j));
            working.set(j, tmp);
        }
        int[] cursor = {working.size() - 1};
        return new Card.Deck(1, new java.util.random.RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return picks[cursor[0]--];
            }
        });
    }
}
