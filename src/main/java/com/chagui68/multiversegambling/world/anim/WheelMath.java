package com.chagui68.multiversegambling.world.anim;

/**
 * Geometry of a horizontal wheel: where a sector sits and how far a spin has to travel
 * to stop on a chosen one.
 *
 * <p>Free of Bukkit on purpose, so the spin of the roulette can be checked by tests
 * instead of by eye. Angles grow clockwise seen from above, the first sector sitting
 * on the {@code +Z} axis.</p>
 */
public final class WheelMath {

    /**
     * A full turn.
     */
    public static final double TAU = Math.PI * 2;

    private WheelMath() {
    }

    /**
     * Angle of sector {@code index} of {@code count}, evenly spread around the wheel.
     */
    public static double angleOf(int index, int count) {
        if (count <= 0) {
            return 0.0;
        }
        return TAU * Math.floorMod(index, count) / count;
    }

    /**
     * Folds any angle into {@code [0, TAU)}.
     */
    public static double normalize(double angle) {
        double wrapped = angle % TAU;
        return wrapped < 0 ? wrapped + TAU : wrapped;
    }

    /**
     * Arc a spin has to cover to go from one angle to another after a whole number of
     * turns, so the wheel never spins backwards and always lands on the target.
     */
    public static double sweep(double fromAngle, double targetAngle, int turns) {
        return TAU * Math.max(0, turns) + normalize(targetAngle - fromAngle);
    }

    /**
     * Ease out cubic: the ball leaves at full speed and creeps to a stop.
     */
    public static double ease(double progress) {
        double clamped = Math.max(0, Math.min(1, progress));
        double left = 1 - clamped;
        return 1 - left * left * left;
    }

    /**
     * X offset of a point at that angle and radius.
     */
    public static double x(double angle, double radius) {
        return radius * Math.sin(angle);
    }

    /**
     * Z offset of a point at that angle and radius.
     */
    public static double z(double angle, double radius) {
        return radius * Math.cos(angle);
    }
}
