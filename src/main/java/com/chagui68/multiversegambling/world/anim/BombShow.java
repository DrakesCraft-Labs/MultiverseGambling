package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * The hot bomb as a prop: a block of TNT on a podium with a fuse running away from it
 * that shortens as the round goes on, and a scorch mark where it went off.
 *
 * <p>The fuse is the suspense of the game made visible: the round is decided by a roll on
 * every tick, so the fuse only shows how much of the drawn time is left. It is rebuilt
 * between explosions, so the podium is never left blown up.</p>
 */
public final class BombShow extends ArenaShow {

    /** Blocks of fuse at full length. */
    private static final int FUSE = 8;

    private boolean blasted;
    private int drawnFuse = -1;

    public BombShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int ticks) {
        super(plugin, stage, ticks);
    }

    @Override
    protected void onStart() {
        drawBomb();
        burn(1.0);
    }

    /** Shortens the fuse: {@code ratio} is the share of the drawn time that is left. */
    public void burn(double ratio) {
        if (blasted) {
            blasted = false;
            drawBomb();
            drawnFuse = -1;
        }
        double clamped = Math.max(0, Math.min(1, ratio));
        int length = (int) Math.ceil(clamped * FUSE);
        if (length == drawnFuse) {
            return;
        }
        for (int step = 1; step <= FUSE; step++) {
            tile(step + 1, 1, 0, step <= length ? Material.RED_CONCRETE : Material.AIR);
        }
        drawnFuse = length;
        playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.6f);
    }

    /** It went off on whoever was holding it. */
    public void blast() {
        blasted = true;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                tile(dx, 0, dz, Material.BLACK_CONCRETE);
                tile(dx, 1, dz, Material.AIR);
            }
        }
        tile(0, 1, 0, Material.ORANGE_CONCRETE);
        tile(0, 2, 0, Material.RED_CONCRETE);
        tile(1, 1, 0, Material.ORANGE_CONCRETE);
        tile(-1, 1, 0, Material.ORANGE_CONCRETE);
        tile(0, 1, 1, Material.ORANGE_CONCRETE);
        tile(0, 1, -1, Material.ORANGE_CONCRETE);
        drawnFuse = -1;
        playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
    }

    private void drawBomb() {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                tile(dx, 0, dz, Material.POLISHED_BLACKSTONE);
            }
        }
        tile(0, 1, 0, Material.TNT);
    }
}
