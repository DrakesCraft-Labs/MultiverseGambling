package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tragaperras de 3 rodillos. La tabla de simbolos es configurable y el retorno
 * teorico se calcula de forma exacta, de modo que {@code /casino rtp} puede
 * demostrar cuanto paga el slot que el administrador tenga puesto.
 */
public final class SlotsTable {

    /** Un simbolo del rodillo: aspecto, frecuencia y lo que paga. */
    public record Symbol(String id, String glyph, double weight, double triple, double pair) {

        public static Symbol of(String id, String glyph, double weight, double triple) {
            return new Symbol(id, glyph, weight, triple, 0);
        }
    }

    /**
     * Tabla por defecto: premios pequenos frecuentes y un jackpot raro. Los pesos
     * y los pagos estan ajustados a mano para un RTP cercano al 95%, que es lo
     * normal en una tragaperras real. {@link #rtp()} lo demuestra y el test lo fija.
     */
    public static final List<Symbol> DEFAULT = List.of(
            new Symbol("cereza", "\uD83C\uDF52", 30, 7.0, 1.55),
            Symbol.of("limon", "\uD83C\uDF4B", 25, 10.5),
            Symbol.of("campana", "\uD83D\uDD14", 18, 21.0),
            Symbol.of("diamante", "\uD83D\uDC8E", 12, 46.0),
            Symbol.of("siete", "7\uFE0F\u20E3", 8, 105.0),
            Symbol.of("estrella", "\u2B50", 4, 265.0),
            Symbol.of("corona", "\uD83D\uDC51", 2, 600.0));

    private final List<Symbol> symbols;
    private final Map<String, Symbol> byId = new LinkedHashMap<>();
    private final WeightedTable<Symbol> table = WeightedTable.of();

    public SlotsTable(List<Symbol> symbols) {
        if (symbols.size() < 2) {
            throw new IllegalArgumentException("Hacen falta al menos 2 simbolos");
        }
        this.symbols = List.copyOf(symbols);
        for (Symbol symbol : this.symbols) {
            byId.put(symbol.id(), symbol);
            table.add(symbol, symbol.weight());
        }
    }

    public static SlotsTable defaults() {
        return new SlotsTable(DEFAULT);
    }

    public List<Symbol> symbols() {
        return symbols;
    }

    public List<Symbol> spin() {
        return spin(Rng.generator()::nextDouble);
    }

    /**
     * Gira los rodillos con una fuente de uniformes externa. Es lo que permite
     * alimentar la maquina con el azar verificable del casino.
     */
    public List<Symbol> spin(java.util.function.DoubleSupplier uniforms) {
        List<Symbol> reels = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            reels.add(table.roll(uniforms.getAsDouble()));
        }
        return reels;
    }

    /** Multiplicador pagado por una combinacion de 3 rodillos. */
    public double payout(List<Symbol> reels) {
        if (reels.size() != 3) {
            throw new IllegalArgumentException("Se esperan 3 rodillos");
        }
        Symbol a = reels.get(0);
        Symbol b = reels.get(1);
        Symbol c = reels.get(2);
        if (a.id().equals(b.id()) && b.id().equals(c.id())) {
            return a.triple();
        }
        if (a.id().equals(b.id()) || b.id().equals(c.id()) || a.id().equals(c.id())) {
            Symbol paired = a.id().equals(b.id()) ? a : (b.id().equals(c.id()) ? b : c);
            return paired.pair();
        }
        return 0;
    }

    /**
     * Retorno teorico exacto al jugador. Verificado en los tests: si alguien
     * pone una tabla que devuelve mas de lo que cobra, el test lo canta.
     */
    public double rtp() {
        double expected = 0;
        for (Symbol tripleSym : symbols) {
            double p = table.probability(tripleSym);
            expected += p * p * p * tripleSym.triple();
        }
        for (Symbol pairSym : symbols) {
            double p = table.probability(pairSym);
            if (pairSym.pair() <= 0) {
                continue;
            }
            double exactlyPair = 3 * p * p * (1 - p);
            expected += exactlyPair * pairSym.pair();
        }
        return expected;
    }

    /** Probabilidad de cada simbolo, para mostrar en el menu. */
    public double chanceOf(String id) {
        Symbol symbol = byId.get(id);
        return symbol == null ? 0 : table.probability(symbol);
    }
}
