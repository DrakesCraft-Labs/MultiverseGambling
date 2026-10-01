package com.chagui68.multiversegambling.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geometry of the casino world: where the central plaza sits, where every game
 * pavilion goes and how the boulevards tie them together.
 *
 * <p>Deliberately free of Bukkit so the layout can be unit tested: the builder only
 * turns these coordinates into blocks. Pavilions are laid out on a square grid, filled
 * from the middle outwards, so the centre stays free for the plaza and the whole
 * thing remains symmetric no matter how many games are registered. Every row and every
 * column of the grid carries a boulevard, so any pavilion can be reached from any
 * other one, and the cells no game uses become gardens instead of empty grass.</p>
 *
 * <p>With the 21 games of the catalogue the grid is 5 x 5 and the whole casino spans
 * {@code 2 * extent()} = 408 blocks, so the default 500 block world keeps a green belt
 * round the edge.</p>
 */
public final class CasinoLayout {

    /**
     * Radius of the paved plaza around spawn, in blocks.
     */
    public static final int PLAZA_RADIUS = 36;
    /**
     * Half width of one pavilion; the platform is {@code 2 * ARENA_RADIUS + 1} wide.
     */
    public static final int ARENA_RADIUS = 20;
    /**
     * Radius of the dark stage in the middle of a pavilion, where the shows stand.
     */
    public static final int STAGE_RADIUS = 13;
    /**
     * Distance between two neighbouring pavilion centres.
     */
    public static final int SPACING = 84;
    /**
     * Width of the boulevards, kerbs included; always odd so a road has a middle line.
     */
    public static final int ROAD_WIDTH = 7;
    /**
     * Half width of the opening in the middle of each pavilion wall.
     */
    public static final int GATE_HALF_WIDTH = 3;
    /**
     * Grass left between the outermost pavilion and the edge of the casino, which is
     * where the ring road and the tree belt go.
     */
    public static final int MARGIN = 16;

    /**
     * One game pavilion.
     */
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

        /**
         * True when the block at these coordinates belongs to the platform.
         */
        public boolean contains(int x, int z) {
            return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
        }
    }

    /**
     * A cell of the grid that no game uses: it becomes a garden roundabout.
     */
    public record Garden(int centerX, int centerZ) {
    }

    /**
     * A straight piece of road along one axis.
     */
    public record Road(int fromX, int fromZ, int toX, int toZ) {

        public boolean alongX() {
            return fromZ == toZ;
        }

        public boolean alongZ() {
            return fromX == toX;
        }

        /**
         * True when the point lies on the middle line of this road.
         */
        public boolean passes(int x, int z) {
            if (alongX()) {
                return z == fromZ && x >= Math.min(fromX, toX) && x <= Math.max(fromX, toX);
            }
            return x == fromX && z >= Math.min(fromZ, toZ) && z <= Math.max(fromZ, toZ);
        }
    }

    /**
     * Side of a pavilion facing the plaza: its main gate, where the name of the game is
     * hung and where the shows turn to.
     */
    public enum Edge {
        NORTH, SOUTH, EAST, WEST
    }

    private final int size;
    private final int columns;
    private final int extent;
    private final List<Arena> arenas;
    private final List<Garden> gardens;
    private final Map<String, Arena> byGame;

    private CasinoLayout(int size, int columns, int extent, List<Arena> arenas, List<Garden> gardens) {
        this.size = size;
        this.columns = columns;
        this.extent = extent;
        this.arenas = List.copyOf(arenas);
        this.gardens = List.copyOf(gardens);
        this.byGame = new LinkedHashMap<>();
        for (Arena arena : arenas) {
            this.byGame.put(arena.gameId(), arena);
        }
    }

    /**
     * Lays out one pavilion per game.
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
        // single game, and grown until the grid really holds every pavilion (an odd grid
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
        List<Garden> gardens = new ArrayList<>();
        for (int i = gameIds.size(); i < cells.size(); i++) {
            int[] cell = cells.get(i);
            gardens.add(new Garden(cell[0] * SPACING, cell[1] * SPACING));
        }
        return new CasinoLayout(size, columns, extent, arenas, gardens);
    }

    /**
     * How many arenas a grid of that many columns holds, plaza centre excluded.
     */
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

    /**
     * Cells from the plaza to the outermost ring of the grid.
     */
    public int half() {
        return columns / 2;
    }

    /**
     * Blocks between spawn and the edge of the casino, margin included.
     */
    public int extent() {
        return extent;
    }

    /**
     * Half side of the square ring road that closes the grid: it runs half way through
     * the margin, outside every pavilion.
     */
    public int ringRoad() {
        return half() * SPACING + ARENA_RADIUS + MARGIN / 2;
    }

    public List<Arena> arenas() {
        return arenas;
    }

    /**
     * Grid cells left free by the catalogue, in the order they would be filled.
     */
    public List<Garden> gardens() {
        return gardens;
    }

    public Arena arena(String gameId) {
        return byGame.get(gameId);
    }

    /**
     * Side of a pavilion facing the plaza. A pavilion has a gate on every side; this
     * is the main one, the side a player walking from spawn arrives at.
     */
    public static Edge entrance(Arena arena) {
        if (arena.centerZ() != 0 && Math.abs(arena.centerZ()) >= Math.abs(arena.centerX())) {
            return arena.centerZ() > 0 ? Edge.NORTH : Edge.SOUTH;
        }
        return arena.centerX() > 0 ? Edge.WEST : Edge.EAST;
    }

    /**
     * The boulevards: one along every row and every column of the grid, from one side
     * of the ring road to the other, plus the four sides of the ring road itself.
     */
    public List<Road> roads() {
        int ring = ringRoad();
        List<Road> roads = new ArrayList<>();
        for (int line = -half(); line <= half(); line++) {
            int offset = line * SPACING;
            roads.add(new Road(-ring, offset, ring, offset));
            roads.add(new Road(offset, -ring, offset, ring));
        }
        roads.add(new Road(-ring, -ring, ring, -ring));
        roads.add(new Road(-ring, ring, ring, ring));
        roads.add(new Road(-ring, -ring, -ring, ring));
        roads.add(new Road(ring, -ring, ring, ring));
        return List.copyOf(roads);
    }
}
