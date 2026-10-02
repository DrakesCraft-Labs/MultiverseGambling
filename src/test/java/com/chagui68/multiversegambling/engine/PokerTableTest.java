package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The poker table moves real money between players, so the rules that decide who puts
 * in and who takes out are pinned here: blinds and turn order, raises, all-ins, side
 * pots, split pots, uncalled bets, and above all that no chip is ever created or lost.
 */
class PokerTableTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);

    private static Card.Deck shuffled(long seed) {
        return new Card.Deck(1, new SplittableRandom(seed));
    }

    /**
     * A deck that deals exactly these cards first, in this order.
     */
    private static Card.Deck forced(List<Card> first) {
        // Card.Deck builds the 52 cards in a fixed order and swaps them with
        // Fisher-Yates from the end. Feeding it the right swaps puts any card where we
        // want: easier is to search for each wanted card at each step.
        List<Card> order = new ArrayList<>();
        for (Card.Suit suit : Card.Suit.values()) {
            for (int rank = 2; rank <= Card.ACE; rank++) {
                order.add(new Card(suit, rank));
            }
        }
        // Desired final arrangement: the wanted cards first, the rest after them.
        List<Card> target = new ArrayList<>(first);
        for (Card card : order) {
            if (!target.contains(card)) {
                target.add(card);
            }
        }
        // Fisher-Yates from the end: at step i the card that ends at position i is chosen
        // among positions 0..i.
        List<Card> working = new ArrayList<>(order);
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

    private static long chips(PokerTable table) {
        long total = table.pot();
        for (int i = 0; i < table.size(); i++) {
            if (table.seat(i) != null) {
                total += table.seat(i).stack();
            }
        }
        return total;
    }

    @Test
    void theForcedDeckDealsWhatWeAskFor() {
        Card.Deck deck = forced(PokerHandTest.cards("As Kd 2c"));
        assertEquals(PokerHandTest.cards("As").get(0), deck.draw());
        assertEquals(PokerHandTest.cards("Kd").get(0), deck.draw());
        assertEquals(PokerHandTest.cards("2c").get(0), deck.draw());
    }

    @Test
    void blindsAndTurnOrderThreeHanded() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.sit(2, C, 1000);
        assertTrue(table.startHand(shuffled(1)));
        assertEquals(0, table.button());
        assertEquals(1, table.smallBlindSeat());
        assertEquals(2, table.bigBlindSeat());
        assertEquals(995, table.seat(1).stack());
        assertEquals(990, table.seat(2).stack());
        // Under the gun is the button when three play.
        assertEquals(0, table.toAct());
        assertEquals(2, table.seat(0).hole().size());
        PokerTable.Options options = table.options(0);
        assertEquals(10, options.toCall());
        assertEquals(20, options.minRaiseTo());
        assertFalse(options.canCheck());
    }

    @Test
    void headsUpTheButtonPostsTheSmallBlindAndActsFirst() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(3, A, 1000);
        table.sit(6, B, 1000);
        table.startHand(shuffled(2));
        assertEquals(table.button(), table.smallBlindSeat());
        assertEquals(table.button(), table.toAct());
        // Limp, the big blind checks its option, then the big blind acts first after the flop.
        assertTrue(table.act(table.toAct(), PokerTable.Action.CALL, 0));
        assertEquals(table.bigBlindSeat(), table.toAct());
        assertTrue(table.act(table.toAct(), PokerTable.Action.CHECK, 0));
        assertEquals(PokerTable.Phase.STREET_DONE, table.phase());
        table.nextStreet();
        assertEquals(3, table.board().size());
        assertEquals(table.bigBlindSeat(), table.toAct());
    }

    @Test
    void theBigBlindGetsItsOption() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.sit(2, C, 1000);
        table.startHand(shuffled(3));
        table.act(0, PokerTable.Action.CALL, 0);
        table.act(1, PokerTable.Action.CALL, 0);
        assertEquals(2, table.toAct(), "everybody limped: the big blind may still raise");
        assertTrue(table.options(2).canRaise());
        table.act(2, PokerTable.Action.RAISE, 40);
        assertEquals(0, table.toAct(), "a raise from the big blind reopens the round");
    }

    @Test
    void aRaiseMustBeAtLeastThePreviousOne() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.sit(2, C, 1000);
        table.startHand(shuffled(4));
        assertFalse(table.act(0, PokerTable.Action.RAISE, 15), "below the minimum raise");
        assertTrue(table.act(0, PokerTable.Action.RAISE, 40));
        PokerTable.Options options = table.options(1);
        assertEquals(70, options.minRaiseTo(), "the raise was 30 so the next one is at least 30 more");
        assertEquals(35, options.toCall());
    }

    @Test
    void aShortAllInDoesNotReopenTheBetting() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.sit(2, C, 55);
        table.startHand(shuffled(5));
        // A raises to 40, B calls, C (big blind) goes all in for 55: only 15 more, short.
        table.act(0, PokerTable.Action.RAISE, 40);
        table.act(1, PokerTable.Action.CALL, 0);
        table.act(2, PokerTable.Action.ALL_IN, 0);
        assertEquals(0, table.toAct());
        PokerTable.Options options = table.options(0);
        assertEquals(15, options.toCall());
        assertFalse(options.canRaise(), "A already acted and the all in was not a full raise");
        assertFalse(options.canAllIn());
        table.act(0, PokerTable.Action.CALL, 0);
        assertFalse(table.options(1).canRaise());
        table.act(1, PokerTable.Action.CALL, 0);
        assertEquals(PokerTable.Phase.STREET_DONE, table.phase());
    }

    @Test
    void foldingAroundGivesThePotWithoutShowing() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.sit(2, C, 1000);
        table.startHand(shuffled(6));
        table.act(0, PokerTable.Action.RAISE, 30);
        table.act(1, PokerTable.Action.FOLD, 0);
        table.act(2, PokerTable.Action.FOLD, 0);
        assertEquals(PokerTable.Phase.FINISHED, table.phase());
        assertTrue(table.uncontested());
        assertEquals(1015, table.seat(0).stack(), "the raise comes back, the blinds are won");
        assertEquals(995, table.seat(1).stack());
        assertEquals(990, table.seat(2).stack());
        assertEquals(3000, chips(table));
    }

    @Test
    void sidePotsAreWonByWhoCoveredThem() {
        // A has the best hand but only 100 chips; B beats C for the side pot.
        Card.Deck deck = forced(PokerHandTest.cards(
                // Hole cards are dealt one at a time from the small blind: B, C, A, B, C, A.
                "Kc Qc As Kd Qd Ah"
                        // burn, flop, burn, turn, burn, river
                        + " 3h Ac 9s 4d 5c Ks 8h Jd 6s"));
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 100);
        table.sit(1, B, 1000);
        table.sit(2, C, 1000);
        table.startHand(deck);
        assertEquals(0, table.button());
        assertTrue(table.act(0, PokerTable.Action.ALL_IN, 0));
        assertTrue(table.act(1, PokerTable.Action.RAISE, 400));
        assertTrue(table.act(2, PokerTable.Action.CALL, 0));
        while (table.phase() != PokerTable.Phase.FINISHED) {
            if (table.phase() == PokerTable.Phase.STREET_DONE) {
                table.nextStreet();
            } else {
                int seat = table.toAct();
                PokerTable.Options options = table.options(seat);
                table.act(seat, options.canCheck() ? PokerTable.Action.CHECK : PokerTable.Action.CALL, 0);
            }
        }
        List<PokerTable.Award> awards = table.results();
        assertEquals(2, awards.size());
        assertEquals(300, awards.get(0).amount(), "main pot: 100 from each of the three");
        assertEquals(List.of(0), awards.get(0).winners());
        assertEquals(600, awards.get(1).amount(), "side pot: 300 more from B and C");
        assertEquals(List.of(1), awards.get(1).winners());
        assertEquals(300, table.seat(0).stack());
        assertEquals(1200, table.seat(1).stack());
        assertEquals(600, table.seat(2).stack());
        assertEquals(2100, chips(table));
    }

    @Test
    void anUncalledBetGoesBack() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 200);
        table.startHand(shuffled(7));
        assertEquals(0, table.button());
        // The bigger stack shoves, the short one calls all in: 800 nobody can call comes back.
        PokerTable.Seat big = table.seat(0);
        assertTrue(table.act(0, PokerTable.Action.ALL_IN, 0));
        assertTrue(table.act(1, PokerTable.Action.CALL, 0));
        assertEquals(PokerTable.Phase.STREET_DONE, table.phase());
        table.nextStreet();
        assertEquals(800, big.stack(), "the part of the shove nobody could call is returned");
        assertTrue(table.runningOut());
        while (table.phase() != PokerTable.Phase.FINISHED) {
            table.nextStreet();
        }
        assertEquals(5, table.board().size());
        assertEquals(1200, chips(table));
    }

    @Test
    void aTieSplitsThePot() {
        // Both play the broadway straight on the board.
        Card.Deck deck = forced(PokerHandTest.cards(
                "2c 3d 2h 3s"
                        + " 4h As Ks Qd 5h Jc 6h 10h"));
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1001);
        table.sit(1, B, 1000);
        table.startHand(deck);
        // Heads up: seat 0 is the button and small blind; it completes, BB checks.
        table.act(0, PokerTable.Action.CALL, 0);
        table.act(1, PokerTable.Action.CHECK, 0);
        table.nextStreet();
        while (table.phase() != PokerTable.Phase.FINISHED) {
            if (table.phase() == PokerTable.Phase.STREET_DONE) {
                table.nextStreet();
            } else {
                table.act(table.toAct(), PokerTable.Action.CHECK, 0);
            }
        }
        PokerTable.Award award = table.results().get(0);
        assertEquals(2, award.winners().size());
        assertEquals(PokerHand.Category.STRAIGHT, award.hand().category());
        assertEquals(1001, table.seat(0).stack());
        assertEquals(1000, table.seat(1).stack());
    }

    @Test
    void oddChipsAreSplitFairly() {
        assertEquals(List.of(34L, 33L, 33L), PokerTable.split(100, 3));
        assertEquals(List.of(5L, 5L), PokerTable.split(10, 2));
    }

    @Test
    void theRakeIsOnlyTakenAfterTheFlop() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.rake(0.05, 0);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.startHand(shuffled(8));
        table.act(table.toAct(), PokerTable.Action.RAISE, 100);
        table.act(table.toAct(), PokerTable.Action.FOLD, 0);
        assertEquals(0, table.results().get(0).rake(), "no flop, no drop");

        table.startHand(shuffled(9));
        table.act(table.toAct(), PokerTable.Action.CALL, 0);
        table.act(table.toAct(), PokerTable.Action.CHECK, 0);
        table.nextStreet();
        table.act(table.toAct(), PokerTable.Action.RAISE, 100);
        table.act(table.toAct(), PokerTable.Action.FOLD, 0);
        PokerTable.Award award = table.results().get(0);
        assertEquals(1, award.rake(), "5% of the 20 in the middle once the flop is out");
        assertEquals(1999, chips(table));
    }

    @Test
    void playersCannotLeaveAHandTheyAreIn() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.startHand(shuffled(10));
        assertEquals(-1, table.leave(0));
        table.sit(2, C, 500);
        assertFalse(table.seat(2).inHand(), "a player who sits down mid hand waits for the next one");
        assertEquals(500, table.leave(2));
    }

    @Test
    void abortingAHandGivesEverythingBack() {
        PokerTable table = new PokerTable(8, 5, 10);
        table.sit(0, A, 1000);
        table.sit(1, B, 1000);
        table.sit(2, C, 1000);
        table.startHand(shuffled(11));
        table.act(0, PokerTable.Action.RAISE, 300);
        table.abortHand();
        assertEquals(1000, table.seat(0).stack());
        assertEquals(1000, table.seat(1).stack());
        assertEquals(1000, table.seat(2).stack());
        assertEquals(PokerTable.Phase.IDLE, table.phase());
    }

    @Test
    void randomHandsNeverCreateOrLoseChips() {
        SplittableRandom random = new SplittableRandom(42);
        UUID[] ids = {A, B, C, D, new UUID(0, 5), new UUID(0, 6)};
        for (int game = 0; game < 300; game++) {
            PokerTable table = new PokerTable(8, 5, 10);
            int players = 2 + random.nextInt(5);
            long total = 0;
            for (int p = 0; p < players; p++) {
                long stack = 20 + random.nextInt(2000);
                table.sit(p, ids[p], stack);
                total += stack;
            }
            for (int hand = 0; hand < 10 && table.ready() >= 2; hand++) {
                assertTrue(table.startHand(shuffled(random.nextLong())));
                int guard = 0;
                while (table.phase() != PokerTable.Phase.FINISHED && guard++ < 500) {
                    if (table.phase() == PokerTable.Phase.STREET_DONE) {
                        table.nextStreet();
                        continue;
                    }
                    int seat = table.toAct();
                    PokerTable.Options options = table.options(seat);
                    assertNotNull(options);
                    int choice = random.nextInt(10);
                    boolean ok;
                    if (choice < 2) {
                        ok = table.act(seat, PokerTable.Action.FOLD, 0);
                    } else if (choice < 6) {
                        ok = table.act(seat, options.canCheck() ? PokerTable.Action.CHECK : PokerTable.Action.CALL, 0);
                    } else if (choice < 9 && options.canRaise()) {
                        long span = options.maxRaiseTo() - options.minRaiseTo();
                        long to = options.minRaiseTo() + (span > 0 ? random.nextLong(span + 1) : 0);
                        ok = table.act(seat, PokerTable.Action.RAISE, to);
                    } else if (options.canAllIn()) {
                        ok = table.act(seat, PokerTable.Action.ALL_IN, 0);
                    } else {
                        ok = table.act(seat, options.canCheck() ? PokerTable.Action.CHECK : PokerTable.Action.CALL, 0);
                    }
                    assertTrue(ok, "a legal move was refused");
                    assertEquals(total, chips(table), "chips appeared or vanished mid hand");
                }
                assertEquals(PokerTable.Phase.FINISHED, table.phase());
                assertEquals(total, chips(table), "chips appeared or vanished at the end of the hand");
                for (int i = 0; i < table.size(); i++) {
                    if (table.seat(i) != null && table.seat(i).stack() == 0) {
                        table.leave(i);
                    }
                }
            }
        }
    }
}
