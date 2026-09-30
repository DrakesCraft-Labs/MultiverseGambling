package com.chagui68.multiversegambling.world;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

/**
 * The dedicated casino world: a flat stage of {@code world.size} x {@code world.size}
 * blocks with a plaza at spawn, a grid of roads and one arena per registered game.
 *
 * <p>The world is created as a flat world the first time the plugin runs, so the
 * build only has to place the structures on top of the ground. Nothing is ever
 * built in the server's main world: pointing {@code world.name} at it is refused
 * with a warning instead of paving over somebody's lobby.</p>
 */
public final class CasinoWorldManager {

    private static final String FLAT_SETTINGS =
            "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;1;minecraft:plains";

    /** One colour per arena, cycled when more games than colours are registered. */
    private static final Material[] PALETTE = {
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.GREEN_CONCRETE, Material.CYAN_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE, Material.WHITE_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.BLACK_CONCRETE,
            Material.BROWN_CONCRETE, Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.CYAN_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.PURPLE_TERRACOTTA
    };

    private final MultiverseGamblingPlugin plugin;
    private World world;
    private CasinoLayout layout;
    private int floorY;
    private boolean built;

    public CasinoWorldManager(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------------------------- setup

    /** Loads (or creates) the world and builds the structures the first time. */
    public boolean setup() {
        if (!plugin.config().worldEnabled()) {
            plugin.getLogger().info("Casino world disabled in the configuration.");
            return false;
        }
        if (!ensure()) {
            return false;
        }
        if (plugin.config().worldBuildStructures() && !looksBuilt()) {
            build();
        } else {
            built = true;
        }
        plugin.getLogger().info("Casino world '" + world.getName() + "' ready: " + layout.arenas().size()
                + " arenas inside a " + layout.size() + "x" + layout.size() + " border, ground at y="
                + (floorY + 1) + ".");
        return true;
    }

    /** Creates the world when it does not exist yet and refreshes border and spawn. */
    public boolean ensure() {
        if (world != null && Bukkit.getWorld(world.getName()) != null) {
            return true;
        }
        String name = plugin.config().worldName();
        world = Bukkit.getWorld(name);
        if (world == null) {
            if (isMainWorldName(name)) {
                plugin.getLogger().warning("world.name points at the server's main world ('" + name
                        + "'). Refusing to build there; set world.name to a dedicated world.");
                return false;
            }
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .generatorSettings(FLAT_SETTINGS)
                    .createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Could not create or load the casino world '" + name + "'.");
            return false;
        }
        probeFloor();
        layout = createLayout();
        applyBorder();
        world.setSpawnLocation(0, floorY + 1, 0);
        return true;
    }

    private boolean isMainWorldName(String name) {
        List<World> worlds = Bukkit.getWorlds();
        return !worlds.isEmpty() && worlds.get(0).getName().equalsIgnoreCase(name);
    }

    /**
     * Builds the layout, growing the world when the configuration is too small for
     * the number of registered games instead of failing to start.
     */
    private CasinoLayout createLayout() {
        List<String> ids = gameIds();
        int size = plugin.config().worldSize();
        try {
            return CasinoLayout.of(size, ids);
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning(error.getMessage());
        }
        for (int candidate = size + 50; candidate <= 2000; candidate += 50) {
            try {
                CasinoLayout bigger = CasinoLayout.of(candidate, ids);
                plugin.getLogger().warning("Using a " + candidate + "x" + candidate
                        + " casino world so every arena fits; raise world.size in config.yml.");
                return bigger;
            } catch (IllegalArgumentException ignored) {
                // Keep growing; the last attempt below reports the failure.
            }
        }
        throw new IllegalStateException("The casino layout does not fit in any allowed world size");
    }

    private void applyBorder() {
        world.getWorldBorder().setCenter(0.0, 0.0);
        world.getWorldBorder().setSize(layout.size());
        world.getWorldBorder().setWarningDistance(0);
    }

    private void probeFloor() {
        // Probe far away from the plaza and the roads, so the level stays stable no
        // matter how many times the structures are rebuilt.
        int probe = Math.max(1, plugin.config().worldSize() / 2 - 4);
        floorY = world.getHighestBlockYAt(probe, probe) + 1;
    }

    /** Registered game ids, in registration order. */
    private List<String> gameIds() {
        List<String> ids = new ArrayList<>();
        for (Game game : plugin.games().all()) {
            ids.add(game.id());
        }
        return ids;
    }

    // ------------------------------------------------------------------ building

    /** True when the plaza marker is already in place. */
    public boolean looksBuilt() {
        return world != null && world.getBlockAt(0, floorY, 0).getType() == Material.GOLD_BLOCK;
    }

    /**
     * Builds (or rebuilds) the plaza, the roads and the arenas.
     *
     * @return true when the build finished, false when the world is not ready
     */
    public boolean rebuild() {
        if (!ensure()) {
            return false;
        }
        return build();
    }

    private boolean build() {
        if (world == null || layout == null) {
            return false;
        }
        long started = System.currentTimeMillis();
        try {
            roads();
            plaza();
            for (CasinoLayout.Arena arena : layout.arenas()) {
                arena(arena);
            }
            built = true;
        } catch (RuntimeException error) {
            plugin.getLogger().severe("Could not build the casino structures: " + error);
            error.printStackTrace();
            return false;
        }
        plugin.getLogger().info("Casino structures built in " + (System.currentTimeMillis() - started)
                + " ms (" + layout.arenas().size() + " arenas).");
        return true;
    }

    private void roads() {
        for (CasinoLayout.Road road : layout.roads()) {
            int stepX = Integer.compare(road.toX(), road.fromX());
            int stepZ = Integer.compare(road.toZ(), road.fromZ());
            int x = road.fromX();
            int z = road.fromZ();
            while (true) {
                // Roads stop at the plaza edge and resume on the far side of an arena.
                if (!insidePlaza(x, z) && !insideArena(x, z)) {
                    strip(x, z, road.alongX());
                }
                if (x == road.toX() && z == road.toZ()) {
                    break;
                }
                x += stepX;
                z += stepZ;
            }
        }
    }

    /** A three block wide piece of road centred on the segment. */
    private void strip(int x, int z, boolean alongX) {
        int half = CasinoLayout.ROAD_WIDTH / 2;
        for (int offset = -half; offset <= half; offset++) {
            if (alongX) {
                set(x, floorY, z + offset, Material.POLISHED_DIORITE);
            } else {
                set(x + offset, floorY, z, Material.POLISHED_DIORITE);
            }
        }
    }

    private boolean insidePlaza(int x, int z) {
        return x * x + z * z <= CasinoLayout.PLAZA_RADIUS * CasinoLayout.PLAZA_RADIUS;
    }

    private boolean insideArena(int x, int z) {
        for (CasinoLayout.Arena arena : layout.arenas()) {
            if (arena.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    private void plaza() {
        int radius = CasinoLayout.PLAZA_RADIUS;
        disc(0, 0, radius, Material.POLISHED_DEEPSLATE);
        disc(0, 0, radius - 5, Material.SMOOTH_QUARTZ);
        disc(0, 0, 4, Material.GOLD_BLOCK);
        kerb(radius);
        // Welcome monument in the middle of the plaza.
        set(0, floorY + 1, 0, Material.GOLD_BLOCK);
        set(0, floorY + 2, 0, Material.GOLD_BLOCK);
        set(0, floorY + 3, 0, Material.SEA_LANTERN);
        sign(2, floorY + 1, 0, Material.OAK_SIGN, BlockFace.EAST,
                "&6&lMultiverseGambling", "&7Casino world", "&7/casino menu", "&7/casino language");
        // Lamps on the diagonals keep the four road exits clear.
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                lamp(dx * 22, dz * 22);
            }
        }
    }

    /** One block high rim around the plaza, opened wherever a road leaves it. */
    private void kerb(int radius) {
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                int distance = x * x + z * z;
                if (distance < radius * radius || distance > (radius + 1) * (radius + 1)) {
                    continue;
                }
                if (Math.abs(x) <= CasinoLayout.ROAD_WIDTH || Math.abs(z) <= CasinoLayout.ROAD_WIDTH) {
                    continue;
                }
                set(x, floorY + 1, z, Material.POLISHED_BLACKSTONE);
            }
        }
    }

    private void disc(int centerX, int centerZ, int radius, Material material) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    set(centerX + x, floorY, centerZ + z, material);
                }
            }
        }
    }

    private void arena(CasinoLayout.Arena arena) {
        Material floor = PALETTE[arena.index() % PALETTE.length];
        int radius = CasinoLayout.ARENA_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean border = Math.abs(x) == radius || Math.abs(z) == radius;
                set(arena.centerX() + x, floorY, arena.centerZ() + z,
                        border ? Material.POLISHED_BLACKSTONE : floor);
            }
        }
        // Small medallion in the middle of the platform.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(arena.centerX() + x, floorY, arena.centerZ() + z,
                        ring ? Material.GOLD_BLOCK : floor);
            }
        }
        fence(arena);
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                lamp(arena.centerX() + dx * (radius - 2), arena.centerZ() + dz * (radius - 2));
            }
        }
        arenaSign(arena);
    }

    /** Fence around the platform with a three block opening on the entrance side. */
    private void fence(CasinoLayout.Arena arena) {
        CasinoLayout.Edge entrance = CasinoLayout.entrance(arena);
        boolean openAlongX = entrance == CasinoLayout.Edge.NORTH || entrance == CasinoLayout.Edge.SOUTH;
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int z : new int[]{arena.minZ(), arena.maxZ()}) {
                if (isOpening(entrance, openAlongX, z, x, arena)) {
                    continue;
                }
                set(x, floorY + 1, z, Material.OAK_FENCE);
            }
        }
        for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
            for (int x : new int[]{arena.minX(), arena.maxX()}) {
                if (isOpening(entrance, !openAlongX, x, z, arena)) {
                    continue;
                }
                set(x, floorY + 1, z, Material.OAK_FENCE);
            }
        }
    }

    /**
     * @param acrossX  true when the fence line being walked runs along the X axis
     * @param position the coordinate that changes along that line
     * @param fixed    the coordinate that stays put
     */
    private boolean isOpening(CasinoLayout.Edge entrance, boolean acrossX, int fixed, int position,
                              CasinoLayout.Arena arena) {
        boolean onEntranceEdge = switch (entrance) {
            case NORTH -> fixed == arena.minZ();
            case SOUTH -> fixed == arena.maxZ();
            case WEST -> fixed == arena.minX();
            case EAST -> fixed == arena.maxX();
        };
        if (!onEntranceEdge) {
            return false;
        }
        int middle = acrossX ? arena.centerX() : arena.centerZ();
        return Math.abs(position - middle) <= 1;
    }

    /** Sign outside the entrance so the arena is easy to find from the road. */
    private void arenaSign(CasinoLayout.Arena arena) {
        Game game = plugin.games().byId(arena.gameId()).orElse(null);
        String fallback = game == null ? arena.gameId() : game.name();
        String label = plugin.messages().getOr("catalog." + arena.gameId() + ".name", fallback);
        int x = arena.centerX();
        int z = arena.centerZ();
        int offset = CasinoLayout.ARENA_RADIUS + 2;
        String[] lines = {"&6" + label, "&7" + arena.gameId(), "&7/casino play",
                "&8Arena " + (arena.index() + 1)};
        switch (CasinoLayout.entrance(arena)) {
            case NORTH -> sign(x, floorY + 1, z - offset, Material.OAK_SIGN, BlockFace.NORTH, lines);
            case SOUTH -> sign(x, floorY + 1, z + offset, Material.OAK_SIGN, BlockFace.SOUTH, lines);
            case WEST -> sign(x - offset, floorY + 1, z, Material.OAK_SIGN, BlockFace.WEST, lines);
            case EAST -> sign(x + offset, floorY + 1, z, Material.OAK_SIGN, BlockFace.EAST, lines);
        }
    }

    // ------------------------------------------------------------------- placing

    private void lamp(int x, int z) {
        set(x, floorY, z, Material.CHISELED_STONE_BRICKS);
        set(x, floorY + 1, z, Material.CHISELED_STONE_BRICKS);
        set(x, floorY + 2, z, Material.SEA_LANTERN);
    }

    private void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    private void sign(int x, int y, int z, Material material, BlockFace facing, String... lines) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        BlockData data = material.createBlockData();
        if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(facing);
        }
        block.setBlockData(data, false);
        BlockState state = block.getState();
        if (state instanceof Sign sign) {
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                sign.getSide(Side.FRONT).line(i, Text.c(lines[i]));
            }
            sign.update(true, false);
        }
    }

    // ------------------------------------------------------------------- players

    public boolean ready() {
        return world != null && Bukkit.getWorld(world.getName()) != null && layout != null;
    }

    public World world() {
        return world;
    }

    public CasinoLayout layout() {
        return layout;
    }

    /** Where players land when they ask for the casino world. */
    public Location spawn() {
        if (world == null) {
            return null;
        }
        return new Location(world, 0.5, floorY + 1, 3.5, 180.0f, 0.0f);
    }

    /** Sends a player to the plaza, loading the spawn chunk first. */
    public boolean teleport(Player player) {
        if (!ensure() || !ready()) {
            return false;
        }
        Location target = spawn();
        world.getChunkAt(target).load();
        player.setFallDistance(0.0f);
        return player.teleport(target);
    }

    /** Honours {@code world.teleport-on-join} one tick after the player joins. */
    public void handleJoin(Player player) {
        if (!plugin.config().worldEnabled() || !plugin.config().worldTeleportOnJoin()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> teleport(player));
    }

    public boolean built() {
        return built;
    }
}
