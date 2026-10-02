package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The house player of the poker table.
 *
 * <p>It plays a plain, solid game: it estimates how often its hand wins against the
 * players still in the hand by dealing the unknown cards a few hundred times, and
 * compares that with what the pot asks it to pay. Strong hands bet and raise, hands with
 * the right price call, the rest check or fold, with a little randomness so it can be
 * neither read nor bullied with a single trick. It never looks at anybody's cards: it
 * only sees its own hand and the board, like everybody else at the table.</p>
 *
 * <p>Its randomness only shapes its decisions. The cards themselves are always dealt
 * from the provably fair shuffle of the hand.</p>
 */
public final class PokerBot {

    /**
     * A decision of the bot.
     *
     * @param raiseTo total bet for the round when the action is a raise
     */
    public record Decision(PokerTable.Action action, long raiseTo) {
    }

    private PokerBot() {
    }

    /**
     * What the bot sitting in that seat does now.
     */
    public static Decision decide(PokerTable table, int seat, RandomGenerator random) {
        PokerTable.Options options = table.options(seat);
        if (options == null) {
            return new Decision(PokerTable.Action.FOLD, 0);
        }
        PokerTable.Seat me = table.seat(seat);
        int opponents = Math.max(1, table.liveCount() - 1);
        int iterations = opponents <= 2 ? 400 : 250;
        double equity = equity(me.hole(), table.board(), opponents, iterations, random);
        // 1.0 is an average hand against that many opponents; 2.0 wins twice as often.
        double strength = equity * (opponents + 1) + (random.nextDouble() - 0.5) * 0.15;
        long pot = table.pot();
        long current = table.currentBet();

        if (options.canCheck()) {
            if (strength > 1.6 && raising(options)) {
                return raise(options, current + Math.round(pot * 0.7));
            }
            if (strength > 1.25 && random.nextDouble() < 0.5 && raising(options)) {
                return raise(options, current + Math.round(pot * 0.5));
            }
            if (random.nextDouble() < 0.06 && raising(options)) {
                return raise(options, current + Math.round(pot * 0.5));
            }
            return new Decision(PokerTable.Action.CHECK, 0);
        }

        double price = options.toCall() / (double) (pot + options.toCall());
        if (strength > 1.9 && raising(options)) {
            return raise(options, current + pot + options.toCall());
        }
        if (equity >= price + 0.03) {
            return call(options);
        }
        if (options.toCall() <= pot * 0.3 && random.nextDouble() < 0.04 && raising(options)) {
            return raise(options, current + pot);
        }
        // Cheap enough to see one more card with something.
        if (options.toCall() <= table.bigBlind() && strength > 0.8) {
            return call(options);
        }
        return new Decision(PokerTable.Action.FOLD, 0);
    }

    private static boolean raising(PokerTable.Options options) {
        return options.canRaise() || options.canAllIn() && options.maxRaiseTo() > options.toCall();
    }

    private static Decision raise(PokerTable.Options options, long target) {
        if (!options.canRaise()) {
            return options.canAllIn() ? new Decision(PokerTable.Action.ALL_IN, 0) : call(options);
        }
        long to = Math.max(options.minRaiseTo(), Math.min(options.maxRaiseTo(), target));
        // Most of the stack in: simply go all in.
        if (to >= options.maxRaiseTo() * 0.8) {
            return options.canAllIn() ? new Decision(PokerTable.Action.ALL_IN, 0)
                    : new Decision(PokerTable.Action.RAISE, options.maxRaiseTo());
        }
        return new Decision(PokerTable.Action.RAISE, to);
    }

    private static Decision call(PokerTable.Options options) {
        if (options.toCall() == 0) {
            return new Decision(PokerTable.Action.CHECK, 0);
        }
        return new Decision(PokerTable.Action.CALL, 0);
    }

    /**
     * How often these two cards win (a tie counting as its share) against that many
     * random hands, the board being completed at random.
     */
    public static double equity(List<Card> hole, List<Card> board, int opponents, int iterations,
                                RandomGenerator random) {
        Set<Card> known = new HashSet<>(hole);
        known.addAll(board);
        List<Card> rest = new ArrayList<>(52);
        for (Card.Suit suit : Card.Suit.values()) {
            for (int rank = 2; rank <= Card.ACE; rank++) {
                Card card = new Card(suit, rank);
                if (!known.contains(card)) {
                    rest.add(card);
                }
            }
        }
        int missing = 5 - board.size();
        int needed = missing + 2 * opponents;
        if (needed > rest.size() || iterations <= 0) {
            return 0.0;
        }
        double total = 0;
        Card[] pool = rest.toArray(new Card[0]);
        Card[] mine = new Card[7];
        Card[] theirs = new Card[7];
        int shown = board.size();
        for (int i = 0; i < hole.size(); i++) {
            mine[i] = hole.get(i);
        }
        for (int i = 0; i < shown; i++) {
            mine[2 + i] = board.get(i);
            theirs[2 + i] = board.get(i);
        }
        for (int run = 0; run < iterations; run++) {
            // Partial Fisher-Yates: only the cards this run needs are drawn.
            for (int i = 0; i < needed; i++) {
                int j = i + random.nextInt(pool.length - i);
                Card tmp = pool[i];
                pool[i] = pool[j];
                pool[j] = tmp;
            }
            for (int i = 0; i < missing; i++) {
                mine[2 + shown + i] = pool[i];
                theirs[2 + shown + i] = pool[i];
            }
            int myScore = PokerHand.score(mine, 7);
            boolean beaten = false;
            int ties = 0;
            for (int o = 0; o < opponents && !beaten; o++) {
                theirs[0] = pool[missing + 2 * o];
                theirs[1] = pool[missing + 2 * o + 1];
                int score = PokerHand.score(theirs, 7);
                if (score > myScore) {
                    beaten = true;
                } else if (score == myScore) {
                    ties++;
                }
            }
            if (!beaten) {
                total += 1.0 / (ties + 1);
            }
        }
        return total / iterations;
    }
}
