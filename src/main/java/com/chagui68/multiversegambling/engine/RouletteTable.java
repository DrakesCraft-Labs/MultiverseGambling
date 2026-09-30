package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ruleta europea (un solo 0) o americana (0 y 00). Mantiene la rueda como una
 * lista de bolsillos para que el 00 americano no rompa ninguna cuenta.
 */
public final class RouletteTable {

    public enum Color {
        VERDE, ROJO, NEGRO
    }

    /**
     * Modalidades de apuesta y lo que paga cada una cuando acierta.
     *
     * <p>COLOR cubre solo rojo (seleccion 0) y negro (seleccion 1). El verde no es
     * una apuesta de color: en la ruleta de verdad el 0 se paga a caballo (NUMERO),
     * y por eso aqui no se puede "apostar al verde a 2x", que seria un timo.</p>
     */
    public enum Bet {
        NUMERO(36.0),
        COLOR(2.0),
        PARIDAD(2.0),
        MITAD(2.0),
        DOCENA(3.0),
        COLUMNA(3.0);

        private final double payout;

        Bet(double payout) {
            this.payout = payout;
        }

        /** Multiplicador sobre lo apostado, ya incluido el retorno del capital. */
        public double payout() {
            return payout;
        }
    }

    /** Representa el "00" en la ruleta americana. */
    public static final int DOUBLE_ZERO = 37;

    private static final Set<Integer> RED = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private final List<Integer> pockets;

    private RouletteTable(List<Integer> pockets) {
        this.pockets = List.copyOf(pockets);
    }

    public static RouletteTable european() {
        List<Integer> pockets = new ArrayList<>(37);
        for (int i = 0; i <= 36; i++) {
            pockets.add(i);
        }
        return new RouletteTable(pockets);
    }

    public static RouletteTable american() {
        List<Integer> pockets = new ArrayList<>(38);
        for (int i = 0; i <= 36; i++) {
            pockets.add(i);
        }
        pockets.add(DOUBLE_ZERO);
        return new RouletteTable(pockets);
    }

    public List<Integer> pockets() {
        return pockets;
    }

    public int pocketCount() {
        return pockets.size();
    }

    /** Numero de casillas que hacen ganar a cada tipo de apuesta. */
    private static int winningPockets(Bet bet) {
        return switch (bet) {
            case NUMERO -> 1;
            case COLOR, PARIDAD, MITAD -> 18;
            case DOCENA, COLUMNA -> 12;
        };
    }

    /**
     * Retorno teorico de una apuesta. La gracia de la ruleta es que <em>todas</em>
     * las apuestas comparten el mismo retorno: 36/37 en la europea (2.70% de
     * ventaja) y 36/38 en la americana (5.26%). El test lo comprueba apuesta a apuesta.
     */
    public double rtp(Bet bet) {
        return bet.payout() * winningPockets(bet) / pocketCount();
    }

    public double houseEdge(Bet bet) {
        return 1.0 - rtp(bet);
    }

    public int spin() {
        return Rng.pick(pockets);
    }

    public static boolean isGreen(int pocket) {
        return pocket == 0 || pocket == DOUBLE_ZERO;
    }

    public static boolean isRed(int pocket) {
        return RED.contains(pocket);
    }

    public static Color colorOf(int pocket) {
        if (isGreen(pocket)) {
            return Color.VERDE;
        }
        return isRed(pocket) ? Color.ROJO : Color.NEGRO;
    }

    public static String label(int pocket) {
        return pocket == DOUBLE_ZERO ? "00" : String.valueOf(pocket);
    }

    /**
     * Decide si la apuesta gana. {@code selection} significa:
     * <ul>
     *   <li>NUMERO: el propio numero (el 0 se paga a 36 y cubre el verde).</li>
     *   <li>COLOR: 0 = rojo, 1 = negro.</li>
     *   <li>PARIDAD: 0 = par, 1 = impar.</li>
     *   <li>MITAD: 0 = 1-18 (bajo), 1 = 19-36 (alto).</li>
     *   <li>DOCENA: 0 = 1-12, 1 = 13-24, 2 = 25-36.</li>
     *   <li>COLUMNA: 0, 1 o 2.</li>
     * </ul>
     *
     * @return el multiplicador a pagar, o 0 si se pierde.
     */
    public static double payoutOf(Bet bet, int selection, int result) {
        boolean wins = switch (bet) {
            case NUMERO -> selection == result;
            case COLOR -> !isGreen(result) && (selection == 0 ? isRed(result) : !isRed(result));
            case PARIDAD -> !isGreen(result) && (result % 2 == 0) == (selection == 0);
            case MITAD -> !isGreen(result) && (result <= 18) == (selection == 0);
            case DOCENA -> !isGreen(result) && (result - 1) / 12 == selection;
            case COLUMNA -> !isGreen(result) && result % 3 == (selection + 1) % 3;
        };
        return wins ? bet.payout() : 0.0;
    }

    public static String describeBet(Bet bet, int selection) {
        return switch (bet) {
            case NUMERO -> "Numero " + label(selection);
            case COLOR -> selection == 0 ? "Rojo" : "Negro";
            case PARIDAD -> selection == 0 ? "Par" : "Impar";
            case MITAD -> selection == 0 ? "1-18" : "19-36";
            case DOCENA -> "Docena " + (selection + 1) + " (" + (selection * 12 + 1) + "-" + (selection * 12 + 12) + ")";
            case COLUMNA -> "Columna " + (selection + 1);
        };
    }
}
