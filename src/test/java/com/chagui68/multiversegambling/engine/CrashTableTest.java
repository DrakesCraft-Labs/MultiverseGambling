package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CrashTableTest {

    private static final double EDGE = 0.01;

    @Test
    void laVentajaDeLaCasaSeConcentraEnElEstallidoInstantaneo() {
        // Todo u por debajo del margen revienta a 1.00x.
        assertEquals(1.00, CrashTable.crashPoint(0.0, EDGE));
        assertEquals(1.00, CrashTable.crashPoint(0.009, EDGE));
        assertEquals(1.00, CrashTable.crashPoint(EDGE - 1e-9, EDGE));
    }

    @Test
    void elPuntoDeExplosionEsMonotonoRespectoAlAzar() {
        double previous = 0;
        for (double u = EDGE; u < 1.0; u += 0.001) {
            double point = CrashTable.crashPoint(u, EDGE);
            assertTrue(point >= previous, "la curva debe crecer con u");
            assertTrue(point >= 1.0);
            previous = point;
        }
    }

    @Test
    void retirarseEnCualquierObjetivoTieneElMismoValorEsperado() {
        // Esta es la propiedad clave del juego: si falla, hay una estrategia ganadora.
        for (double target : new double[] {1.5, 2.0, 3.0, 10.0, 50.0}) {
            double chance = CrashTable.reachChance(target, EDGE);
            assertEquals(1.0 - EDGE, chance * target, 1e-12, "objetivo " + target);
        }
    }

    @Test
    void laFrecuenciaSimuladaDeLlegarADosEquivaleAlCuarentaYNueveYMedioPorCiento() {
        int reached = 0;
        int samples = 400_000;
        for (int i = 0; i < samples; i++) {
            if (CrashTable.crashPoint(Rng.next(), EDGE) >= 2.0) {
                reached++;
            }
        }
        double frequency = (double) reached / samples;
        assertEquals(0.495, frequency, 0.005, "frecuencia simulada de alcanzar 2.00x");
    }

    @Test
    void elValorEsperadoDeUnaApuestaFijaEsElMargenDeLaCasa() {
        // Estrategia fija: retirarse siempre en 3x. El retorno medio debe ser 0.99.
        double target = 3.0;
        double totalReturn = 0;
        int samples = 400_000;
        for (int i = 0; i < samples; i++) {
            double point = CrashTable.crashPoint(Rng.next(), EDGE);
            totalReturn += point >= target ? target : 0;
        }
        assertEquals(1.0 - EDGE, totalReturn / samples, 0.01);
    }

    @Test
    void laCurvaCreceYSePuedeInvertir() {
        double growth = CrashTable.growthRate(10.0);
        assertEquals(2.0, CrashTable.multiplierAt(10.0, growth), 1e-9);
        assertEquals(10.0, CrashTable.secondsToReach(2.0, growth), 1e-9);
        assertEquals(1.0, CrashTable.multiplierAt(0, growth), 1e-9);
    }

    @Test
    void unUInvalidoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> CrashTable.crashPoint(1.0, EDGE));
        assertThrows(IllegalArgumentException.class, () -> CrashTable.crashPoint(-0.1, EDGE));
    }
}
