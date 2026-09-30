package com.freebuff.casino.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class FairnessTest {

    @Test
    void elAzarVerificableEsDeterminista() {
        String server = "secreto-de-prueba";
        String client = "jugador-1";
        double first = ProvablyFair.roll(server, client, 7, 0);
        assertEquals(first, ProvablyFair.roll(server, client, 7, 0), 0.0);
        assertNotEquals(first, ProvablyFair.roll(server, client, 8, 0), "otro nonce, otra tirada");
        assertNotEquals(first, ProvablyFair.roll(server, client, 7, 1), "otro cursor, otra tirada");
    }

    @Test
    void lasTiradasEstanEnRangoYUnoSeRepite() {
        String server = ProvablyFair.randomSeed();
        Map<Long, Boolean> seen = new HashMap<>();
        for (long nonce = 0; nonce < 5_000; nonce++) {
            double value = ProvablyFair.roll(server, "cliente", nonce, 0);
            assertTrue(value >= 0 && value < 1, "fuera de rango: " + value);
            seen.put((long) (value * 1_000_000), true);
        }
        // Con 5000 tiradas al microsegundo, esperamos mucha variedad.
        assertTrue(seen.size() > 4_900, "las tiradas parecen poco uniformes: " + seen.size());
    }

    @Test
    void elHashDelSecretoPermiteAuditar() {
        String seed = ProvablyFair.randomSeed();
        assertEquals(64, seed.length(), "la semilla deberia ser de 256 bits en hex");
        String hash = ProvablyFair.sha256(seed);
        assertEquals(64, hash.length());
        assertEquals(hash, ProvablyFair.sha256(seed), "el hash debe ser estable");
        assertNotEquals(hash, ProvablyFair.sha256(seed + "x"));
    }

    @Test
    void laDistribucionDeLasTiradasEsUniforme() {
        String server = ProvablyFair.randomSeed();
        int[] buckets = new int[10];
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            int bucket = (int) (ProvablyFair.roll(server, "c", i, 0) * 10);
            buckets[Math.min(9, bucket)]++;
        }
        for (int bucket = 0; bucket < 10; bucket++) {
            double share = (double) buckets[bucket] / samples;
            assertEquals(0.1, share, 0.01, "bucket " + bucket);
        }
    }

    @Test
    void lasTiradasMultiplesSonIndependientesYReproducibles() {
        String server = ProvablyFair.randomSeed();
        double[] many = ProvablyFair.rollMany(server, "c", 3, 5);
        assertEquals(5, many.length);
        for (int i = 0; i < 5; i++) {
            assertEquals(ProvablyFair.roll(server, "c", 3, i), many[i], 0.0);
        }
    }

    @Test
    void rollIntRespetaElLimite() {
        String server = ProvablyFair.randomSeed();
        for (int i = 0; i < 10_000; i++) {
            int value = ProvablyFair.rollInt(server, "c", i, 0, 37);
            assertTrue(value >= 0 && value < 37);
        }
        assertThrows(IllegalArgumentException.class, () -> ProvablyFair.rollInt(server, "c", 0, 0, 0));
    }

    @Test
    void laTablaPonderadaRespetaLosPesos() {
        // Ojo: no se puede encadenar sobre of() porque Java no propaga el tipo
        // hasta la asignacion; se construye paso a paso.
        WeightedTable<String> table = new WeightedTable<>();
        table.add("comun", 80).add("raro", 19).add("mitico", 1);
        assertEquals(100.0, table.totalWeight(), 1e-9);
        assertEquals(0.8, table.probability("comun"), 1e-9);

        Map<String, Integer> hits = new HashMap<>();
        int samples = 200_000;
        for (int i = 0; i < samples; i++) {
            hits.merge(table.roll(), 1, Integer::sum);
        }
        assertEquals(0.80, (double) hits.getOrDefault("comun", 0) / samples, 0.005);
        assertEquals(0.19, (double) hits.getOrDefault("raro", 0) / samples, 0.005);
        assertEquals(0.01, (double) hits.getOrDefault("mitico", 0) / samples, 0.002);
    }

    @Test
    void losPesosInvalidosSeIgnoranYLaTablaVaciaFallaAlGirar() {
        WeightedTable<String> table = new WeightedTable<>();
        table.add("valido", 5).add("cero", 0).add("negativo", -3);
        assertEquals(1, table.size());
        assertEquals(1.0, table.probability("valido"), 1e-9);
        WeightedTable<String> empty = WeightedTable.of();
        assertTrue(empty.isEmpty());
        assertThrows(IllegalStateException.class, empty::roll);
    }
}
