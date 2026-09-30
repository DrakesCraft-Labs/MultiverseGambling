package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.SlotsTable.Symbol;
import java.util.List;

class SlotsTableTest {

    private final SlotsTable slots = SlotsTable.defaults();

    private Symbol symbol(String id) {
        return slots.symbols().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void tresIgualesPaganElPremioMayor() {
        Symbol corona = symbol("corona");
        assertEquals(corona.triple(), slots.payout(List.of(corona, corona, corona)), 1e-9);
    }

    @Test
    void dosCerezasPaganElPremioPequenoYPeroNoConOtrasMonedas() {
        Symbol cereza = symbol("cereza");
        Symbol limon = symbol("limon");
        assertEquals(cereza.pair(), slots.payout(List.of(cereza, cereza, limon)), 1e-9);
        assertEquals(cereza.pair(), slots.payout(List.of(limon, cereza, cereza)), 1e-9);
        assertEquals(0.0, slots.payout(List.of(limon, limon, cereza)), 1e-9);
    }

    @Test
    void sinCombinacionesNoSePagaNada() {
        Symbol cereza = symbol("cereza");
        Symbol limon = symbol("limon");
        Symbol campana = symbol("campana");
        assertEquals(0.0, slots.payout(List.of(cereza, limon, campana)), 1e-9);
    }

    @Test
    void elRetornoTeoricoEstaEnElRangoDeUnaTragaperrasReal() {
        double rtp = slots.rtp();
        assertTrue(rtp > 0.90, "el RTP no deberia ser ruinoso, era " + rtp);
        assertTrue(rtp < 0.99, "el RTP no puede acercarse a 1 o la casa no gana, era " + rtp);
        assertEquals(0.9475, rtp, 0.005, "la tabla por defecto esta calibrada al 94.75%");
    }

    @Test
    void losPremiosEstanOrdenadosPorRareza() {
        // De mas comun a mas raro: el premio debe ir subiendo sin excepcion.
        List<Symbol> sorted = slots.symbols().stream()
                .sorted((a, b) -> Double.compare(b.weight(), a.weight()))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i).triple() > sorted.get(i - 1).triple(),
                    sorted.get(i).id() + " (peso " + sorted.get(i).weight() + ") paga "
                            + sorted.get(i).triple() + " y es mas raro que " + sorted.get(i - 1).id()
                            + " (peso " + sorted.get(i - 1).weight() + ", paga "
                            + sorted.get(i - 1).triple() + ")");
        }
    }

    @Test
    void elJackpotEsRaroDeVerdad() {
        double chance = slots.chanceOf("corona");
        assertTrue(chance < 0.03, "el jackpot deberia salir menos del 3% de las veces");
        assertFalse(Double.isNaN(chance));
    }

    @Test
    void laTiradaDevuelveSiempreTresSimbolosValidos() {
        for (int i = 0; i < 2_000; i++) {
            List<Symbol> reels = slots.spin();
            assertEquals(3, reels.size());
            for (Symbol reel : reels) {
                assertTrue(slots.symbols().contains(reel));
            }
        }
    }
}
