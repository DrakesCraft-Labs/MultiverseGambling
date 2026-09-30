package com.freebuff.casino.engine;

/**
 * Matematica de los juegos de "destapar casillas" (Minas, Tablero de Bombas,
 * Torre). El multiplicador es siempre la inversa exacta de la probabilidad de
 * sobrevivir, recortada por la ventaja de la casa, asi que nunca se puede
 * explotar eligiendo el numero de minas.
 */
public final class MinesTable {

    private MinesTable() {
    }

    /**
     * Probabilidad de destapar {@code revealed} casillas seguras seguidas sin
     * tocar ninguna de las {@code mines} minas repartidas en {@code tiles}.
     */
    public static double survivalChance(int tiles, int mines, int revealed) {
        if (revealed < 0 || tiles <= 0 || mines < 0 || mines >= tiles) {
            return 0;
        }
        if (revealed > tiles - mines) {
            return 0;
        }
        double chance = 1.0;
        for (int i = 0; i < revealed; i++) {
            chance *= (double) (tiles - mines - i) / (tiles - i);
        }
        return chance;
    }

    /** Multiplicador de retirada tras destapar {@code revealed} casillas seguras. */
    public static double multiplier(int tiles, int mines, int revealed, double houseEdge) {
        if (revealed <= 0) {
            return 1.0;
        }
        double chance = survivalChance(tiles, mines, revealed);
        if (chance <= 0) {
            return 0;
        }
        return (1.0 - houseEdge) / chance;
    }

    /**
     * Multiplicador acumulado para el juego de torre: cada piso tiene
     * {@code tiles} casillas de las que {@code bombs} son mortales.
     */
    public static double towerMultiplier(int level, int tiles, int bombs, double houseEdge) {
        if (level <= 0) {
            return 1.0;
        }
        double safePerLevel = (double) (tiles - bombs) / tiles;
        double chance = Math.pow(safePerLevel, level);
        if (chance <= 0) {
            return 0;
        }
        return (1.0 - houseEdge) / chance;
    }

    /** Ventaja de la casa efectiva si se aplica redondeo hacia abajo. */
    public static double truncate(double multiplier) {
        return Math.floor(multiplier * 100.0) / 100.0;
    }
}
