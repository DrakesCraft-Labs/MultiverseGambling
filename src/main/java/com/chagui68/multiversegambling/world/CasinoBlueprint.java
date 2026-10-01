package com.chagui68.multiversegambling.world;

import com.chagui68.multiversegambling.world.board.BoardGrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Stairs;

/**
 * Every block of the casino, decided before a single one is placed.
 *
 * <p>The design is written into a map of positions grouped by chunk, so the build job
 * can then walk the world one chunk at a time, compare what is there with what should
 * be there and only touch what differs. Later layers win: the grass is painted first,
 * then the boulevards, then the plaza, the gardens and the pavilions on top.</p>
 *
 * <p>The style is a night casino in black, white and gold: dark stone boulevards with
 * white kerbs and lamp posts, a marble plaza with a lit fountain, and one walled
 * pavilion per game with gates on every side, gold trims, corner towers and bleachers,
 * its stage lit by invisible light blocks so the shows shine at any hour.</p>
 */
final class CasinoBlueprint {

    /**
     * Everything the build needs to know about a board played by clicking blocks.
     */
    record BoardPad(BoardGrid grid, int cells, Material tile, Material filler) {
    }

    /**
     * A tree to grow once the blocks are down.
     */
    record TreeSpot(int x, int z, TreeType type) {
    }

    /** How high above the ground the build clears old blocks. */
    static final int CLEAR_HEIGHT = 32;

    /** One accent colour per pavilion, cycled when more games than colours are registered. */
    static final Material[] ACCENTS = {
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.GREEN_CONCRETE, Material.CYAN_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE, Material.WHITE_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.BLACK_CONCRETE,
            Material.BROWN_CONCRETE, Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.CYAN_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.PURPLE_TERRACOTTA
    };

    private static final Material ROAD = Material.POLISHED_DEEPSLATE;
    private static final Material KERB = Material.SMOOTH_STONE;
    private static final Material DASH = Material.SMOOTH_QUARTZ;
    private static final Material[] FLOWERS = {
            Material.POPPY, Material.DANDELION, Material.ALLIUM, Material.AZURE_BLUET,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.PINK_TULIP
    };
    private static final TreeType[] AVENUE_TREES = {TreeType.CHERRY, TreeType.BIRCH, TreeType.CHERRY, TreeType.TREE};
    private static final TreeType[] FOREST_TREES = {TreeType.TREE, TreeType.BIRCH, TreeType.CHERRY, TreeType.TREE,
            TreeType.BIG_TREE};

    private final CasinoLayout layout;
    private final int floor;
    private final Map<Long, Map<Long, BlockData>> chunks = new HashMap<>();
    private final Map<Material, BlockData> plain = new HashMap<>();
    private final Map<Long, Integer> roadLayer = new HashMap<>();
    private final List<TreeSpot> trees = new ArrayList<>();

    private CasinoBlueprint(CasinoLayout layout, int floor) {
        this.layout = layout;
        this.floor = floor;
    }

    /**
     * Designs the whole casino.
     *
     * @param floor  Y of the ground blocks (the grass layer)
     * @param boards board of every game played on blocks, by game id
     */
    static CasinoBlueprint design(CasinoLayout layout, int floor, Map<String, BoardPad> boards) {
        CasinoBlueprint blueprint = new CasinoBlueprint(layout, floor);
        blueprint.boulevards();
        blueprint.streetFurniture();
        blueprint.plaza();
        for (CasinoLayout.Garden garden : layout.gardens()) {
            blueprint.garden(garden);
        }
        for (CasinoLayout.Arena arena : layout.arenas()) {
            blueprint.pavilion(arena, boards.get(arena.gameId()));
        }
        blueprint.outskirts();
        blueprint.wildflowers();
        return blueprint;
    }

    // ------------------------------------------------------------------ access

    int floor() {
        return floor;
    }

    List<TreeSpot> trees() {
        return trees;
    }

    /**
     * Blocks of one chunk, or an empty map when the design leaves it as plain grass.
     */
    Map<Long, BlockData> chunk(int chunkX, int chunkZ) {
        return chunks.getOrDefault(chunkKey(chunkX, chunkZ), Map.of());
    }

    int blocks() {
        int total = 0;
        for (Map<Long, BlockData> chunk : chunks.values()) {
            total += chunk.size();
        }
        return total;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF);
    }

    /**
     * Accent colour of a pavilion: also what its centre is paved with, which is how
     * the world report recognises a pavilion that was built.
     */
    static Material accent(CasinoLayout.Arena arena) {
        return ACCENTS[arena.index() % ACCENTS.length];
    }

    // ------------------------------------------------------------------ writing

    private void set(int x, int y, int z, Material material) {
        set(x, y, z, plain.computeIfAbsent(material, Material::createBlockData));
    }

    private void set(int x, int y, int z, BlockData data) {
        chunks.computeIfAbsent(chunkKey(x >> 4, z >> 4), key -> new HashMap<>())
                .put(blockKey(x, y, z), data);
    }

    private boolean isSet(int x, int y, int z) {
        Map<Long, BlockData> chunk = chunks.get(chunkKey(x >> 4, z >> 4));
        return chunk != null && chunk.containsKey(blockKey(x, y, z));
    }

    private static BlockData stairs(Material material, BlockFace facing) {
        BlockData data = material.createBlockData();
        if (data instanceof Stairs stairs) {
            stairs.setFacing(facing);
        }
        return data;
    }

    private static BlockData hangingLantern() {
        BlockData data = Material.LANTERN.createBlockData();
        if (data instanceof Lantern lantern) {
            lantern.setHanging(true);
        }
        return data;
    }

    private static BlockData leaves(Material material) {
        BlockData data = material.createBlockData();
        if (data instanceof Leaves leaves) {
            // Placed by hand, far from any log: without this they would decay.
            leaves.setPersistent(true);
        }
        return data;
    }

    /**
     * A small, stable pseudo random number for a position, so the design comes out the
     * same every time it is rebuilt.
     */
    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7FFFFFFF;
    }

    // --------------------------------------------------------------- boulevards

    private boolean insidePlaza(int x, int z, int margin) {
        int radius = CasinoLayout.PLAZA_RADIUS + margin;
        return x * x + z * z <= radius * radius;
    }

    private boolean insidePavilion(int x, int z, int margin) {
        for (CasinoLayout.Arena arena : layout.arenas()) {
            if (Math.abs(x - arena.centerX()) <= CasinoLayout.ARENA_RADIUS + margin
                    && Math.abs(z - arena.centerZ()) <= CasinoLayout.ARENA_RADIUS + margin) {
                return true;
            }
        }
        return false;
    }

    private boolean insideGarden(int x, int z, double margin) {
        for (CasinoLayout.Garden garden : layout.gardens()) {
            double dx = x - garden.centerX();
            double dz = z - garden.centerZ();
            if (dx * dx + dz * dz <= (11.5 + margin) * (11.5 + margin)) {
                return true;
            }
        }
        return false;
    }

    private boolean onRoad(int x, int z) {
        return roadLayer.containsKey(((long) x << 32) | (z & 0xFFFFFFFFL));
    }

    /**
     * Seven blocks wide: a white kerb each side, dark stone and a dashed middle line.
     * Kerbs never cut through the body of a crossing road.
     */
    private void boulevards() {
        int half = CasinoLayout.ROAD_WIDTH / 2;
        for (int pass = 0; pass < 3; pass++) {
            for (CasinoLayout.Road road : layout.roads()) {
                int length = Math.abs(road.toX() - road.fromX()) + Math.abs(road.toZ() - road.fromZ());
                int stepX = Integer.compare(road.toX(), road.fromX());
                int stepZ = Integer.compare(road.toZ(), road.fromZ());
                for (int along = -half; along <= length + half; along++) {
                    int x = road.fromX() + stepX * along;
                    int z = road.fromZ() + stepZ * along;
                    for (int offset = -half; offset <= half; offset++) {
                        int px = road.alongX() ? x : x + offset;
                        int pz = road.alongX() ? z + offset : z;
                        if (insidePlaza(px, pz, 0) || insidePavilion(px, pz, 0) || insideGarden(px, pz, 0)) {
                            continue;
                        }
                        long key = ((long) px << 32) | (pz & 0xFFFFFFFFL);
                        boolean edge = Math.abs(offset) == half;
                        boolean dash = offset == 0 && Math.floorMod(along, 8) < 4 && along >= 0 && along <= length;
                        if (pass == 0 && edge) {
                            roadLayer.putIfAbsent(key, 0);
                            set(px, floor, pz, KERB);
                        } else if (pass == 1 && !edge) {
                            roadLayer.put(key, 1);
                            set(px, floor, pz, ROAD);
                        } else if (pass == 2 && dash) {
                            set(px, floor, pz, DASH);
                        }
                    }
                }
            }
        }
    }

    /**
     * Lamp posts on both sides of every boulevard, and an avenue of trees behind them.
     */
    private void streetFurniture() {
        int ring = layout.ringRoad();
        for (CasinoLayout.Road road : layout.roads()) {
            int length = Math.abs(road.toX() - road.fromX()) + Math.abs(road.toZ() - road.fromZ());
            int stepX = Integer.compare(road.toX(), road.fromX());
            int stepZ = Integer.compare(road.toZ(), road.fromZ());
            for (int along = 8; along < length - 4; along += 8) {
                int x = road.fromX() + stepX * along;
                int z = road.fromZ() + stepZ * along;
                int side = (along / 16) % 2 == 0 ? 1 : -1;
                if (along % 16 == 8) {
                    int lx = road.alongX() ? x : x + side * 5;
                    int lz = road.alongX() ? z + side * 5 : z;
                    if (clearFor(lx, lz, 3)) {
                        lampPost(lx, lz);
                    }
                }
                if (along % 24 == 0) {
                    for (int treeSide : new int[]{-1, 1}) {
                        int tx = road.alongX() ? x : x + treeSide * 10;
                        int tz = road.alongX() ? z + treeSide * 10 : z;
                        boolean outside = Math.abs(tx) > ring || Math.abs(tz) > ring;
                        if (!outside && clearFor(tx, tz, 6) && !nearRoad(tx, tz, 5)) {
                            trees.add(new TreeSpot(tx, tz, AVENUE_TREES[hash(tx, tz, 3) % AVENUE_TREES.length]));
                        }
                    }
                }
            }
        }
    }

    private boolean clearFor(int x, int z, int margin) {
        return !onRoad(x, z) && !insidePlaza(x, z, margin) && !insidePavilion(x, z, margin)
                && !insideGarden(x, z, margin) && !isSet(x, floor, z) && !isSet(x, floor + 1, z);
    }

    private boolean nearRoad(int x, int z, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (onRoad(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void lampPost(int x, int z) {
        set(x, floor, z, Material.POLISHED_BLACKSTONE);
        for (int y = 1; y <= 3; y++) {
            set(x, floor + y, z, Material.POLISHED_BLACKSTONE_WALL);
        }
        set(x, floor + 4, z, Material.LANTERN);
    }

    // -------------------------------------------------------------------- plaza

    private void plaza() {
        int radius = CasinoLayout.PLAZA_RADIUS;
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > radius + 1) {
                    continue;
                }
                if (r > radius) {
                    // A pink hedge round the plaza, open wherever a boulevard leaves it.
                    if (Math.abs(x) > 4 && Math.abs(z) > 4) {
                        set(x, floor, z, Material.MOSS_BLOCK);
                        set(x, floor + 1, z, leaves(Material.FLOWERING_AZALEA_LEAVES));
                    }
                    continue;
                }
                set(x, floor, z, plazaFloor(x, z, r));
            }
        }
        fountain();
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 8 + i * Math.PI / 4;
            grandLamp((int) Math.round(Math.sin(angle) * 25), (int) Math.round(Math.cos(angle) * 25));
            planter((int) Math.round(Math.sin(angle) * 19), (int) Math.round(Math.cos(angle) * 19));
        }
    }

    private Material plazaFloor(int x, int z, double r) {
        int radius = CasinoLayout.PLAZA_RADIUS;
        if (r > radius - 1.5) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        if (r > 29.5 && r <= 30.5) {
            return Material.GOLD_BLOCK;
        }
        if (r > 30.5 && r <= 31.5) {
            return Material.POLISHED_BLACKSTONE;
        }
        if (r > 16.5 && r <= 17.5) {
            return Material.GOLD_BLOCK;
        }
        // Eight spokes, the four on the axes leading into the boulevards.
        if (r >= 9 && r <= 33) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double across = Math.abs(x * Math.cos(angle) - z * Math.sin(angle));
                double ahead = x * Math.sin(angle) + z * Math.cos(angle);
                if (ahead > 0 && across <= (i % 2 == 0 ? 2.5 : 1.2)) {
                    return i % 2 == 0 ? ROAD : Material.POLISHED_BLACKSTONE;
                }
            }
        }
        if (r > 31.5) {
            return Material.SMOOTH_STONE;
        }
        if (r > 17.5) {
            return (Math.floorDiv(x, 3) + Math.floorDiv(z, 3)) % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS;
        }
        return Material.POLISHED_DEEPSLATE;
    }

    private void fountain() {
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 8.4) {
                    continue;
                }
                if (r > 7.2) {
                    set(x, floor, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    set(x, floor + 1, z, Material.CHISELED_QUARTZ_BLOCK);
                    continue;
                }
                boolean pillar = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                boolean light = Math.abs(r - 5) < 0.5 && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
                set(x, floor, z, light ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                if (!pillar) {
                    set(x, floor + 1, z, Material.WATER);
                }
            }
        }
        // The marker of a finished build, under the pillar.
        set(0, floor, 0, Material.GOLD_BLOCK);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, floor + y, z, Material.QUARTZ_PILLAR);
                }
                set(x, floor + 5, z, Math.abs(x) + Math.abs(z) == 0 ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
            }
        }
    }

    private void grandLamp(int x, int z) {
        set(x, floor, z, Material.POLISHED_BLACKSTONE_BRICKS);
        set(x, floor + 1, z, Material.CHISELED_QUARTZ_BLOCK);
        for (int y = 2; y <= 4; y++) {
            set(x, floor + y, z, Material.QUARTZ_PILLAR);
        }
        set(x, floor + 5, z, Material.GOLD_BLOCK);
        set(x, floor + 6, z, Material.LANTERN);
    }

    private void planter(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(x + dx, floor, z + dz, Material.MOSS_BLOCK);
                if (dx != 0 || dz != 0) {
                    set(x + dx, floor + 1, z + dz, FLOWERS[hash(x + dx, z + dz, 7) % FLOWERS.length]);
                }
            }
        }
        set(x, floor + 1, z, Material.FLOWERING_AZALEA);
    }

    // ------------------------------------------------------------------ gardens

    /**
     * A grid cell nobody plays in: a roundabout round a flowering tree.
     */
    private void garden(CasinoLayout.Garden garden) {
        int cx = garden.centerX();
        int cz = garden.centerZ();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                if (r > 11.5) {
                    continue;
                }
                if (r > 10.5 || (r >= 6.5 && r < 7.5)) {
                    set(x, floor, z, KERB);
                } else if (r >= 7.5) {
                    set(x, floor, z, ROAD);
                } else if (r >= 2 && hash(x, z, 11) % 100 < 40) {
                    set(x, floor + 1, z, FLOWERS[hash(x, z, 13) % FLOWERS.length]);
                }
            }
        }
        trees.add(new TreeSpot(cx, cz, TreeType.CHERRY));
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4 + i * Math.PI / 2;
            lampPost(cx + (int) Math.round(Math.sin(angle) * 13), cz + (int) Math.round(Math.cos(angle) * 13));
        }
    }

    // ---------------------------------------------------------------- pavilions

    private void pavilion(CasinoLayout.Arena arena, BoardPad board) {
        int r = CasinoLayout.ARENA_RADIUS;
        int cx = arena.centerX();
        int cz = arena.centerZ();
        Material accent = accent(arena);
        Material glass = stainedGlass(accent);
        int gate = CasinoLayout.GATE_HALF_WIDTH;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                int along = Math.abs(dx) >= Math.abs(dz) ? dz : dx;
                boolean gatePath = ring >= r - 3 && Math.abs(along) <= gate;
                set(x, floor, z, pavilionFloor(dx, dz, ring, gatePath, accent));
                if (ring == r && !gatePath) {
                    boolean tower = Math.abs(dx) >= r - 2 && Math.abs(dz) >= r - 2;
                    if (!tower) {
                        set(x, floor + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                        set(x, floor + 2, z, glass);
                        set(x, floor + 3, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
                    }
                }
            }
        }
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                tower(cx + sx * (r - 1), cz + sz * (r - 1), accent);
            }
        }
        for (BlockFace side : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST}) {
            gateArch(arena, side);
            bleachers(arena, side);
        }
        // Invisible light over the whole platform, so the pavilion is bright at night too.
        for (int dx = -r + 4; dx <= r - 4; dx += 6) {
            for (int dz = -r + 4; dz <= r - 4; dz += 6) {
                set(cx + dx, floor + 6, cz + dz, Material.LIGHT);
            }
        }
        if (board != null) {
            boardPad(arena, board);
        }
    }

    private Material pavilionFloor(int dx, int dz, int ring, boolean gatePath, Material accent) {
        int r = CasinoLayout.ARENA_RADIUS;
        if (gatePath) {
            return ROAD;
        }
        if (ring == r) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        if (ring >= r - 3) {
            return Material.SMOOTH_STONE;
        }
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
            return accent;
        }
        double distance = Math.sqrt(dx * dx + dz * dz);
        double stage = CasinoLayout.STAGE_RADIUS;
        if (distance <= stage - 0.6) {
            return Material.POLISHED_DEEPSLATE;
        }
        if (distance <= stage + 0.4) {
            return Material.GOLD_BLOCK;
        }
        if (distance <= stage + 1.4) {
            return accent;
        }
        if (ring == r - 4) {
            return Material.POLISHED_BLACKSTONE;
        }
        return (Math.floorDiv(dx, 2) + Math.floorDiv(dz, 2)) % 2 == 0
                ? Material.SMOOTH_QUARTZ : Material.POLISHED_BLACKSTONE;
    }

    private void tower(int x, int z, Material accent) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 1; y <= 6; y++) {
                    set(x + dx, floor + y, z + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                }
                set(x + dx, floor + 7, z + dz, accent);
            }
        }
        set(x, floor + 8, z, Material.LANTERN);
    }

    /**
     * Two quartz pillars, a lintel with a gold keystone and two lanterns hanging inside.
     */
    private void gateArch(CasinoLayout.Arena arena, BlockFace side) {
        int r = CasinoLayout.ARENA_RADIUS;
        int gate = CasinoLayout.GATE_HALF_WIDTH;
        for (int along = -gate - 1; along <= gate + 1; along++) {
            int[] spot = edgePoint(arena, side, along, r);
            boolean pillar = Math.abs(along) == gate + 1;
            if (pillar) {
                for (int y = 1; y <= 6; y++) {
                    set(spot[0], floor + y, spot[1], Material.QUARTZ_PILLAR);
                }
            }
            set(spot[0], floor + 7, spot[1], along == 0 ? Material.GOLD_BLOCK : Material.SMOOTH_QUARTZ);
            if (Math.abs(along) == 2) {
                set(spot[0], floor + 6, spot[1], hangingLantern());
            }
        }
    }

    /**
     * Two rows of quartz steps along the inside of a wall, between the gate and the
     * towers, rising towards the wall.
     */
    private void bleachers(CasinoLayout.Arena arena, BlockFace side) {
        int r = CasinoLayout.ARENA_RADIUS;
        for (int along = -(r - 4); along <= r - 4; along++) {
            if (Math.abs(along) <= CasinoLayout.GATE_HALF_WIDTH + 2) {
                continue;
            }
            int[] back = edgePoint(arena, side, along, r - 1);
            int[] front = edgePoint(arena, side, along, r - 2);
            set(back[0], floor + 1, back[1], Material.SMOOTH_QUARTZ);
            set(back[0], floor + 2, back[1], stairs(Material.QUARTZ_STAIRS, side));
            set(front[0], floor + 1, front[1], stairs(Material.QUARTZ_STAIRS, side));
        }
    }

    /**
     * A point on one side of a pavilion: {@code depth} blocks from the middle towards
     * that side, {@code along} blocks across it.
     */
    private static int[] edgePoint(CasinoLayout.Arena arena, BlockFace side, int along, int depth) {
        return switch (side) {
            case NORTH -> new int[]{arena.centerX() + along, arena.centerZ() - depth};
            case SOUTH -> new int[]{arena.centerX() + along, arena.centerZ() + depth};
            case WEST -> new int[]{arena.centerX() - depth, arena.centerZ() + along};
            default -> new int[]{arena.centerX() + depth, arena.centerZ() + along};
        };
    }

    /**
     * Resting board of a game played by clicking blocks: one tile per cell, the filler
     * of a grid the configuration does not fill, and a blackstone frame around it that
     * also holds the blocks used to collect a prize when one is on the table.
     */
    private void boardPad(CasinoLayout.Arena arena, BoardPad pad) {
        BoardGrid grid = pad.grid();
        if (!grid.fitsIn(CasinoLayout.ARENA_RADIUS)) {
            return;
        }
        int y = floor + 1;
        for (int cell = 0; cell < grid.cellCount(); cell++) {
            set(arena.centerX() + grid.dx(cell), y, arena.centerZ() + grid.dz(cell),
                    cell < pad.cells() ? pad.tile() : pad.filler());
        }
        for (int dx = grid.originX() - 1; dx <= grid.originX() + grid.columns(); dx++) {
            set(arena.centerX() + dx, y, arena.centerZ() + grid.originZ() - 1, Material.POLISHED_BLACKSTONE);
            set(arena.centerX() + dx, y, arena.centerZ() + grid.originZ() + grid.rows(), Material.POLISHED_BLACKSTONE);
        }
        for (int dz = grid.originZ(); dz < grid.originZ() + grid.rows(); dz++) {
            set(arena.centerX() + grid.originX() - 1, y, arena.centerZ() + dz, Material.POLISHED_BLACKSTONE);
            set(arena.centerX() + grid.originX() + grid.columns(), y, arena.centerZ() + dz, Material.POLISHED_BLACKSTONE);
        }
    }

    private static Material stainedGlass(Material accent) {
        String name = accent.name();
        int cut = name.lastIndexOf('_');
        Material glass = cut > 0 ? Material.matchMaterial(name.substring(0, cut) + "_STAINED_GLASS") : null;
        return glass == null ? Material.YELLOW_STAINED_GLASS : glass;
    }

    // ---------------------------------------------------------------- outskirts

    /**
     * A belt of trees between the ring road and the edge of the world, and a hedge along
     * the world border.
     */
    private void outskirts() {
        int edge = layout.size() / 2 - 2;
        int ring = layout.ringRoad() + CasinoLayout.ROAD_WIDTH;
        for (int x = -edge; x <= edge; x += 11) {
            for (int z = -edge; z <= edge; z += 11) {
                int jx = x + hash(x, z, 17) % 5 - 2;
                int jz = z + hash(x, z, 19) % 5 - 2;
                boolean belt = Math.abs(jx) > ring + 2 || Math.abs(jz) > ring + 2;
                boolean inside = Math.abs(jx) < edge - 6 && Math.abs(jz) < edge - 6;
                // The corners stay open: the ground level is measured there.
                boolean corner = Math.abs(jx) > edge - 16 && Math.abs(jz) > edge - 16;
                if (belt && inside && !corner && hash(jx, jz, 23) % 100 < 70) {
                    trees.add(new TreeSpot(jx, jz, FOREST_TREES[hash(jx, jz, 29) % FOREST_TREES.length]));
                }
            }
        }
        BlockData hedge = leaves(Material.OAK_LEAVES);
        for (int along = -edge; along <= edge; along++) {
            for (int[] spot : new int[][]{{along, -edge}, {along, edge}, {-edge, along}, {edge, along}}) {
                set(spot[0], floor + 1, spot[1], hedge);
                set(spot[0], floor + 2, spot[1], hedge);
            }
        }
    }

    /**
     * Grass tufts and the odd flower on the lawns, so the open ground is not bare.
     */
    private void wildflowers() {
        int edge = layout.size() / 2 - 4;
        for (int x = -edge; x <= edge; x++) {
            for (int z = -edge; z <= edge; z++) {
                int roll = hash(x, z, 31) % 100;
                if (roll >= 9 || isSet(x, floor, z) || isSet(x, floor + 1, z) || onRoad(x, z)) {
                    continue;
                }
                if (insidePlaza(x, z, 2) || insidePavilion(x, z, 2) || insideGarden(x, z, 1)) {
                    continue;
                }
                set(x, floor + 1, z, roll < 7 ? Material.SHORT_GRASS : FLOWERS[hash(x, z, 37) % FLOWERS.length]);
            }
        }
    }
}
