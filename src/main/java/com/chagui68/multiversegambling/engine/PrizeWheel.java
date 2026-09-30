package com.chagui68.multiversegambling.engine;

import java.util.function.DoubleSupplier;

/**
 * Ruleta de la suerte: una rueda fisica de casillas.
 *
 * <p>Cada casilla tiene su multiplicador y todas son igual de probables, asi que el
 * retorno es simplemente la media. Eso hace imposible "colar" una tabla que pague
 * de mas sin darse cuenta: {@link #rtp()} lo delata y un test lo fija.</p>
 */
public final class PrizeWheel {

    /**
     * Rueda por defecto: 12 casillas, muchas de cero y un par de golpes grandes.
     * La media es 0.95, o sea un 95% de retorno.
     */
    private static final double[] DEFAULT = {0, 1.3, 0, 2.0, 0, 4.0, 0, 1.6, 0, 2.5, 0, 0};

    private final double[] pockets;

    public PrizeWheel(double... pockets) {
        if (pockets.length < 2) {
            throw new IllegalArgumentException("La rueda necesita al menos 2 casillas");
        }
        for (double pocket : pockets) {
            if (pocket < 0) {
                throw new IllegalArgumentException("Un multiplicador negativo no tiene sentido: " + pocket);
            }
        }
        this.pockets = pockets.clone();
    }

    public static PrizeWheel defaultWheel() {
        return new PrizeWheel(DEFAULT);
    }

    public int size() {
        return pockets.length;
    }

    public double multiplier(int index) {
        return pockets[Math.floorMod(index, pockets.length)];
    }

    public double[] pockets() {
        return pockets.clone();
    }

    /** Indice ganador: todas las casillas pesan lo mismo. */
    public int spin(DoubleSupplier uniforms) {
        double uniform = uniforms.getAsDouble();
        int index = (int) Math.floor(uniform * pockets.length);
        return Math.max(0, Math.min(pockets.length - 1, index));
    }

    public int spin() {
        return spin(Rng.generator()::nextDouble);
    }

    public double rtp() {
        double sum = 0;
        for (double pocket : pockets) {
            sum += pocket;
        }
        return sum / pockets.length;
    }

    public double best() {
        double best = 0;
        for (double pocket : pockets) {
            best = Math.max(best, pocket);
        }
        return best;
    }

    /** Probabilidad de que salga la casilla mas alta. */
    public double bestChance() {
        int count = 0;
        double best = best();
        for (double pocket : pockets) {
            if (pocket == best) {
                count++;
            }
        }
        return (double) count / pockets.length;
    }
}
