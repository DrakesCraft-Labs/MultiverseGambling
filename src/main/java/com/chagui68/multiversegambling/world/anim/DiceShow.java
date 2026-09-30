package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * A line of dice on the floor of an arena, each one a white square with its pips raised on
 * top, so a hand of dice poker can be read from the road.
 *
 * <p>While the hand is being dealt the dice keep changing face and they settle one after
 * the other. The faces come from the provably fair rolls of the game: the show only
 * paints them.</p>
 */
public final class DiceShow extends ArenaShow {

    /** Pips of each face, as offsets inside the 3 x 3 square of the die. */
    private static final int[][][] PIPS = {
            {},                                                     // 0, unused
            {{0, 0}},                                               // 1
            {{-1, -1}, {1, 1}},                                     // 2
            {{-1, -1}, {0, 0}, {1, 1}},                             // 3
            {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}},                   // 4
            {{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}},           // 5
            {{-1, -1}, {-1, 0}, {-1, 1}, {1, -1}, {1, 0}, {1, 1}}   // 6
    };

    /** Blocks between the centres of two dice. */
    private static final int SPACING = 4;

    private final int[] faces;
    private final boolean[] settled;

    /**
     * @param faces the hand that was rolled, one value from 1 to 6 per die
     * @param ticks frames the throw lasts
     */
    public DiceShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int[] faces, int ticks) {
        super(plugin, stage, ticks);
        this.faces = faces.clone();
        this.settled = new boolean[faces.length];
    }

    @Override
    protected void onStart() {
        for (int die = 0; die < faces.length; die++) {
            drawDie(die, 1 + Rng.intBetween(0, 5));
        }
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        boolean changed = false;
        for (int die = 0; die < faces.length; die++) {
            if (elapsed >= settleFrame(die, duration)) {
                if (!settled[die]) {
                    settled[die] = true;
                    drawDie(die, faces[die]);
                    changed = true;
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.9f + die * 0.15f);
                }
            } else if (elapsed % 3 == 0) {
                // The tumble is repainted every third frame: fast enough to look random,
                // cheap enough not to hammer the arena with block updates.
                drawDie(die, 1 + Rng.intBetween(0, 5));
                changed = true;
            }
        }
        if (changed && elapsed % 2 == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, (float) (0.9 + progress(elapsed)));
        }
    }

    @Override
    protected void onSettle() {
        for (int die = 0; die < faces.length; die++) {
            drawDie(die, faces[die]);
        }
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.3f);
    }

    /** Die {@code index} freezes at that share of the throw, left to right. */
    private int settleFrame(int index, int duration) {
        return (int) (duration * (0.45 + 0.4 * index / Math.max(1.0, faces.length - 1.0)));
    }

    /** Paints one die: the white square and the raised pips of its face. */
    private void drawDie(int index, int face) {
        int center = centerOf(index);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                tile(center + dx, 0, dz, Material.WHITE_CONCRETE);
                tile(center + dx, 1, dz, Material.AIR);
            }
        }
        for (int[] pip : pips(face)) {
            tile(center + pip[0], 1, pip[1], Material.BLACK_CONCRETE);
        }
    }

    /** Offsets of the pips of a face inside the 3 x 3 square of its die. */
    static int[][] pips(int face) {
        return PIPS[Math.max(1, Math.min(6, face))];
    }

    private int centerOf(int index) {
        return (2 * index - (faces.length - 1)) * SPACING / 2;
    }
}
