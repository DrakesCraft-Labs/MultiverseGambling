package com.freebuff.casino.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.freebuff.casino.engine.RouletteTable.Bet;
import com.freebuff.casino.engine.RouletteTable.Color;

class RouletteTableTest {

    private final RouletteTable european = RouletteTable.european();
    private final RouletteTable american = RouletteTable.american();

    @Test
    void laRuedaEuropeaTieneLosBolsillosCorrectos() {
        assertEquals(37, european.pocketCount());
        assertEquals(38, american.pocketCount());
        assertTrue(american.pockets().contains(RouletteTable.DOUBLE_ZERO));
        assertFalse(european.pockets().contains(RouletteTable.DOUBLE_ZERO));
    }

    @Test
    void losColoresCubrenTodaLaRuedaSinSolaparse() {
        int rojos = 0;
        int negros = 0;
        int verdes = 0;
        for (int pocket : european.pockets()) {
            switch (RouletteTable.colorOf(pocket)) {
                case ROJO -> rojos++;
                case NEGRO -> negros++;
                case VERDE -> verdes++;
            }
        }
        assertEquals(18, rojos);
        assertEquals(18, negros);
        assertEquals(1, verdes);
        assertEquals(37, rojos + negros + verdes);
    }

    @Test
    void laVentajaDeLaCasaEsLaEsperada() {
        // 2.70% en la europea y 5.26% en la americana, los numeros clasicos.
        assertEquals(1.0 - 36.0 / 37.0, european.houseEdge(Bet.COLOR), 1e-9);
        assertEquals(1.0 - 36.0 / 38.0, american.houseEdge(Bet.COLOR), 1e-9);
        assertEquals(0.027027027, european.houseEdge(Bet.NUMERO), 1e-9);
    }

    @Test
    void todasLasApuestasDeLaRuletaTienenElMismoRetorno() {
        // Esta es la propiedad que define la ruleta: la ventaja no depende de la
        // apuesta. Si alguien "mejora" un pago, este test lo detecta al instante.
        for (Bet bet : Bet.values()) {
            assertEquals(36.0 / 37.0, european.rtp(bet), 1e-9, "europea, apuesta " + bet);
            assertEquals(36.0 / 38.0, american.rtp(bet), 1e-9, "americana, apuesta " + bet);
        }
    }

    @Test
    void elRetornoCalculadoCoincideConLaTiradaRealSobreTodaLaRueda() {
        for (Bet bet : Bet.values()) {
            int selections = switch (bet) {
                case NUMERO -> 37;
                case COLOR -> 2;
                case PARIDAD, MITAD -> 2;
                case DOCENA, COLUMNA -> 3;
            };
            for (int selection = 0; selection < selections; selection++) {
                double returned = 0;
                for (int result : european.pockets()) {
                    returned += RouletteTable.payoutOf(bet, selection, result);
                }
                double measured = returned / european.pocketCount();
                assertEquals(european.rtp(bet), measured, 1e-9,
                        "La apuesta " + bet + " con seleccion " + selection + " no cuadra");
            }
        }
    }

    @Test
    void elVerdeSoloSePagaALaApuestaDirectaAlCero() {
        assertEquals(36.0, RouletteTable.payoutOf(Bet.NUMERO, 0, 0));
        assertEquals(Color.VERDE, RouletteTable.colorOf(0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.NUMERO, 0, 1));
    }

    @Test
    void elCeroNuncaPagaApuestasExteriores() {
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLOR, 1, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.PARIDAD, 0, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.MITAD, 0, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.DOCENA, 0, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLUMNA, 0, 0));
        // En la americana el 00 tampoco paga.
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLOR, 1, RouletteTable.DOUBLE_ZERO));
    }

    @Test
    void lasColumnasYDocenasAciertanEnLosNumerosCorrectos() {
        // Columna 1: 1, 4, 7 ... 34
        assertEquals(3.0, RouletteTable.payoutOf(Bet.COLUMNA, 0, 1));
        assertEquals(3.0, RouletteTable.payoutOf(Bet.COLUMNA, 0, 34));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLUMNA, 0, 2));
        // Columna 3: 3, 6 ... 36
        assertEquals(3.0, RouletteTable.payoutOf(Bet.COLUMNA, 2, 36));
        // Docenas
        assertEquals(3.0, RouletteTable.payoutOf(Bet.DOCENA, 0, 12));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.DOCENA, 0, 13));
        assertEquals(3.0, RouletteTable.payoutOf(Bet.DOCENA, 2, 36));
    }

    @Test
    void elResultadoDeLaTiradaSiempreEsUnBolsilloValido() {
        for (int i = 0; i < 5_000; i++) {
            int result = european.spin();
            assertTrue(result >= 0 && result <= 36);
        }
        for (int i = 0; i < 5_000; i++) {
            int result = american.spin();
            assertTrue(result >= 0 && result <= RouletteTable.DOUBLE_ZERO);
        }
    }
}
