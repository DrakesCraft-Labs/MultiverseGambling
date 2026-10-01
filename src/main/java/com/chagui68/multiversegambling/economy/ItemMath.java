package com.chagui68.multiversegambling.economy;

import java.util.List;
import java.util.Locale;

/**
 * The arithmetic of a bet staked with items, free of Bukkit so it is covered by tests.
 *
 * <p>Items cannot be split, so a payout of 19.6 diamonds is paid as 19 diamonds plus a
 * 60% chance of a twentieth one. The extra item is decided by a provably fair roll, which
 * keeps the average payout exactly at {@code stake * multiplier}: rounding down would
 * quietly raise the house edge, and rounding to the nearest would sometimes lower it.</p>
 */
public final class ItemMath {

    private ItemMath() {
    }

    /**
     * Whole items to pay for {@code units}: the whole part, plus one more when the roll
     * falls under the fraction.
     *
     * @param roll uniform in {@code [0, 1)}
     */
    public static int roundByChance(double units, double roll) {
        if (!(units > 0)) {
            return 0;
        }
        double whole = Math.floor(units + 1e-9);
        double fraction = units - whole;
        int paid = (int) Math.min(Integer.MAX_VALUE, whole);
        if (fraction > 1e-9 && roll < fraction) {
            paid++;
        }
        return paid;
    }

    /**
     * Chance, from 0 to 1, of getting the extra item on top of the whole part.
     */
    public static double extraChance(double units) {
        if (!(units > 0)) {
            return 0;
        }
        double fraction = units - Math.floor(units + 1e-9);
        return fraction > 1e-9 ? fraction : 0;
    }

    /**
     * Splits a number of items into stacks no larger than {@code maxStack}.
     */
    public static int[] stacks(int amount, int maxStack) {
        int size = Math.max(1, maxStack);
        int full = amount / size;
        int rest = amount % size;
        int[] out = new int[full + (rest > 0 ? 1 : 0)];
        java.util.Arrays.fill(out, 0, full, size);
        if (rest > 0) {
            out[full] = rest;
        }
        return out;
    }

    /**
     * True when the item type matches one of the blocked patterns: an exact material
     * name, or {@code *_SUFFIX} / {@code PREFIX_*} for whole families (every shulker box,
     * every bundle).
     */
    public static boolean blocked(String material, List<String> patterns) {
        String name = material.toUpperCase(Locale.ROOT);
        for (String raw : patterns) {
            String pattern = raw.trim().toUpperCase(Locale.ROOT);
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.startsWith("*") && name.endsWith(pattern.substring(1))) {
                return true;
            }
            if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
            if (name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}
