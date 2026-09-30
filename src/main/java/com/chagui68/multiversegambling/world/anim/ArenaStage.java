package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.world.CasinoLayout;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The piece of the casino world that belongs to one game: the platform built by
 * {@code CasinoWorldManager} plus the maths to place a show on it.
 *
 * <p>Everything here is relative to the centre of the arena, so a show never has to
 * know where on the grid its arena happens to sit. The ground of the arena is at
 * {@link #floorY()}; a player standing on it has their feet one block higher.</p>
 */
public final class ArenaStage {

    /**
     * Where a show can drop a spectator when the game asks for it.
     */
    public static final int VIEW_DISTANCE = 11;

    /**
     * Looking down at a table lying on the ground: the wheel, the dice, the track.
     */
    public static final float TABLE_PITCH = 28.0f;

    /**
     * Looking up at a board or a tower standing on the arena, such as plinko or crash.
     */
    public static final float BOARD_PITCH = -8.0f;

    /**
     * How far a sound from the arena reaches.
     */
    public static final double SOUND_RADIUS = 64.0;

    private final World world;
    private final CasinoLayout.Arena arena;
    private final int floorY;

    public ArenaStage(World world, CasinoLayout.Arena arena, int floorY) {
        this.world = world;
        this.arena = arena;
        this.floorY = floorY;
    }

    public World world() {
        return world;
    }

    public CasinoLayout.Arena arena() {
        return arena;
    }

    /**
     * Y of the blocks the arena floor is made of.
     */
    public int floorY() {
        return floorY;
    }

    /**
     * Standing spot in the middle of the arena.
     */
    public Location center() {
        return at(0, 0, 0);
    }

    /**
     * Location relative to the centre of the arena; {@code dy = 0} is the ground.
     */
    public Location at(double dx, double dy, double dz) {
        return new Location(world,
                arena.centerX() + 0.5 + dx,
                floorY + 1 + dy,
                arena.centerZ() + 0.5 + dz);
    }

    /**
     * Where a spectator stands to watch a show, looking down at the platform.
     */
    public Location watcher(int distance) {
        return watcher(distance, TABLE_PITCH, 0, 1);
    }

    /**
     * Spectator spot for one of {@code total} watchers, spread evenly round the arena
     * and all of them facing its middle.
     */
    public Location watcher(int distance, int index, int total) {
        return watcher(distance, TABLE_PITCH, index, total);
    }

    /**
     * Spectator spot looking at a show with the given pitch: positive looks down at a
     * table on the ground, negative looks up at a board or a tower.
     */
    public Location watcher(int distance, float pitch) {
        return watcher(distance, pitch, 0, 1);
    }

    /**
     * One of {@code total} watchers of a show, spread evenly round the arena, all of
     * them facing its middle with the same pitch.
     */
    public Location watcher(int distance, float pitch, int index, int total) {
        double angle = WheelMath.angleOf(index, Math.max(1, total));
        double dx = WheelMath.x(angle, distance);
        double dz = WheelMath.z(angle, distance);
        Location spot = at(dx, 0, dz);
        // A yaw of 0 looks towards +Z, so the angle towards the middle is atan2(dx, -dz).
        spot.setYaw((float) Math.toDegrees(Math.atan2(dx, -dz)));
        spot.setPitch(pitch);
        return spot;
    }

    /**
     * Loads the chunks the arena sits on, so blocks and entities can be placed.
     */
    public void load() {
        int minChunkX = arena.minX() >> 4;
        int maxChunkX = arena.maxX() >> 4;
        int minChunkZ = arena.minZ() >> 4;
        int maxChunkZ = arena.maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ);
            }
        }
    }

    /**
     * Players gathered round the arena, the audience of a show.
     */
    public List<Player> nearbyPlayers() {
        return nearbyPlayers(SOUND_RADIUS);
    }

    /**
     * Players within that many blocks of the middle of the arena.
     */
    public List<Player> nearbyPlayers(double radius) {
        List<Player> players = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center(), radius, radius, radius)) {
            if (entity instanceof Player player) {
                players.add(player);
            }
        }
        return players;
    }
}
