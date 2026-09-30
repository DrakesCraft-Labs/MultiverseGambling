package com.freebuff.casino.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.freebuff.casino.engine.HorseOdds.Runner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DiceAndRaceTest {

    @Test
    void laProbabilidadDeGanarEsLaComplementariaDelObjetivo() {
        assertEquals(50.0, DiceTable.winChanceOver(50.0), 1e-9);
        assertEquals(25.0, DiceTable.winChanceOver(75.0), 1e-9);
        assertEquals(10.0, DiceTable.winChanceUnder(10.0), 1e-9);
    }

    @Test
    void elPagoEsJustoRecortadoPorLaVentaja() {
        assertEquals(1.98, DiceTable.payout(50.0, 0.01), 1e-9);
        assertEquals(9.9, DiceTable.payout(10.0, 0.01), 1e-9);
        assertEquals(0.99 * 100.0 / 98.0, DiceTable.payout(98.0, 0.01), 1e-9);
        assertEquals(0.0, DiceTable.payout(0, 0.01), 1e-9);
    }

    @Test
    void laTiradaSiempreEstaEnRangoYSeComportaComoIndicaElObjetivo() {
        for (int i = 0; i < 20_000; i++) {
            double roll = DiceTable.roll();
            assertTrue(roll >= 0 && roll < 100, "tirada fuera de rango: " + roll);
            assertEquals(roll, DiceTable.round2(roll), 1e-9, "la tirada deberia tener 2 decimales");
        }
    }

    @Test
    void apostarPorEncimaDelCincuentaPorCientoGanaLaMitadDeLasVeces() {
        int wins = 0;
        int samples = 200_000;
        for (int i = 0; i < samples; i++) {
            if (DiceTable.wins(DiceTable.roll(), 50.0, true)) {
                wins++;
            }
        }
        assertEquals(0.5, (double) wins / samples, 0.005);
    }

    @Test
    void elRetornoDelJuegoDeDadosEsElMargenDeLaCasa() {
        // Retirarse con objetivo 2 (prob 50%) paga 1.98 -> EV de 0.99 por unidad.
        double chance = DiceTable.winChanceOver(50.0) / 100.0;
        double payout = DiceTable.payout(50.0, 0.01);
        assertEquals(0.99, chance * payout, 1e-9);
    }

    @Test
    void caraOCruzNoPuedePagarDosFijo() {
        // Pagar 2.0 con una moneda justa da ventaja cero a la casa: hay que
        // recortarlo igual que en los dados.
        assertEquals(1.96, DiceTable.payout(50.0, 0.02), 1e-9);
        assertTrue(DiceTable.payout(50.0, 0.02) < 2.0);
        assertEquals(1.0 - 0.02, 0.5 * DiceTable.payout(50.0, 0.02), 1e-9,
                "el retorno de la moneda es 1 menos la ventaja");
    }

    @Test
    void elCampoDeCaballosTienePagosCoherentesConSuFuerza() {
        List<Runner> field = HorseOdds.field(8, 0.01);
        assertEquals(8, field.size());
        double totalStrength = field.stream().mapToDouble(Runner::strength).sum();
        for (Runner runner : field) {
            // El retorno va por apuesta, no sumando caballos: quien apueste a uno
            // cualquiera debe recuperar el 99% en media, gane el que gane.
            double chance = runner.strength() / totalStrength;
            assertEquals(0.99, chance * runner.odds(), 0.011, "caballo " + runner.index());
        }
        // Y el caballo mas fuerte paga menos que el mas debil.
        Runner favourite = field.stream().max((a, b) -> Double.compare(a.strength(), b.strength())).orElseThrow();
        Runner outsider = field.stream().min((a, b) -> Double.compare(a.strength(), b.strength())).orElseThrow();
        assertTrue(favourite.odds() < outsider.odds(), "el favorito deberia pagar menos");
    }

    @Test
    void elGanadorSeEligeConLaFrecuenciaEsperada() {
        List<Runner> field = HorseOdds.field(4, 0.01);
        double totalStrength = field.stream().mapToDouble(Runner::strength).sum();
        Map<Integer, Integer> wins = new HashMap<>();
        int samples = 200_000;
        for (int i = 0; i < samples; i++) {
            wins.merge(HorseOdds.winner(field).index(), 1, Integer::sum);
        }
        for (Runner runner : field) {
            double expected = runner.strength() / totalStrength;
            double observed = (double) wins.getOrDefault(runner.index(), 0) / samples;
            assertEquals(expected, observed, 0.01, "caballo " + runner.index());
        }
    }

    @Test
    void laSimulacionDeLaCarreraDevuelveUnCuadroPorPaso() {
        List<double[]> frames = HorseOdds.simulate(HorseOdds.field(6, 0.01), 40);
        assertEquals(40, frames.size());
        for (double[] frame : frames) {
            assertEquals(6, frame.length);
        }
        // Las posiciones nunca retroceden.
        for (int step = 1; step < frames.size(); step++) {
            for (int runner = 0; runner < 6; runner++) {
                assertTrue(frames.get(step)[runner] >= frames.get(step - 1)[runner]);
            }
        }
    }

    @Test
    void unCampoVacioNoSePuedeSortear() {
        assertThrows(IllegalStateException.class, () -> HorseOdds.winner(List.of()));
    }
}
