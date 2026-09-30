package com.chagui68.multiversegambling.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geometry of the casino world: where the central plaza sits and where every game
 * arena goes.
 *
 * <p>Deliberately free of Bukkit so the layout can be unit tested: the builder only
 * turns these coordinates into blocks. Arenas are laid out on a square grid, filled
 * from the middle outwards, so the centre stays free for the plaza and the whole
 * thing remains symmetric no matter how many games are registered.</p>
 */
public final class CasinoLayout {

    /** Radius of the paved plaza around spawn, in blocks. */
    public static final int PLAZA_RADIUS = 30;
    /** Half width of one arena; the platform is {@code 2 * ARENA_RADIUS + 1} wide. */
    public static final int ARENA_RADIUS = 12;
    /** Distance between two neighbouring arena centres. */
    public static final int SPACING = 110;
    /** Width of the roads linking the plaza with the arenas. */
    public static final int ROAD_WIDTH = 3;
    /** Grass left free between the outermost arena and the world border. */
    public static final int MARGIN = 8;

    /** One game arena. */
    public record Arena(String gameId, int index, int centerX, int centerZ) {

        public int minX() {
            return centerX - ARENA_RADIUS;
        }

        public int maxX() {
            return centerX + ARENA_RADIUS;
        }

        public int minZ() {
            return centerZ - ARENA_RADIUS;
        }

        public int maxZ() {
            return centerZ + ARENA_RADIUS;
        }

        /** True when the block at these coordinates belongs to the platform. */
        public boolean contains(int x, int z) {
            return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
        }
    }

    /** A straight piece of road along one axis. */
    public record Road(int fromX, int fromZ, int toX, int toZ) {

        public boolean alongX() {
            return fromZ == toZ;
        }

        public boolean alongZ() {
            return fromX == toX;
        }
    }

    /**
     * Side of an arena the player walks in from. The entrance always faces the
     * plaza, so a sign placed there is visible while walking down the road.
     */
    public enum Edge {
        NORTH, SOUTH, EAST, WEST
    }

    private final int size;
    private final int columns;
    private final int extent;
    private final List<Arena> arenas;
    private final Map<String, Arena> byGame;

    private CasinoLayout(int size, int columns, int extent, List<Arena> arenas) {
        this.size = size;
        this.columns = columns;
        this.extent = extent;
        this.arenas = List.copyOf(arenas);
        this.byGame = new LinkedHashMap<>();
        for (Arena arena : arenas) {
            this.byGame.put(arena.gameId(), arena);
        }
    }

    /**
     * Lays out one arena per game.
     *
     * @param size    side of the square world, in blocks
     * @param gameIds game ids in the order they should be placed (centre first)
     * @throws IllegalArgumentException when the games do not fit in that world
     */
    public static CasinoLayout of(int size, List<String> gameIds) {
        if (size <= 0) {
            throw new IllegalArgumentException("world size must be positive, got " + size);
        }
        if (gameIds == null || gameIds.isEmpty()) {
            throw new IllegalArgumentException("at least one game is needed to build a casino");
        }

        // At least two columns, so the ring around the plaza always exists even with a
        // single game, and grown until the grid really holds every arena (an odd grid
        // gives up its centre cell to the plaza).
        int columns = Math.max(2, (int) Math.ceil(Math.sqrt(gameIds.size())));
        while (capacity(columns) < gameIds.size()) {
            columns++;
        }
        int half = columns / 2;
        List<int[]> cells = new ArrayList<>();
        for (int col = -half; col <= half; col++) {
            for (int row = -half; row <= half; row++) {
                if (col == 0 && row == 0) {
                    continue;
                }
                cells.add(new int[]{col, row});
            }
        }
        // Closest to the middle first, so the first registered games sit in the
        // heart of the world and the newest ones extend outwards.
        cells.sort(Comparator
                .comparingInt((int[] cell) -> Math.max(Math.abs(cell[0]), Math.abs(cell[1])))
                .thenComparingInt(cell -> Math.abs(cell[0]) + Math.abs(cell[1]))
                .thenComparingInt(cell -> cell[0])
                .thenComparingInt(cell -> cell[1]));

        if (gameIds.size() > cells.size()) {
            throw new IllegalArgumentException("a " + columns + "x" + columns
                    + " grid holds " + cells.size() + " arenas, but " + gameIds.size() + " games are registered");
        }

        int extent = half * SPACING + ARENA_RADIUS + MARGIN;
        if (extent * 2 > size) {
            throw new IllegalArgumentException("world size " + size
                    + " is too small: the layout needs at least " + (extent * 2) + " blocks");
        }

        List<Arena> arenas = new ArrayList<>(gameIds.size());
        for (int i = 0; i < gameIds.size(); i++) {
            int[] cell = cells.get(i);
            arenas.add(new Arena(gameIds.get(i), i, cell[0] * SPACING, cell[1] * SPACING));
        }
        return new CasinoLayout(size, columns, extent, arenas);
    }

    /** How many arenas a grid of that many columns holds, plaza centre excluded. */
    public static int capacity(int columns) {
        int side = 2 * (Math.max(2, columns) / 2) + 1;
        return side * side - 1;
    }

    public int size() {
        return size;
    }

    public int columns() {
        return columns;
    }

    /** Blocks between spawn and the outermost arena edge, margin included. */
    public int extent() {
        return extent;
    }

    public List<Arena> arenas() {
        return arenas;
    }

    public Arena arena(String gameId) {
        return byGame.get(gameId);
    }

    /**
     * Side of an arena the entrance is on: the one the road arrives at, which is
     * always the side facing the axis the arena was reached through.
     */
    public static Edge entrance(Arena arena) {
        if (arena.centerZ() != 0) {
            return arena.centerZ() > 0 ? Edge.NORTH : Edge.SOUTH;
        }
        return arena.centerX() > 0 ? Edge.WEST : Edge.EAST;
    }

    /** Roads, in two legs per arena: along the X axis and then along the Z axis. */
    public List<Road> roads() {
        List<Road> roads = new ArrayList<>(arenas.size() * 2);
        for (Arena arena : arenas) {
            if (arena.centerX() != 0) {
                roads.add(new Road(0, 0, arena.centerX(), 0));
            }
            if (arena.centerZ() != 0) {
                roads.add(new Road(arena.centerX(), 0, arena.centerX(), arena.centerZ()));
            }
        }
        return List.copyOf(roads);
    }
}
