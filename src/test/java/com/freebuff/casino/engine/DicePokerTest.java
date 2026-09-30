package com.freebuff.casino.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class DicePokerTest {

    @Test
    void detectaTodasLasCategorias() {
        assertEquals(DicePoker.Hand.REPOKER, DicePoker.handOf(new int[] {4, 4, 4, 4, 4}));
        assertEquals(DicePoker.Hand.POKER, DicePoker.handOf(new int[] {4, 4, 4, 4, 2}));
        assertEquals(DicePoker.Hand.FULL, DicePoker.handOf(new int[] {4, 4, 4, 2, 2}));
        assertEquals(DicePoker.Hand.ESCALERA, DicePoker.handOf(new int[] {2, 3, 4, 5, 6}));
        assertEquals(DicePoker.Hand.ESCALERA, DicePoker.handOf(new int[] {1, 2, 3, 4, 5}));
        assertEquals(DicePoker.Hand.TRIO, DicePoker.handOf(new int[] {4, 4, 4, 2, 6}));
        assertEquals(DicePoker.Hand.DOBLE_PAREJA, DicePoker.handOf(new int[] {4, 4, 2, 2, 6}));
        assertEquals(DicePoker.Hand.PAREJA, DicePoker.handOf(new int[] {4, 4, 2, 5, 6}));
        assertEquals(DicePoker.Hand.CARTA_ALTA, DicePoker.handOf(new int[] {1, 3, 4, 5, 6}));
    }

    @Test
    void noCuentaEscalerasConRepeticiones() {
        // 1,2,3,4,6 no es escalera; 1,2,3,4,4 tampoco.
        assertEquals(DicePoker.Hand.CARTA_ALTA, DicePoker.handOf(new int[] {1, 2, 3, 4, 6}));
        assertEquals(DicePoker.Hand.PAREJA, DicePoker.handOf(new int[] {1, 2, 3, 4, 4}));
    }

    @Test
    void lasManosMejoresPuntuanMasAlto() {
        int repoker = DicePoker.score(new int[] {6, 6, 6, 6, 6});
        int poker = DicePoker.score(new int[] {6, 6, 6, 6, 5});
        int full = DicePoker.score(new int[] {6, 6, 6, 5, 5});
        int escalera = DicePoker.score(new int[] {2, 3, 4, 5, 6});
        int trio = DicePoker.score(new int[] {6, 6, 6, 2, 3});
        int doble = DicePoker.score(new int[] {6, 6, 5, 5, 2});
        int pareja = DicePoker.score(new int[] {6, 6, 4, 3, 2});
        // Cuidado: 2-3-4-5-6 es escalera al seis, no carta alta.
        int alta = DicePoker.score(new int[] {6, 5, 4, 3, 1});

        assertTrue(repoker > poker);
        assertTrue(poker > full);
        assertTrue(full > escalera);
        assertTrue(escalera > trio);
        assertTrue(trio > doble);
        assertTrue(doble > pareja);
        assertTrue(pareja > alta);
    }

    @Test
    void elDesempateSeResuelvePorElValorDeLosDados() {
        int parejaAlta = DicePoker.score(new int[] {6, 6, 4, 3, 2});
        int parejaBaja = DicePoker.score(new int[] {5, 5, 4, 3, 2});
        assertTrue(parejaAlta > parejaBaja, "la pareja de seises debe ganar a la de cincos");

        int trioSextos = DicePoker.score(new int[] {6, 6, 6, 2, 3});
        int trioCincos = DicePoker.score(new int[] {5, 5, 5, 6, 2});
        assertTrue(trioSextos > trioCincos, "el trio manda sobre las cartas de apoyo");
        // Ojo con 5-5-5-6-6: eso ya es un full house, no un trio.
        assertEquals(DicePoker.Hand.FULL, DicePoker.handOf(new int[] {5, 5, 5, 6, 6}));
    }

    @Test
    void tiradasIdenticasEmpatanExactamente() {
        assertEquals(DicePoker.score(new int[] {3, 3, 5, 5, 1}), DicePoker.score(new int[] {1, 5, 3, 5, 3}));
    }

    @Test
    void losDadosSiempreEstanEnRango() {
        for (int i = 0; i < 20_000; i++) {
            int[] dice = DicePoker.roll();
            assertEquals(5, dice.length);
            for (int die : dice) {
                assertTrue(die >= 1 && die <= 6, "dado invalido en " + Arrays.toString(dice));
            }
            DicePoker.handOf(dice);
            DicePoker.score(dice);
        }
    }

    @Test
    void unDadoFueraDeRangoSeRechaza() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DicePoker.handOf(new int[] {1, 2, 3, 4, 7}));
    }
}
