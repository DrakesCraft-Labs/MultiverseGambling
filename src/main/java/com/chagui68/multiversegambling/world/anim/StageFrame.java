package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.world.CasinoLayout;

/**
 * The local frame of a pavilion stage: the axes every show is designed in.
 *
 * <p>A show is drawn as if the audience stood on {@code +z} looking towards {@code -z},
 * with {@code +x} on their right and {@code +y} up. The frame turns those local axes
 * so that {@code +z} points at the main gate of the pavilion, which is where a player
 * walking from the plaza arrives: every show greets its visitors face on.</p>
 *
 * <p>The turn is always a whole number of quarter turns, so local block offsets map to
 * world block offsets exactly. Free of Bukkit so the maths is covered by tests.</p>
 *
 * <p>Minecraft spins a display entity by {@code -yaw} round the vertical axis, so a
 * point at local angle {@code a} (measured from {@code +z} towards {@code +x}) ends up at
 * world angle {@code a - yaw}. A frame of {@code quarter} turns uses the yaw that sends
 * local {@code +z} to world angle {@code quarter * 90}.</p>
 */
public record StageFrame(int quarter) {

    public StageFrame {
        quarter = Math.floorMod(quarter, 4);
    }

    /**
     * The frame whose {@code +z} points out of the main gate of the pavilion.
     */
    public static StageFrame facing(CasinoLayout.Edge entrance) {
        return switch (entrance) {
            case SOUTH -> new StageFrame(0);   // gate on +z
            case EAST -> new StageFrame(1);    // gate on +x
            case NORTH -> new StageFrame(2);   // gate on -z
            case WEST -> new StageFrame(3);    // gate on -x
        };
    }

    /**
     * Yaw of an entity whose local {@code +z} must point along the frame's {@code +z}.
     */
    public float yaw() {
        return switch (quarter) {
            case 1 -> -90.0f;
            case 2 -> 180.0f;
            case 3 -> 90.0f;
            default -> 0.0f;
        };
    }

    /**
     * World X offset of a local point.
     */
    public double worldX(double x, double z) {
        return switch (quarter) {
            case 1 -> z;
            case 2 -> -x;
            case 3 -> -z;
            default -> x;
        };
    }

    /**
     * World Z offset of a local point.
     */
    public double worldZ(double x, double z) {
        return switch (quarter) {
            case 1 -> -x;
            case 2 -> -z;
            case 3 -> x;
            default -> z;
        };
    }

    /**
     * Yaw of somebody standing at a local spot and looking at another local spot.
     */
    public float lookYaw(double fromX, double fromZ, double toX, double toZ) {
        double dx = worldX(toX - fromX, toZ - fromZ);
        double dz = worldZ(toX - fromX, toZ - fromZ);
        // Minecraft looks along (-sin(yaw), cos(yaw)).
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
