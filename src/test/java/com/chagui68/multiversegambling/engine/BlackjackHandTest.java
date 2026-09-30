package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.Card.Suit;
import java.util.List;

class BlackjackHandTest {

    private static Card c(int rank) {
        return Card.of(Suit.PICAS, rank);
    }

    @Test
    void elAsValeOnceHastaQueSeRompeLaMano() {
        assertEquals(21, BlackjackHand.value(List.of(c(Card.ACE), c(10))));
        // 11 + 5 + 5 = 21: el As sigue valiendo 11 porque la mano no se pasa.
        assertEquals(21, BlackjackHand.value(List.of(c(Card.ACE), c(5), c(5))));
        // 11 + 6 + 5 = 22, se pasa, asi que el As baja a 1 y quedan 12.
        assertEquals(12, BlackjackHand.value(List.of(c(Card.ACE), c(6), c(5))));
        // Con dos Ases solo uno puede valer 11.
        assertEquals(12, BlackjackHand.value(List.of(c(Card.ACE), c(Card.ACE))));
        // 11 + 11 + 11 = 33 -> el algoritmo baja dos Ases a 1.
        assertEquals(13, BlackjackHand.value(List.of(c(Card.ACE), c(Card.ACE), c(Card.ACE))));
    }

    @Test
    void lasFigurasValenDiez() {
        assertEquals(20, BlackjackHand.value(List.of(c(Card.KING), c(Card.QUEEN))));
        assertEquals(20, BlackjackHand.value(List.of(c(Card.JACK), c(Card.KING))));
    }

    @Test
    void laManoBlandaSeDetectaCorrectamente() {
        assertTrue(BlackjackHand.isSoft(List.of(c(Card.ACE), c(6))));
        assertFalse(BlackjackHand.isSoft(List.of(c(Card.ACE), c(6), c(10))));
        assertFalse(BlackjackHand.isSoft(List.of(c(10), c(7))));
    }

    @Test
    void soloEsBlackjackNaturalConDosCartas() {
        assertTrue(BlackjackHand.isBlackjack(List.of(c(Card.ACE), c(Card.KING))));
        assertTrue(BlackjackHand.isBlackjack(List.of(c(10), c(Card.ACE))));
        // 21 en tres cartas es 21, pero no es natural.
        assertFalse(BlackjackHand.isBlackjack(List.of(c(7), c(7), c(7))));
        assertFalse(BlackjackHand.isBlackjack(List.of(c(Card.ACE), c(Card.ACE))));
    }

    @Test
    void elCrupierSePlantaEnDiecisiete() {
        assertTrue(BlackjackHand.dealerMustHit(List.of(c(10), c(6)), false));
        assertFalse(BlackjackHand.dealerMustHit(List.of(c(10), c(7)), false));
        // La regla opcional: con 17 blando el crupier pide.
        assertTrue(BlackjackHand.dealerMustHit(List.of(c(Card.ACE), c(6)), true));
        assertFalse(BlackjackHand.dealerMustHit(List.of(c(Card.ACE), c(6)), false));
    }

    @Test
    void elNaturalPagaTresAVenticuatroSoloContraNada() {
        List<Card> natural = List.of(c(Card.ACE), c(Card.KING));
        List<Card> normal = List.of(c(10), c(9));
        List<Card> dealerNatural = List.of(c(Card.ACE), c(Card.QUEEN));
        assertEquals(2.5, BlackjackHand.payout(natural, normal, true, false, false));
        assertEquals(1.0, BlackjackHand.payout(natural, dealerNatural, true, true, false), "empate de naturales");
    }

    @Test
    void perderLaManoConMasDeVeintiunoNoPagaNada() {
        assertEquals(0.0, BlackjackHand.payout(List.of(c(10), c(10), c(5)), List.of(c(10), c(8)), false, false, false));
    }

    @Test
    void elEmpateDevuelveLaApuestaYLaVictoriaDobla() {
        assertEquals(1.0, BlackjackHand.payout(List.of(c(10), c(9)), List.of(c(10), c(9)), false, false, false));
        assertEquals(2.0, BlackjackHand.payout(List.of(c(10), c(10)), List.of(c(10), c(8)), false, false, false));
        assertEquals(2.0, BlackjackHand.payout(List.of(c(10), c(6)), List.of(c(10), c(5), c(5)), false, false, true));
        assertEquals(0.0, BlackjackHand.payout(List.of(c(10), c(6)), List.of(c(10), c(9)), false, false, false));
    }

    @Test
    void laBarajaMultiMazoNoSeQuedaSinCartas() {
        Card.Deck deck = new Card.Deck(6);
        for (int i = 0; i < 6 * 52 + 10; i++) {
            assertTrue(deck.draw() != null);
        }
    }
}
