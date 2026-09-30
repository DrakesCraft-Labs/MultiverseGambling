package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Crash as a tower: a column rises one block every time the multiplier doubles, changing
 * colour as it gets dangerous, and either stops in gold when the player cashes out or
 * bursts when the curve crashes.
 *
 * <p>The height is logarithmic on purpose, so a run that reaches 1,000x does not need a
 * thousand blocks. The game drives it frame by frame with {@link #climb(double)}, which
 * is where the real multiplier comes from.</p>
 */
public final class CrashTowerShow extends ArenaShow {

    /** Blocks the tower can climb: any more would not fit in the view. */
    private static final int MAX_LEVELS = 10;

    private final double maxMultiplier;
    private int levels;
    private boolean over;

    public CrashTowerShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                          double maxMultiplier, int ticks) {
        super(plugin, stage, ticks);
        this.maxMultiplier = Math.max(1.5, maxMultiplier);
    }

    @Override
    protected void onStart() {
        // A small podium for the tower to grow out of.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                tile(dx, 0, dz, Material.POLISHED_BLACKSTONE);
            }
        }
    }

    /** Raises the tower to the height the multiplier deserves right now. */
    public void climb(double multiplier) {
        if (over) {
            return;
        }
        int wanted = heightFor(multiplier);
        if (wanted <= levels) {
            return;
        }
        for (int level = levels; level < wanted; level++) {
            tile(0, level + 1, 0, colourFor(multiplier));
        }
        levels = wanted;
        playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, (float) Math.min(2.0, 0.9 + levels * 0.1));
    }

    /** The player got out in time: the tower is paid in gold where it stands. */
    public void cashOut() {
        if (over) {
            return;
        }
        over = true;
        for (int level = 0; level < Math.max(1, levels); level++) {
            tile(0, level + 1, 0, Material.GOLD_BLOCK);
        }
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    /** The curve burst: the tower is left as a scorched stump. */
    public void burst() {
        if (over) {
            return;
        }
        over = true;
        for (int level = 0; level < levels; level++) {
            tile(0, level + 1, 0, level < 2 ? Material.BLACK_CONCRETE : Material.ORANGE_CONCRETE);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    tile(dx, 0, dz, Material.BLACK_CONCRETE);
                }
            }
        }
        playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
    }

    /** One block per doubling of the multiplier, capped so it always stays in view. */
    private int heightFor(double multiplier) {
        return heightFor(multiplier, maxMultiplier);
    }

    /** Blocks the tower climbs for a multiplier, never below one nor above the cap. */
    static int heightFor(double multiplier, double maxMultiplier) {
        double clamped = Math.max(1.0, Math.min(Math.max(1.5, maxMultiplier), multiplier));
        int wanted = (int) Math.round(Math.log(clamped) / Math.log(2.0)) + 1;
        return Math.max(1, Math.min(MAX_LEVELS, wanted));
    }

    private Material colourFor(double multiplier) {
        if (multiplier >= 10) {
            return Material.RED_CONCRETE;
        }
        if (multiplier >= 3) {
            return Material.ORANGE_CONCRETE;
        }
        return multiplier >= 1.5 ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE;
    }
}
