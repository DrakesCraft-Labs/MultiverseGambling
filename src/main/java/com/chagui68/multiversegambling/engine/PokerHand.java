package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Texas hold'em hand ranking: the best five cards out of the seven a player can use.
 *
 * <p>Every hand becomes a single number, {@link Result#score()}, so comparing two hands
 * is comparing two integers: the category sits in the high bits and the ranks that break
 * ties follow it, most important first. The best five are found by trying the 21 ways of
 * picking five cards out of seven, which is plenty fast and leaves no special case to get
 * wrong.</p>
 */
public final class PokerHand {

    /**
     * The hand categories, weakest first.
     */
    public enum Category {
        HIGH_CARD, PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND,
        STRAIGHT_FLUSH, ROYAL_FLUSH;

        /**
         * Language key of the name of the category, such as {@code poker.hand.full-house}.
         */
        public String key() {
            return "poker.hand." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * A ranked hand: what it is, its score and the five cards that make it.
     */
    public record Result(Category category, int score, List<Card> best) implements Comparable<Result> {

        @Override
        public int compareTo(Result other) {
            return Integer.compare(score, other.score);
        }
    }

    private PokerHand() {
    }

    /**
     * Ranks the best hand that can be made with these cards (five to seven of them).
     */
    public static Result best(Collection<Card> cards) {
        Card[] all = cards.toArray(new Card[0]);
        if (all.length < 5) {
            throw new IllegalArgumentException("A poker hand needs at least five cards, got " + all.length);
        }
        int[] combo = new int[5];
        int bestScore = bestScore(all, all.length, combo);
        List<Card> five = new ArrayList<>(5);
        for (int index : combo) {
            five.add(all[index]);
        }
        return new Result(categoryOf(bestScore), bestScore, List.copyOf(five));
    }

    /**
     * Same as {@link #best(Collection)}, from the two hole cards and the board.
     */
    public static Result best(List<Card> hole, List<Card> board) {
        List<Card> cards = new ArrayList<>(hole);
        cards.addAll(board);
        return best(cards);
    }

    /**
     * Score of the best hand among the first {@code n} cards, without building anything:
     * the fast path the house player uses thousands of times per decision.
     */
    public static int score(Card[] cards, int n) {
        return bestScore(cards, n, null);
    }

    private static int bestScore(Card[] all, int n, int[] bestCombo) {
        int best = -1;
        int[] ranks = new int[5];
        int[] suits = new int[5];
        for (int a = 0; a < n - 4; a++) {
            for (int b = a + 1; b < n - 3; b++) {
                for (int c = b + 1; c < n - 2; c++) {
                    for (int d = c + 1; d < n - 1; d++) {
                        for (int e = d + 1; e < n; e++) {
                            ranks[0] = all[a].rank();
                            ranks[1] = all[b].rank();
                            ranks[2] = all[c].rank();
                            ranks[3] = all[d].rank();
                            ranks[4] = all[e].rank();
                            suits[0] = all[a].suit().ordinal();
                            suits[1] = all[b].suit().ordinal();
                            suits[2] = all[c].suit().ordinal();
                            suits[3] = all[d].suit().ordinal();
                            suits[4] = all[e].suit().ordinal();
                            int score = scoreOf(ranks, suits);
                            if (score > best) {
                                best = score;
                                if (bestCombo != null) {
                                    bestCombo[0] = a;
                                    bestCombo[1] = b;
                                    bestCombo[2] = c;
                                    bestCombo[3] = d;
                                    bestCombo[4] = e;
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Category encoded in a score.
     */
    public static Category categoryOf(int score) {
        return Category.values()[score >>> 20];
    }

    /**
     * Score of exactly five cards.
     */
    static int scoreOfFive(Card[] five) {
        int[] ranks = new int[5];
        int[] suits = new int[5];
        for (int i = 0; i < 5; i++) {
            ranks[i] = five[i].rank();
            suits[i] = five[i].suit().ordinal();
        }
        return scoreOf(ranks, suits);
    }

    /**
     * Score of five cards given as ranks and suits. The rank array is sorted in place.
     */
    private static int scoreOf(int[] ranks, int[] suits) {
        boolean flush = suits[0] == suits[1] && suits[0] == suits[2] && suits[0] == suits[3]
                && suits[0] == suits[4];
        // Insertion sort, highest first: five elements.
        for (int i = 1; i < 5; i++) {
            int value = ranks[i];
            int j = i - 1;
            while (j >= 0 && ranks[j] < value) {
                ranks[j + 1] = ranks[j];
                j--;
            }
            ranks[j + 1] = value;
        }

        int straightHigh = straightHigh(ranks);
        if (flush && straightHigh > 0) {
            Category category = straightHigh == Card.ACE ? Category.ROYAL_FLUSH : Category.STRAIGHT_FLUSH;
            return encode(category, straightHigh, 0, 0, 0, 0);
        }

        // Ranks grouped by how often they appear: the biggest group first, then the
        // higher rank. The ranks are sorted, so equal ranks sit next to each other.
        int[] groupRank = new int[5];
        int[] groupSize = new int[5];
        int groups = 0;
        for (int i = 0; i < 5; i++) {
            if (groups > 0 && groupRank[groups - 1] == ranks[i]) {
                groupSize[groups - 1]++;
            } else {
                groupRank[groups] = ranks[i];
                groupSize[groups] = 1;
                groups++;
            }
        }
        // Stable sort by size, biggest first; ranks already descend within a size.
        for (int i = 1; i < groups; i++) {
            int size = groupSize[i];
            int rank = groupRank[i];
            int j = i - 1;
            while (j >= 0 && groupSize[j] < size) {
                groupSize[j + 1] = groupSize[j];
                groupRank[j + 1] = groupRank[j];
                j--;
            }
            groupSize[j + 1] = size;
            groupRank[j + 1] = rank;
        }

        int top = groupSize[0];
        if (top == 4) {
            return encode(Category.FOUR_OF_A_KIND, groupRank[0], groupRank[1], 0, 0, 0);
        }
        if (top == 3 && groups == 2) {
            return encode(Category.FULL_HOUSE, groupRank[0], groupRank[1], 0, 0, 0);
        }
        if (flush) {
            return encode(Category.FLUSH, ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]);
        }
        if (straightHigh > 0) {
            return encode(Category.STRAIGHT, straightHigh, 0, 0, 0, 0);
        }
        if (top == 3) {
            return encode(Category.THREE_OF_A_KIND, groupRank[0], groupRank[1], groupRank[2], 0, 0);
        }
        if (top == 2 && groups == 3) {
            return encode(Category.TWO_PAIR, groupRank[0], groupRank[1], groupRank[2], 0, 0);
        }
        if (top == 2) {
            return encode(Category.PAIR, groupRank[0], groupRank[1], groupRank[2], groupRank[3], 0);
        }
        return encode(Category.HIGH_CARD, ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]);
    }

    /**
     * Highest card of a straight in five ranks sorted high to low, 5 for the wheel
     * (A-2-3-4-5), or 0 when the ranks are not a straight.
     */
    private static int straightHigh(int[] ranks) {
        for (int i = 1; i < 5; i++) {
            if (ranks[i] == ranks[i - 1]) {
                return 0;
            }
        }
        if (ranks[0] - ranks[4] == 4) {
            return ranks[0];
        }
        if (ranks[0] == Card.ACE && ranks[1] == 5 && ranks[4] == 2) {
            return 5;
        }
        return 0;
    }

    private static int encode(Category category, int r0, int r1, int r2, int r3, int r4) {
        return category.ordinal() << 20 | r0 << 16 | r1 << 12 | r2 << 8 | r3 << 4 | r4;
    }
}
