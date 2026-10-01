package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.world.CasinoLayout;

/**
 * Geometry of the plinko wall.
 *
 * <p>The board <b>stands up</b>: the apex is the highest peg, the pyramid widens as it
 * comes down and the buckets hang on a shelf at the bottom, all of it in the plane that
 * faces the audience. A flat board lying on the arena floor reads badly from the sides
 * and hides the fall, which is the whole point of plinko.</p>
 *
 * <p>Offsets are in blocks in the local frame of the stage: {@code x} runs along the
 * board, {@code y} grows upwards from the floor, and the board sits on {@code z = 0}, so
 * the audience in front of the pavilion gate sees it face on.</p>
 *
 * <p>The pitch of the grid shrinks with the number of rows, so a 20 row board is as wide
 * as a 6 row one and never grows out of the arena. Free of Bukkit on purpose: the shape
 * of the wall is checked by tests instead of by eye.</p>
 */
public record PlinkoBoard(int rows) {

    /** Horizontal pitch of a tall grid: 12 rows are this wide. */
    public static final double MAX_COLUMN = 0.65;
    /** Vertical pitch of a shallow grid, so a small board still fills some height. */
    public static final double MAX_ROW = 0.62;
    /** Height of the tallest wall, whatever the configuration asks for. */
    public static final double MAX_HEIGHT = 8.0;
    /** Height of the bucket shelf over the arena floor. */
    public static final double SHELF_HEIGHT = 1.1;
    /**
     * How far the widest wall may reach either side of the middle: wide enough to fill
     * the stage, narrow enough to be taken in at a glance from the watching spot.
     */
    public static final double MAX_HALF_WIDTH = Math.min(8.5, CasinoLayout.STAGE_RADIUS - 1.0);

    public PlinkoBoard {
        if (rows < 1) {
            throw new IllegalArgumentException("a plinko board needs at least one row, got " + rows);
        }
    }

    /**
     * Distance between two columns of the grid.
     */
    public double column() {
        return Math.min(MAX_COLUMN, MAX_HALF_WIDTH / rows);
    }

    /**
     * Distance between two rows of the grid. Divided by the levels plus the shelf, so
     * {@link #height()} never passes {@link #MAX_HEIGHT} plus the shelf whatever the
     * configuration asks for.
     */
    public double row() {
        return Math.min(MAX_ROW, MAX_HEIGHT / (rows + 1));
    }

    /**
     * Half of the wall at its widest, buckets included.
     */
    public double halfWidth() {
        return rows * column() + column();
    }

    /**
     * Height of the wall over the arena floor.
     */
    public double height() {
        return SHELF_HEIGHT + (rows + 1) * row();
    }

    /**
     * Rows of the pyramid, one peg at the top and {@code rows} at the bottom.
     */
    public int levels() {
        return rows;
    }

    /**
     * How many pegs a level holds.
     */
    public int pegs(int level) {
        return level + 1;
    }

    /**
     * X of the peg {@code index} of a level, centred on the middle of the arena.
     */
    public double pegX(int level, int index) {
        return (2.0 * index - level) * column();
    }

    /**
     * Y of a level: level {@code 0} is the apex, the highest pegs of the wall.
     */
    public double levelY(int level) {
        return SHELF_HEIGHT + (rows - level) * row();
    }

    /**
     * X of the bucket {@code slot}, one more bucket than rows.
     */
    public double bucketX(int slot) {
        return (2.0 * slot - rows) * column();
    }

    /**
     * Y of the bucket shelf.
     */
    public double bucketY() {
        return SHELF_HEIGHT;
    }

    /**
     * Column the ball occupies after {@code level} bounces; fractions allowed, so a frame
     * can put the ball between two rows.
     */
    public double ballX(int[] path, double level) {
        int whole = (int) Math.floor(level);
        int next = Math.min(whole + 1, path.length - 1);
        double fraction = level - whole;
        return path[Math.min(whole, path.length - 1)] * column()
                + (path[next] - path[Math.min(whole, path.length - 1)]) * column() * fraction;
    }

    /**
     * Y of the ball after {@code level} bounces: it starts level with the apex and ends
     * on the shelf, fractions allowed so the fall looks continuous.
     */
    public double ballY(double level) {
        return SHELF_HEIGHT + (rows - level) * row();
    }

    /**
     * Where the ball sits after every bounce, in columns of the grid. The last entry is
     * the bucket it lands in, so the ball can never stop anywhere the game does not pay
     * for: the same rolls decide both.
     */
    public static int[] path(double[] rolls) {
        int[] path = new int[rolls.length + 1];
        int rights = 0;
        for (int level = 0; level <= rolls.length; level++) {
            if (level > 0 && rolls[level - 1] < 0.5) {
                rights++;
            }
            path[level] = 2 * rights - level;
        }
        return path;
    }

    /**
     * Bucket the same rolls end in: the number of bounces to the right.
     */
    public static int bucketOf(double[] rolls) {
        int bucket = 0;
        for (double roll : rolls) {
            if (roll < 0.5) {
                bucket++;
            }
        }
        return bucket;
    }
}
