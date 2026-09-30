package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class PrizeWheelTest {

    private final PrizeWheel wheel = PrizeWheel.defaultWheel();

    @Test
    void theDefaultWheelHasTwelveTiles() {
        assertEquals(12, wheel.size());
        assertEquals(4.0, wheel.best(), 1e-9);
    }

    @Test
    void theReturnIsTheAverageOfTheTilesAndIsCalibrated() {
        assertEquals(0.95, wheel.rtp(), 1e-9, "the default wheel returns 95%");
        assertTrue(wheel.rtp() < 1.0, "a wheel that returns more than it takes is a bug");
    }

    @Test
    void theHighestTileIsRare() {
        assertEquals(1.0 / 12.0, wheel.bestChance(), 1e-9);
    }

    @Test
    void everyTileComesUpWithTheExpectedFrequency() {
        Map<Integer, Integer> hits = new HashMap<>();
        int samples = 240_000;
        for (int i = 0; i < samples; i++) {
            hits.merge(wheel.spin(), 1, Integer::sum);
        }
        for (int index = 0; index < wheel.size(); index++) {
            assertEquals(1.0 / wheel.size(), (double) hits.getOrDefault(index, 0) / samples, 0.005,
                    "casilla " + index);
        }
    }

    @Test
    void verifiableSpinsAreReproducible() {
        String serverSeed = ProvablyFair.randomSeed();
        for (long i = 0; i < 1_000; i++) {
            final long nonce = i;
            int a = wheel.spin(() -> ProvablyFair.roll(serverSeed, "cliente", nonce, 0));
            int b = wheel.spin(() -> ProvablyFair.roll(serverSeed, "cliente", nonce, 0));
            assertEquals(a, b, "the same roll must give the same pocket");
        }
    }

    @Test
    void aGenerousWheelRaisesTheReturnAndATestCatchesIt() {
        PrizeWheel cheat = new PrizeWheel(10, 10, 10, 10);
        assertTrue(cheat.rtp() > 1.0, "this wheel gives money away: the test must be able to see it");
    }

    @Test
    void invalidWheelsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PrizeWheel(1.0));
        assertThrows(IllegalArgumentException.class, () -> new PrizeWheel(1.0, -2.0));
    }
}
