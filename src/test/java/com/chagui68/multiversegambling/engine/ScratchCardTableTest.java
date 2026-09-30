package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.ScratchCardTable.Face;
import java.util.List;

class ScratchCardTableTest {

    private final ScratchCardTable table = new ScratchCardTable();

    @Test
    void elCartonTieneNueveCasillasYLosSimbolosSonValidos() {
        List<Face> card = table.newCard();
        assertEquals(ScratchCardTable.CELLS, card.size());
        for (Face face : card) {
            assertTrue(face != null);
        }
    }

    @Test
    void tresIgualesPaganElPremioDelSimbolo() {
        for (Face face : Face.values()) {
            assertEquals(face.triple(), table.payout(List.of(face, face, face)), 1e-9, face.id());
        }
    }

    @Test
    void dosIgualesDevuelvenParteDeLaApuesta() {
        assertEquals(ScratchCardTable.PAIR_PAYOUT,
                table.payout(List.of(Face.CEREZA, Face.CEREZA, Face.CORONA)), 1e-9);
        assertEquals(ScratchCardTable.PAIR_PAYOUT,
                table.payout(List.of(Face.LIMON, Face.SIETE, Face.SIETE)), 1e-9);
    }

    @Test
    void tresDistintosNoPaganNada() {
        assertEquals(0.0, table.payout(List.of(Face.CEREZA, Face.LIMON, Face.CAMPANA)), 1e-9);
    }

    @Test
    void elRetornoEstaCalibradoAlrededorDelNoventaYDosPorCiento() {
        double rtp = table.rtp();
        assertTrue(rtp < 1.0, "el carton no puede devolver mas de lo que cobra, era " + rtp);
        assertTrue(rtp > 0.85, "el carton no deberia ser ruinoso, era " + rtp);
        assertEquals(0.9215, rtp, 0.01, "la tabla esta calibrada al 92.2%");
    }

    @Test
    void laMitadDeLosCartonesDevuelvenAlgo() {
        double chance = table.hitChance();
        assertTrue(chance > 0.5, "rascar deberia premiar a menudo, era " + chance);
        assertTrue(chance < 0.9);
    }

    @Test
    void laSimulacionConfirmaElRetornoTeorico() {
        int samples = 300_000;
        double returned = 0;
        for (int i = 0; i < samples; i++) {
            List<Face> card = table.newCard();
            // Un jugador que siempre destapa las tres primeras casillas.
            returned += table.payout(card.subList(0, ScratchCardTable.PICKS));
        }
        assertEquals(table.rtp(), returned / samples, 0.01);
    }

    @Test
    void destaparUnNumeroIncorrectoDeCasillasSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> table.payout(List.of(Face.CEREZA)));
    }
}
