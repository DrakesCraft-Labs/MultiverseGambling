package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * A slot machine painted on an arena: a wall of three reels, three symbols tall, with a
 * payline across the middle. The reels flash through the symbol table and stop one
 * after another, left to right, leaving the result on the payline.
 *
 * <p>The combination is decided by the provably fair generator before the reels start;
 * this show only paints it. The wall is built when the spin starts and taken down when
 * it ends.</p>
 */
public final class SlotReelsShow extends ArenaShow {

    /**
     * Column offset of each reel, in blocks from the middle of the arena.
     */
    private static final int[] COLUMNS = {-2, 0, 2};
    /**
     * Row offsets of the three symbols each reel shows.
     */
    private static final int[] ROWS = {1, 2, 3};
    private static final int HALF_WIDTH = 4;
    private static final int HEIGHT = 4;

    private final List<Material> result;
    private final List<Material> pool;
    private final Material[][] reels = new Material[3][3];
    private final boolean[] stopped = new boolean[3];
    private boolean highlighted;

    /**
     * @param result block of each winning symbol, one per reel
     * @param pool   blocks to flash through while the reels spin
     * @param ticks  frames the spin lasts
     */
    public SlotReelsShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                         List<Material> result, List<Material> pool, int ticks) {
        super(plugin, stage, ticks);
        this.result = List.copyOf(result);
        this.pool = List.copyOf(pool);
    }

    @Override
    protected void onStart() {
        drawCabinet();
        for (int reel = 0; reel < 3; reel++) {
            for (int row = 0; row < 3; row++) {
                reels[reel][row] = random();
            }
        }
        drawReels();
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        boolean changed = false;
        for (int reel = 0; reel < 3; reel++) {
            if (elapsed >= stopFrame(reel, duration)) {
                if (!stopped[reel]) {
                    stopped[reel] = true;
                    reels[reel] = new Material[]{random(), symbolOf(reel), random()};
                    changed = true;
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.9f + reel * 0.2f);
                }
            } else {
                for (int row = 0; row < 3; row++) {
                    reels[reel][row] = random();
                }
                changed = true;
            }
        }
        // Once the last reel is down, the rest of it is left blurred, so the winning
        // combination reads at a glance and the payline stands out.
        if (!highlighted && stopped[0] && stopped[1] && stopped[2]) {
            highlighted = true;
            for (int reel = 0; reel < 3; reel++) {
                reels[reel][0] = Material.LIGHT_GRAY_CONCRETE;
                reels[reel][2] = Material.LIGHT_GRAY_CONCRETE;
            }
            changed = true;
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.3f);
        }
        if (changed) {
            drawReels();
        }
        if (elapsed % 2 == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) (0.9 + progress(elapsed)));
        }
    }

    @Override
    protected void onSettle() {
        for (int reel = 0; reel < 3; reel++) {
            reels[reel] = new Material[]{Material.LIGHT_GRAY_CONCRETE, symbolOf(reel),
                    Material.LIGHT_GRAY_CONCRETE};
        }
        highlighted = true;
        drawReels();
    }

    /**
     * Reel {@code index} freezes at that share of the spin, left to right.
     */
    private int stopFrame(int index, int duration) {
        return (int) (duration * (0.55 + 0.15 * index));
    }

    private Material symbolOf(int reel) {
        return reel < result.size() ? result.get(reel) : Material.LIGHT_GRAY_CONCRETE;
    }

    private Material random() {
        if (pool.isEmpty()) {
            return Material.LIGHT_GRAY_CONCRETE;
        }
        return pool.get(Rng.intBetween(0, pool.size() - 1));
    }

    private void drawCabinet() {
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
            for (int dy = 0; dy <= HEIGHT; dy++) {
                paint().set(stage().arena().centerX() + dx, stage().floorY() + 1 + dy,
                        stage().arena().centerZ(), dy == HEIGHT
                                ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE);
            }
        }
    }

    private void drawReels() {
        int y = stage().floorY() + 1;
        int z = stage().arena().centerZ();
        for (int reel = 0; reel < 3; reel++) {
            for (int row = 0; row < 3; row++) {
                int x = stage().arena().centerX() + COLUMNS[reel];
                paint().set(x, y + ROWS[row], z, reels[reel][row]);
            }
        }
    }
}
