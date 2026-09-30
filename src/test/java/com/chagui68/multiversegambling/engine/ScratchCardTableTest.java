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
    void theCardHasNineTilesAndValidSymbols() {
        List<Face> card = table.newCard();
        assertEquals(ScratchCardTable.CELLS, card.size());
        for (Face face : card) {
            assertTrue(face != null);
        }
    }

    @Test
    void threeOfAKindPayTheSymbolPrize() {
        for (Face face : Face.values()) {
            assertEquals(face.triple(), table.payout(List.of(face, face, face)), 1e-9, face.id());
        }
    }

    @Test
    void twoOfAKindGivePartOfTheStakeBack() {
        assertEquals(ScratchCardTable.PAIR_PAYOUT,
                table.payout(List.of(Face.CEREZA, Face.CEREZA, Face.CORONA)), 1e-9);
        assertEquals(ScratchCardTable.PAIR_PAYOUT,
                table.payout(List.of(Face.LIMON, Face.SIETE, Face.SIETE)), 1e-9);
    }

    @Test
    void threeDifferentOnesPayNothing() {
        assertEquals(0.0, table.payout(List.of(Face.CEREZA, Face.LIMON, Face.CAMPANA)), 1e-9);
    }

    @Test
    void theReturnIsCalibratedAroundNinetyTwoPercent() {
        double rtp = table.rtp();
        assertTrue(rtp < 1.0, "the card cannot return more than it takes, it was " + rtp);
        assertTrue(rtp > 0.85, "the card should not be ruinous, it was " + rtp);
        assertEquals(0.9215, rtp, 0.01, "la tabla esta calibrada al 92.2%");
    }

    @Test
    void halfOfTheCardsGiveSomethingBack() {
        double chance = table.hitChance();
        assertTrue(chance > 0.5, "scratching should pay off often, it was " + chance);
        assertTrue(chance < 0.9);
    }

    @Test
    void theSimulationConfirmsTheTheoreticalReturn() {
        int samples = 300_000;
        double returned = 0;
        for (int i = 0; i < samples; i++) {
            List<Face> card = table.newCard();
            // A player who always scratches the first three tiles.
            returned += table.payout(card.subList(0, ScratchCardTable.PICKS));
        }
        assertEquals(table.rtp(), returned / samples, 0.01);
    }

    @Test
    void revealingTheWrongNumberOfTilesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> table.payout(List.of(Face.CEREZA)));
    }
}
