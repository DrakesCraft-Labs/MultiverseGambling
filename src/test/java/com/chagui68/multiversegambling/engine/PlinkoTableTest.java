package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PlinkoTableTest {

    @Test
    void theBucketProbabilitiesAddUpToOne() {
        for (int rows : new int[] {8, 12, 16}) {
            double sum = 0;
            for (int bucket = 0; bucket <= rows; bucket++) {
                sum += PlinkoTable.bucketChance(rows, bucket);
            }
            assertEquals(1.0, sum, 1e-9, "rows=" + rows);
        }
    }

    @Test
    void theCentreBucketIsTheMostLikelyAndTheEdgesTheRarest() {
        int rows = 12;
        double center = PlinkoTable.bucketChance(rows, rows / 2);
        double edge = PlinkoTable.bucketChance(rows, 0);
        assertTrue(center > edge * 100, "the centre should be far more likely than the edge");
        assertEquals(edge, PlinkoTable.bucketChance(rows, rows), 1e-12, "la tabla es simetrica");
    }

    @Test
    void theTableNeverFavoursThePlayer() {
        for (int rows : new int[] {8, 12, 16}) {
            double[] table = PlinkoTable.multipliers(rows, 0.01, 10_000);
            double rtp = PlinkoTable.rtp(table, rows, 10_000);
            assertTrue(rtp <= 1.0, "rows=" + rows + " devolvia " + rtp);
        }
    }

    @Test
    void theMultiplierCapOnlyReducesTheReturn() {
        int rows = 16;
        double[] sinTope = PlinkoTable.multipliers(rows, 0.01, 100_000);
        double[] conTope = PlinkoTable.multipliers(rows, 0.01, 50);
        assertTrue(PlinkoTable.rtp(conTope, rows, 50) < PlinkoTable.rtp(sinTope, rows, 100_000));
        for (double value : conTope) {
            assertTrue(value <= 50.0);
        }
    }

    @Test
    void theMultipliersDecreaseTowardsTheCentre() {
        int rows = 12;
        double[] table = PlinkoTable.multipliers(rows, 0.01, 10_000);
        for (int i = 0; i < rows / 2; i++) {
            assertTrue(table[i] > table[i + 1], "bucket " + i + " should pay more than " + (i + 1));
        }
        assertTrue(table[rows / 2] < table[0]);
    }

    @Test
    void theFallAlwaysLandsInsideThePyramid() {
        int rows = 16;
        for (int i = 0; i < 20_000; i++) {
            int bucket = PlinkoTable.drop(rows);
            assertTrue(bucket >= 0 && bucket <= rows);
        }
    }

    @Test
    void theSimulatedDistributionResemblesTheBinomialOne() {
        int rows = 8;
        int samples = 200_000;
        int[] hits = new int[rows + 1];
        for (int i = 0; i < samples; i++) {
            hits[PlinkoTable.drop(rows)]++;
        }
        for (int bucket = 0; bucket <= rows; bucket++) {
            double expected = PlinkoTable.bucketChance(rows, bucket);
            double observed = (double) hits[bucket] / samples;
            assertEquals(expected, observed, 0.005, "cubo " + bucket);
        }
    }
}
