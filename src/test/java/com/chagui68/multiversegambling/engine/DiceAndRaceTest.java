package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.HorseOdds.Runner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DiceAndRaceTest {

    @Test
    void theChanceOfWinningIsTheComplementOfTheTarget() {
        assertEquals(50.0, DiceTable.winChanceOver(50.0), 1e-9);
        assertEquals(25.0, DiceTable.winChanceOver(75.0), 1e-9);
        assertEquals(10.0, DiceTable.winChanceUnder(10.0), 1e-9);
    }

    @Test
    void thePayoutIsFairTrimmedByTheEdge() {
        assertEquals(1.98, DiceTable.payout(50.0, 0.01), 1e-9);
        assertEquals(9.9, DiceTable.payout(10.0, 0.01), 1e-9);
        assertEquals(0.99 * 100.0 / 98.0, DiceTable.payout(98.0, 0.01), 1e-9);
        assertEquals(0.0, DiceTable.payout(0, 0.01), 1e-9);
    }

    @Test
    void theRollIsAlwaysInRangeAndBehavesAsTheTargetSays() {
        for (int i = 0; i < 20_000; i++) {
            double roll = DiceTable.roll();
            assertTrue(roll >= 0 && roll < 100, "roll out of range: " + roll);
            assertEquals(roll, DiceTable.round2(roll), 1e-9, "the roll should have 2 decimals");
        }
    }

    @Test
    void bettingOverFiftyPercentWinsHalfTheTime() {
        int wins = 0;
        int samples = 200_000;
        for (int i = 0; i < samples; i++) {
            if (DiceTable.wins(DiceTable.roll(), 50.0, true)) {
                wins++;
            }
        }
        assertEquals(0.5, (double) wins / samples, 0.005);
    }

    @Test
    void theDiceReturnIsTheHouseMargin() {
        // Cashing out at target 2 (50% chance) pays 1.98 -> an EV of 0.99 per unit.
        double chance = DiceTable.winChanceOver(50.0) / 100.0;
        double payout = DiceTable.payout(50.0, 0.01);
        assertEquals(0.99, chance * payout, 1e-9);
    }

    @Test
    void coinFlipCannotPayAFlatDouble() {
        // Paying 2.0 on a fair coin gives the house no edge: it has to be
        // trimmed exactly like the dice.
        assertEquals(1.96, DiceTable.payout(50.0, 0.02), 1e-9);
        assertTrue(DiceTable.payout(50.0, 0.02) < 2.0);
        assertEquals(1.0 - 0.02, 0.5 * DiceTable.payout(50.0, 0.02), 1e-9,
                "the coin return is 1 minus the edge");
    }

    @Test
    void theHorseFieldHasPayoutsConsistentWithItsStrength() {
        List<Runner> field = HorseOdds.field(8, 0.01);
        assertEquals(8, field.size());
        double totalStrength = field.stream().mapToDouble(Runner::strength).sum();
        for (Runner runner : field) {
            // The return is per bet, not by adding up horses: whoever bets on any
            // of them must get 99% back on average, whoever wins.
            double chance = runner.strength() / totalStrength;
            assertEquals(0.99, chance * runner.odds(), 0.011, "horse " + runner.index());
        }
        // And the strongest horse pays less than the weakest one.
        Runner favourite = field.stream().max((a, b) -> Double.compare(a.strength(), b.strength())).orElseThrow();
        Runner outsider = field.stream().min((a, b) -> Double.compare(a.strength(), b.strength())).orElseThrow();
        assertTrue(favourite.odds() < outsider.odds(), "the favourite should pay less");
    }

    @Test
    void theWinnerIsPickedWithTheExpectedFrequency() {
        List<Runner> field = HorseOdds.field(4, 0.01);
        double totalStrength = field.stream().mapToDouble(Runner::strength).sum();
        Map<Integer, Integer> wins = new HashMap<>();
        int samples = 200_000;
        for (int i = 0; i < samples; i++) {
            wins.merge(HorseOdds.winner(field).index(), 1, Integer::sum);
        }
        for (Runner runner : field) {
            double expected = runner.strength() / totalStrength;
            double observed = (double) wins.getOrDefault(runner.index(), 0) / samples;
            assertEquals(expected, observed, 0.01, "caballo " + runner.index());
        }
    }

    @Test
    void theRaceSimulationReturnsOneFramePerStep() {
        List<double[]> frames = HorseOdds.simulate(HorseOdds.field(6, 0.01), 40);
        assertEquals(40, frames.size());
        for (double[] frame : frames) {
            assertEquals(6, frame.length);
        }
        // The positions never go backwards.
        for (int step = 1; step < frames.size(); step++) {
            for (int runner = 0; runner < 6; runner++) {
                assertTrue(frames.get(step)[runner] >= frames.get(step - 1)[runner]);
            }
        }
    }

    @Test
    void anEmptyFieldCannotBeDrawn() {
        assertThrows(IllegalStateException.class, () -> HorseOdds.winner(List.of()));
    }
}
