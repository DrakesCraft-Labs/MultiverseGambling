package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A bet in items must pay, on average, exactly what the same bet in money pays: the
 * fraction of an item is a chance, never a hidden cut.
 */
class ItemMathTest {

    @Test
    void wholePayoutsAreNeverRounded() {
        assertEquals(20, ItemMath.roundByChance(20.0, 0.0));
        assertEquals(20, ItemMath.roundByChance(20.0, 0.999));
        assertEquals(0, ItemMath.roundByChance(0.0, 0.0));
        assertEquals(0, ItemMath.roundByChance(-3.0, 0.0));
    }

    @Test
    void theFractionIsPaidByChance() {
        assertEquals(20, ItemMath.roundByChance(19.6, 0.10), "a roll under 0.6 pays the extra item");
        assertEquals(19, ItemMath.roundByChance(19.6, 0.75), "a roll over 0.6 does not");
        assertEquals(0.6, ItemMath.extraChance(19.6), 1e-9);
        assertEquals(0.0, ItemMath.extraChance(19.0), 1e-9);
    }

    @Test
    void theAveragePayoutIsExact() {
        // Every roll from 0 to 1 in small even steps: the mean must match the payout.
        for (double units : new double[]{0.4, 1.96, 19.6, 98.02, 7.0}) {
            int steps = 100000;
            double total = 0;
            for (int i = 0; i < steps; i++) {
                total += ItemMath.roundByChance(units, (i + 0.5) / steps);
            }
            assertEquals(units, total / steps, 1e-4, "average of " + units);
        }
    }

    @Test
    void payoutsAreSplitIntoStacks() {
        assertArrayEquals(new int[]{64, 64, 2}, ItemMath.stacks(130, 64));
        assertArrayEquals(new int[]{1, 1, 1}, ItemMath.stacks(3, 1));
        assertArrayEquals(new int[]{16}, ItemMath.stacks(16, 16));
        assertArrayEquals(new int[0], ItemMath.stacks(0, 64));
    }

    @Test
    void blockedItemsMatchWholeFamilies() {
        List<String> blocked = List.of("*SHULKER_BOX", "*BUNDLE", "NETHERITE_*", "ELYTRA");
        assertTrue(ItemMath.blocked("SHULKER_BOX", blocked));
        assertTrue(ItemMath.blocked("RED_SHULKER_BOX", blocked));
        assertTrue(ItemMath.blocked("bundle", blocked));
        assertTrue(ItemMath.blocked("NETHERITE_SWORD", blocked));
        assertTrue(ItemMath.blocked("ELYTRA", blocked));
        assertFalse(ItemMath.blocked("DIAMOND", blocked));
        assertFalse(ItemMath.blocked("CHEST", blocked));
    }
}
