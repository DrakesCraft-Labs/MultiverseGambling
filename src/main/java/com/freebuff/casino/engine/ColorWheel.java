package com.freebuff.casino.engine;

/**
 * La rueda de colores: la apuesta estrella del casino en grupo.
 *
 * <p>Es la rueda europea vista como tres colores. Lo importante es que el pago de
 * cada color sale de sus casillas reales, no de un numero inventado: con 37
 * casillas y un solo verde, el verde <em>tiene</em> que pagar unas 36 veces para
 * que la apuesta siga teniendo la misma ventaja que el rojo o el negro. Asi el
 * boton verde es emocionante sin ser una trampa.</p>
 */
public final class ColorWheel {

    public enum Outcome {
        ROJO, NEGRO, VERDE
    }

    private final int red;
    private final int black;
    private final int green;
    private final double houseEdge;

    public ColorWheel(int red, int black, int green, double houseEdge) {
        if (red < 1 || black < 1 || green < 1) {
            throw new IllegalArgumentException("Cada color necesita al menos una casilla");
        }
        if (houseEdge < 0 || houseEdge >= 1) {
            throw new IllegalArgumentException("Ventaja de la casa invalida: " + houseEdge);
        }
        this.red = red;
        this.black = black;
        this.green = green;
        this.houseEdge = houseEdge;
    }

    /** 18 rojas, 18 negras y 1 verde: la rueda europea. */
    public static ColorWheel european() {
        return new ColorWheel(18, 18, 1, 0.027);
    }

    public int pockets() {
        return red + black + green;
    }

    public int pockets(Outcome outcome) {
        return switch (outcome) {
            case ROJO -> red;
            case NEGRO -> black;
            case VERDE -> green;
        };
    }

    /** Probabilidad real de cada color. */
    public double chance(Outcome outcome) {
        return (double) pockets(outcome) / pockets();
    }

    /** Pago de cada color, recortado a 2 decimales a la baja. */
    public double payout(Outcome outcome) {
        double fair = (1.0 - houseEdge) / chance(outcome);
        return Math.floor(fair * 100.0) / 100.0;
    }

    /** El retorno es el mismo apostando a cualquier color. */
    public double rtp(Outcome outcome) {
        return chance(outcome) * payout(outcome);
    }

    public Outcome spin() {
        return spin(Rng.generator()::nextDouble);
    }

    /**
     * Gira con una tirada ya calculada en [0,1). Es lo que permite que la rueda
     * use el azar verificable del casino en lugar de un generador interno.
     */
    public Outcome spin(java.util.function.DoubleSupplier uniforms) {
        double uniform = Math.max(0, Math.min(0.999999999, uniforms.getAsDouble()));
        int target = 1 + (int) Math.floor(uniform * pockets());
        if (target <= red) {
            return Outcome.ROJO;
        }
        return target <= red + black ? Outcome.NEGRO : Outcome.VERDE;
    }

    public static String label(Outcome outcome) {
        return switch (outcome) {
            case ROJO -> "Rojo";
            case NEGRO -> "Negro";
            case VERDE -> "Verde";
        };
    }
}
