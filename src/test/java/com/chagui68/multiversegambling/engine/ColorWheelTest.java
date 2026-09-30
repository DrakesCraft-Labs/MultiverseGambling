package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.ColorWheel.Outcome;
import java.util.EnumMap;
import java.util.Map;

class ColorWheelTest {

    private final ColorWheel wheel = ColorWheel.european();

    @Test
    void laRuedaEstandarTieneTreintaYSieteCasillas() {
        assertEquals(37, wheel.pockets());
        assertEquals(18, wheel.pockets(Outcome.ROJO));
        assertEquals(18, wheel.pockets(Outcome.NEGRO));
        assertEquals(1, wheel.pockets(Outcome.VERDE));
    }

    @Test
    void elVerdePagaMuchoMasQueElRojoPorqueEsMuchoMasRaro() {
        assertTrue(wheel.payout(Outcome.VERDE) > wheel.payout(Outcome.ROJO) * 15,
                "el verde deberia pagar al menos 15 veces mas");
        // Con una sola casilla verde de 37, el pago justo seria 37x; con el 2.7%
        // de ventaja se queda en 36x, que sigue siendo el gran golpe de la rueda.
        assertEquals(36.0, wheel.payout(Outcome.VERDE), 1e-9);
        // El pago justo del rojo seria 37/18 = 2.055 y queda en 2.00.
        assertEquals(2.0, wheel.payout(Outcome.ROJO), 1e-9);
        assertEquals(2.0, wheel.payout(Outcome.NEGRO), 1e-9);
        assertTrue(wheel.payout(Outcome.VERDE) > 35.0);
    }

    @Test
    void lasTresApuestasTienenExactamenteLaMismaVentaja() {
        for (Outcome outcome : Outcome.values()) {
            assertEquals(wheel.rtp(Outcome.ROJO), wheel.rtp(outcome), 1e-9,
                    "el color " + outcome + " no cuadra");
            assertTrue(wheel.rtp(outcome) < 1.0);
        }
    }

    @Test
    void laFrecuenciaDeCadaColorCoincideConSusCasillas() {
        Map<Outcome, Integer> hits = new EnumMap<>(Outcome.class);
        int samples = 300_000;
        for (int i = 0; i < samples; i++) {
            hits.merge(wheel.spin(), 1, Integer::sum);
        }
        for (Outcome outcome : Outcome.values()) {
            assertEquals(wheel.chance(outcome), (double) hits.getOrDefault(outcome, 0) / samples, 0.005,
                    "frecuencia del color " + outcome);
        }
    }

    @Test
    void unaRuedaConMasVerdesReparteElRiesgoSinCambiarLaVentaja() {
        ColorWheel generosa = new ColorWheel(16, 16, 5, 0.027);
        assertEquals(37, generosa.pockets());
        assertTrue(generosa.payout(Outcome.VERDE) < wheel.payout(Outcome.VERDE),
                "si el verde sale mas a menudo debe pagar menos");
        for (Outcome outcome : Outcome.values()) {
            assertTrue(generosa.rtp(outcome) <= 1.0);
        }
    }

    @Test
    void lasRuedasInvalidasSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> new ColorWheel(0, 18, 1, 0.027));
        assertThrows(IllegalArgumentException.class, () -> new ColorWheel(18, 18, 1, 1.5));
    }
}
