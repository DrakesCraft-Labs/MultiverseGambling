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
    void theStandardWheelHasThirtySevenPockets() {
        assertEquals(37, wheel.pockets());
        assertEquals(18, wheel.pockets(Outcome.RED));
        assertEquals(18, wheel.pockets(Outcome.BLACK));
        assertEquals(1, wheel.pockets(Outcome.GREEN));
    }

    @Test
    void greenPaysFarMoreThanRedBecauseItIsFarRarer() {
        assertTrue(wheel.payout(Outcome.GREEN) > wheel.payout(Outcome.RED) * 15,
                "green should pay at least 15 times more");
        // With a single green pocket out of 37 the fair payout would be 37x; with the 2.7%
        // edge it stays at 36x, which is still the big hit of the wheel.
        assertEquals(36.0, wheel.payout(Outcome.GREEN), 1e-9);
        // The fair payout for red would be 37/18 = 2.055 and it settles at 2.00.
        assertEquals(2.0, wheel.payout(Outcome.RED), 1e-9);
        assertEquals(2.0, wheel.payout(Outcome.BLACK), 1e-9);
        assertTrue(wheel.payout(Outcome.GREEN) > 35.0);
    }

    @Test
    void theThreeBetsHaveExactlyTheSameEdge() {
        for (Outcome outcome : Outcome.values()) {
            assertEquals(wheel.rtp(Outcome.RED), wheel.rtp(outcome), 1e-9,
                    "the colour " + outcome + " does not add up");
            assertTrue(wheel.rtp(outcome) < 1.0);
        }
    }

    @Test
    void theFrequencyOfEveryColourMatchesItsPockets() {
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
    void aWheelWithMoreGreensSpreadsTheRiskWithoutChangingTheEdge() {
        ColorWheel generosa = new ColorWheel(16, 16, 5, 0.027);
        assertEquals(37, generosa.pockets());
        assertTrue(generosa.payout(Outcome.GREEN) < wheel.payout(Outcome.GREEN),
                "if green comes up more often it must pay less");
        for (Outcome outcome : Outcome.values()) {
            assertTrue(generosa.rtp(outcome) <= 1.0);
        }
    }

    @Test
    void invalidWheelsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ColorWheel(0, 18, 1, 0.027));
        assertThrows(IllegalArgumentException.class, () -> new ColorWheel(18, 18, 1, 1.5));
    }
}
