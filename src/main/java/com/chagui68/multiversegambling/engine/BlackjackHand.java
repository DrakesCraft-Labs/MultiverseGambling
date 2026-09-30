package com.chagui68.multiversegambling.engine;

import java.util.List;

/** Blackjack rules and hand evaluation. */
public final class BlackjackHand {

    /** The dealer hits until reaching this total. */
    public static final int DEALER_STAND = 17;
    public static final int TARGET = 21;

    private BlackjackHand() {
    }

    /** Adds the values, counting aces as 11 as long as that does not bust. */
    public static int value(List<Card> hand) {
        int total = 0;
        int aces = 0;
        for (Card card : hand) {
            total += card.blackjackValue();
            if (card.isAce()) {
                aces++;
            }
        }
        while (total > TARGET && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** An ace counts as 11: the hand is "soft" and hitting cannot bust it. */
    public static boolean isSoft(List<Card> hand) {
        int total = 0;
        int aces = 0;
        for (Card card : hand) {
            total += card.blackjackValue();
            if (card.isAce()) {
                aces++;
            }
        }
        return aces > 0 && total <= TARGET;
    }

    public static boolean isBust(List<Card> hand) {
        return value(hand) > TARGET;
    }

    /** Natural blackjack: 21 with the first two cards. Pays 3:2. */
    public static boolean isBlackjack(List<Card> hand) {
        return hand.size() == 2 && value(hand) == TARGET;
    }

    /** The dealer hits below 17, but stands on a soft 17 when configured that way. */
    public static boolean dealerMustHit(List<Card> hand, boolean hitSoft17) {
        int total = value(hand);
        if (total < DEALER_STAND) {
            return true;
        }
        return total == DEALER_STAND && hitSoft17 && isSoft(hand);
    }

    /**
     * Multiplier over the bet for a hand already finished.
     *
     * @param playerBlackjack natural 21 for the player (does not count after doubling)
     * @param dealerBlackjack natural 21 for the dealer
     * @param dealerBust      the dealer busted and the player did not
     * @return 0 on a push (refund), 2.5 with a natural, 2 on a normal win
     */
    public static double payout(List<Card> player, List<Card> dealer,
                                boolean playerBlackjack, boolean dealerBlackjack, boolean dealerBust) {
        int playerValue = value(player);
        if (playerValue > TARGET) {
            return 0;
        }
        if (playerBlackjack && dealerBlackjack) {
            return 1.0;
        }
        if (playerBlackjack) {
            return 2.5;
        }
        if (dealerBlackjack) {
            return 0;
        }
        if (dealerBust) {
            return 2.0;
        }
        int dealerValue = value(dealer);
        if (playerValue > dealerValue) {
            return 2.0;
        }
        return playerValue == dealerValue ? 1.0 : 0;
    }

    public static String describe(List<Card> hand) {
        StringBuilder builder = new StringBuilder();
        for (Card card : hand) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(card.display());
        }
        return builder.toString();
    }
}
