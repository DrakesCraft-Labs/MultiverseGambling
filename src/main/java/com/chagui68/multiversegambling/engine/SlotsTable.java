package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three reel slots. The symbol table is configurable and the theoretical return is
 * computed exactly, so {@code /mvgam rtp} can prove how much the slot configured by
 * the administrator really pays.
 */
public final class SlotsTable {

    /**
     * One reel symbol: look, frequency and what it pays.
     */
    public record Symbol(String id, String glyph, double weight, double triple, double pair) {

        public static Symbol of(String id, String glyph, double weight, double triple) {
            return new Symbol(id, glyph, weight, triple, 0);
        }
    }

    /**
     * Default table: frequent small prizes and a rare jackpot. Weights and payouts are
     * tuned by hand for an RTP close to 95%, which is what a real slot machine does.
     * {@link #rtp()} proves it and the test pins it.
     */
    // The glyphs are characters Minecraft can draw (emoji outside the basic plane render
    // as empty boxes), coloured like the symbol they stand for.
    public static final List<Symbol> DEFAULT = List.of(
            new Symbol("cherry", "&c\u25CF", 30, 7.0, 1.55),
            Symbol.of("lemon", "&e\u25CF", 25, 10.5),
            Symbol.of("bell", "&6\u266B", 18, 21.0),
            Symbol.of("diamond", "&b\u25C6", 12, 46.0),
            Symbol.of("seven", "&c&l7", 8, 105.0),
            Symbol.of("star", "&e\u2605", 4, 265.0),
            Symbol.of("crown", "&6\u265B", 2, 600.0));

    private final List<Symbol> symbols;
    private final Map<String, Symbol> byId = new LinkedHashMap<>();
    private final WeightedTable<Symbol> table = WeightedTable.of();

    public SlotsTable(List<Symbol> symbols) {
        if (symbols.size() < 2) {
            throw new IllegalArgumentException("At least 2 symbols are needed");
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
     * Spins the reels with an external source of uniforms. That is what lets the machine
     * be fed with the provably fair randomness of the casino.
     */
    public List<Symbol> spin(java.util.function.DoubleSupplier uniforms) {
        List<Symbol> reels = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            reels.add(table.roll(uniforms.getAsDouble()));
        }
        return reels;
    }

    /**
     * Multiplier paid for a combination of 3 reels.
     */
    public double payout(List<Symbol> reels) {
        if (reels.size() != 3) {
            throw new IllegalArgumentException("3 reels are expected");
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
     * Exact theoretical return to the player. Verified in the tests: if somebody sets a
     * table that returns more than it takes in, the test shouts about it.
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

    /**
     * Chance of every symbol, to show in the menu.
     */
    public double chanceOf(String id) {
        Symbol symbol = byId.get(id);
        return symbol == null ? 0 : table.probability(symbol);
    }
}
