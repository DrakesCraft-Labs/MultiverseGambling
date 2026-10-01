package com.chagui68.multiversegambling.world.anim;

/**
 * Where the crash rocket is for a given multiplier.
 *
 * <p>The chart of a crash round is the multiplier against time, which grows without a
 * ceiling. The board in the pavilion is only so tall, so the rocket follows the
 * multiplier through {@code p = 1 - 1/m}: 0 at 1x, one half at 2x, 0.9 at 10x, and never
 * quite 1. The path is drawn from the multiplier alone, never from the time left, so
 * nobody can read the crash point off the speed of the rocket.</p>
 *
 * <p>Offsets are in blocks in the local frame of the stage: {@code x} runs left to right
 * in front of the audience and {@code y} up from the floor. Free of Bukkit on purpose.</p>
 */
public final class CrashCurve {

    /** Left edge of the chart, where every round starts. */
    public static final double LEFT = -6.0;
    /** Width of the chart. */
    public static final double WIDTH = 11.5;
    /** Height of the floor line of the chart. */
    public static final double BOTTOM = 1.6;
    /** Height of the chart. */
    public static final double HEIGHT = 9.0;
    /** How sharply the curve bends upwards. */
    private static final double BEND = 2.2;

    private CrashCurve() {
    }

    /**
     * Share of the chart covered at that multiplier, from 0 at 1x towards 1.
     */
    public static double progress(double multiplier) {
        return 1.0 - 1.0 / Math.max(1.0, multiplier);
    }

    public static double x(double multiplier) {
        return LEFT + WIDTH * progress(multiplier);
    }

    public static double y(double multiplier) {
        return BOTTOM + HEIGHT * Math.pow(progress(multiplier), BEND);
    }

    /**
     * Heading of the rocket at that multiplier, in radians from straight up towards
     * the right: the slope of the curve, so the nose always follows the chart.
     */
    public static double heading(double multiplier) {
        double p = progress(multiplier);
        double dx = WIDTH;
        double dy = HEIGHT * BEND * Math.pow(p, BEND - 1);
        return Math.atan2(dx, dy);
    }
}
