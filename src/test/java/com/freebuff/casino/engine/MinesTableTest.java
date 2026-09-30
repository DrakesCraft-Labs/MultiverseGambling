package com.freebuff.casino.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MinesTableTest {

    private static final double EDGE = 0.01;

    @Test
    void laProbabilidadDeSupervivenciaSigueLaHipergeometrica() {
        // Con 25 casillas y 3 minas, la primera eleccion es segura con 22/25.
        assertEquals(22.0 / 25.0, MinesTable.survivalChance(25, 3, 1), 1e-12);
        // La segunda, condicionada a haber sobrevivido, es 21/24.
        assertEquals((22.0 / 25.0) * (21.0 / 24.0), MinesTable.survivalChance(25, 3, 2), 1e-12);
        // Limpiar el tablero entero es tan raro como acertar las 3 minas de golpe:
        // 1 / C(25,3) = 1/2300.
        assertEquals(1.0 / 2300.0, MinesTable.survivalChance(25, 3, 22), 1e-12);
    }

    @Test
    void conVeinticuatroMinasSoloQuedaUnaCasillaBuena() {
        assertEquals(1.0 / 25.0, MinesTable.survivalChance(25, 24, 1), 1e-12);
    }

    @Test
    void masMinasSignificaMejorPagoParaElMismoNumeroDeAciertos() {
        double pocas = MinesTable.multiplier(25, 1, 5, EDGE);
        double medias = MinesTable.multiplier(25, 5, 5, EDGE);
        double muchas = MinesTable.multiplier(25, 20, 5, EDGE);
        assertTrue(pocas < medias, "con 1 mina deberia pagar menos que con 5");
        assertTrue(medias < muchas, "con 5 minas deberia pagar menos que con 20");
    }

    @Test
    void elMultiplicadorSiempreEsRentableParaLaCasa() {
        // Para cualquier configuracion, multiplicador * probabilidad = 1 - edge.
        for (int mines = 1; mines <= 24; mines++) {
            int maxReveals = 25 - mines;
            for (int revealed = 1; revealed <= maxReveals; revealed++) {
                double chance = MinesTable.survivalChance(25, mines, revealed);
                double multiplier = MinesTable.multiplier(25, mines, revealed, EDGE);
                assertEquals(1.0 - EDGE, chance * multiplier, 1e-9,
                        "minas=" + mines + " destapadas=" + revealed);
            }
        }
    }

    @Test
    void sinDestaparNadaNoSeGanaNada() {
        assertEquals(1.0, MinesTable.multiplier(25, 3, 0, EDGE), 1e-12);
    }

    @Test
    void laTorreAplicaElMultiplicadorPorPiso() {
        // 4 casillas con 1 bomba: 3/4 por piso.
        assertEquals((1 - EDGE) / (0.75 * 0.75), MinesTable.towerMultiplier(2, 4, 1, EDGE), 1e-9);
        assertTrue(MinesTable.towerMultiplier(9, 4, 1, EDGE) > MinesTable.towerMultiplier(8, 4, 1, EDGE));
    }

    @Test
    void lasEntradasImposiblesDevuelvenCero() {
        assertEquals(0.0, MinesTable.survivalChance(25, 25, 1));
        assertEquals(0.0, MinesTable.survivalChance(25, 3, 23));
        assertEquals(0.0, MinesTable.multiplier(25, 3, 23, EDGE));
    }
}
