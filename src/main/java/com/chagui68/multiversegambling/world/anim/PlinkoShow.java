package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * Plinko on the floor of an arena: a pyramid of pegs with a ball hopping down it, one
 * bounce per level, and a row of buckets at the far end that lights up where it lands.
 *
 * <p>The board lies flat on the ground and runs away from the watcher, which is what
 * makes a pyramid as wide as the arena fit in it. The bounces come from the same
 * provably fair rolls that decide the bucket, so the ball takes the real path and the
 * bucket it stops in is the one that pays.</p>
 */
public final class PlinkoShow extends ArenaShow {

    /** Frames spent on each level of the pyramid. */
    private static final int FRAMES_PER_LEVEL = 3;
    /** Level 0 sits this many blocks in front of the middle of the arena. */
    private static final int START_DEPTH = 6;
    /** Height of the ball over the board. */
    private static final double BALL_HEIGHT = 1.9;

    private final int[] positions;
    private final int rows;
    private final int bucket;
    private ItemDisplay ball;

    /**
     * @param rolls  uniform roll per bounce, in the order the ball meets them
     * @param bucket bucket the ball has to stop in
     * @param ticks  frames the drop lasts
     */
    public PlinkoShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                      double[] rolls, int bucket, int ticks) {
        super(plugin, stage, ticks);
        this.positions = path(rolls);
        this.rows = rolls.length;
        this.bucket = Math.max(0, Math.min(rows, bucket));
    }

    /**
     * Where the ball sits after each bounce, in blocks from the middle of the arena. The
     * last entry is the bucket it lands in, so the ball can never end anywhere else than
     * the bucket that the game pays for.
     */
    static int[] path(double[] rolls) {
        int[] positions = new int[rolls.length + 1];
        int rights = 0;
        for (int level = 0; level <= rolls.length; level++) {
            if (level > 0 && rolls[level - 1] < 0.5) {
                rights++;
            }
            positions[level] = 2 * rights - level;
        }
        return positions;
    }

    @Override
    protected void onStart() {
        drawBoard();
        ball = spawn(placeAt(0), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.SNOWBALL));
            display.setBillboard(Display.Billboard.CENTER);
            display.setGravity(false);
            display.setInvulnerable(true);
        });
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        double level = Math.min(rows, progress(elapsed) * rows);
        moveBall(placeAt(level));
        if (elapsed % FRAMES_PER_LEVEL == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) (0.9 + level / Math.max(1, rows)));
        }
    }

    @Override
    protected void onSettle() {
        moveBall(placeAt(rows));
        // The bucket that took the ball is left lit until the board is taken down.
        tile(positions[rows], 1, START_DEPTH - rows, Material.LIME_CONCRETE);
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.3f);
    }

    private void moveBall(Location location) {
        if (ball != null && ball.isValid()) {
            ball.teleport(location);
        }
    }

    /** Where the ball is after {@code level} bounces; fractions allowed, for smoothness. */
    private Location placeAt(double level) {
        int whole = (int) Math.floor(level);
        int next = Math.min(whole + 1, rows);
        double fraction = level - whole;
        double fromX = positions[whole];
        double toX = positions[next];
        return stage().at(fromX + (toX - fromX) * fraction, BALL_HEIGHT, START_DEPTH - level);
    }

    private void drawBoard() {
        for (int level = 0; level < rows; level++) {
            for (int peg = 0; peg <= level; peg++) {
                tile(2 * peg - level, 1, START_DEPTH - level, Material.LIGHT_GRAY_CONCRETE);
            }
        }
        // Buckets: the edges are the ones that pay silly money, like the menu says.
        for (int slot = 0; slot <= rows; slot++) {
            boolean edge = slot == 0 || slot == rows;
            tile(2 * slot - rows, 1, START_DEPTH - rows,
                    edge ? Material.GOLD_BLOCK : Material.GRAY_CONCRETE);
        }
    }
}
