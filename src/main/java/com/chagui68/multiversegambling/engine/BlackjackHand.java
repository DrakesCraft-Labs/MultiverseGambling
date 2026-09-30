package com.chagui68.multiversegambling.engine;

import java.util.List;

/** Reglas y evaluacion del Blackjack. */
public final class BlackjackHand {

    /** El crupier pide carta hasta llegar a esta puntuacion. */
    public static final int DEALER_STAND = 17;
    public static final int TARGET = 21;

    private BlackjackHand() {
    }

    /** Suma los valores tratando los Ases como 11 mientras no se pase. */
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

    /** Hay un As contando como 11: la mano es "blanda" y pedir carta no puede romperla. */
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

    /** Blackjack natural: 21 con las dos primeras cartas. Paga 3:2. */
    public static boolean isBlackjack(List<Card> hand) {
        return hand.size() == 2 && value(hand) == TARGET;
    }

    /** El crupier pide mientras no llegue a 17, pero se planta con 17 blando si se configura. */
    public static boolean dealerMustHit(List<Card> hand, boolean hitSoft17) {
        int total = value(hand);
        if (total < DEALER_STAND) {
            return true;
        }
        return total == DEALER_STAND && hitSoft17 && isSoft(hand);
    }

    /**
     * Multiplicador sobre la apuesta para una mano ya terminada.
     *
     * @param playerBlackjack 21 natural del jugador (no cuenta si tambien doblo)
     * @param dealerBlackjack 21 natural del crupier
     * @param dealerBust       el crupier se paso y el jugador no
     * @return 0 en empate (devolucion), 2.5 con natural, 2 en victoria normal
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
