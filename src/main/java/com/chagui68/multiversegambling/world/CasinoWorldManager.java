package com.chagui68.multiversegambling.world;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.game.BoardGame;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.board.BoardGrid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The dedicated casino world: a flat stage of {@code world.size} x {@code world.size}
 * blocks with a plaza at spawn, a grid of boulevards and one pavilion per registered
 * game.
 *
 * <p>The world is created as a flat world the first time the plugin runs. The design is
 * stamped into the world data with a signature of the layout (version, size, games and
 * boards): when the signature changes, because the plugin was updated or a game was
 * added, the casino is rebuilt on its own, a few chunks per tick, wiping whatever the
 * previous version left on the surface. Nothing is ever built in the server's main
 * world: pointing {@code world.name} at it is refused with a warning instead of paving
 * over somebody's lobby.</p>
 */
public final class CasinoWorldManager {

    /**
     * Version of the casino design: bump it whenever the blueprint changes, so existing
     * worlds are rebuilt with the new look.
     */
    static final int DESIGN_VERSION = 2;

    /**
     * Flat terrain for the casino: one bedrock layer, two of dirt and a grass
     * surface. Modern servers expect these settings as JSON with a valid biome
     * (the old {@code 3;bedrock,...;1;plains} string is rejected as malformed
     * JSON and aborts world creation), so the layer list is written out here.
     */
    static final String FLAT_SETTINGS =
            "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
            + "{\"block\":\"minecraft:dirt\",\"height\":2},"
            + "{\"block\":\"minecraft:grass_block\",\"height\":1}],"
            + "\"biome\":\"minecraft:plains\"}";

    /**
     * Outcome of a request to build the casino.
     */
    public enum BuildStart {
        /** The build is running; a message follows when it is done. */
        STARTED,
        /** Another build is still running. */
        BUSY,
        /** The world could not be prepared. */
        FAILED
    }

    private final MultiverseGamblingPlugin plugin;
    private final CasinoDecor decor;
    private final NamespacedKey layoutKey;
    private final NamespacedKey floorKey;
    private World world;
    private CasinoLayout layout;
    private int floorY;
    private boolean built;
    private boolean createdThisRun;
    private CasinoBuildJob job;

    public CasinoWorldManager(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.decor = new CasinoDecor(plugin);
        this.layoutKey = new NamespacedKey(plugin, "layout");
        this.floorKey = new NamespacedKey(plugin, "floor");
    }

    // --------------------------------------------------------------------- setup

    /**
     * Loads (or creates) the world and builds the structures when they are missing or
     * belong to an older design.
     */
    public boolean setup() {
        if (!plugin.config().worldEnabled()) {
            plugin.getLogger().info("Casino world disabled in the configuration.");
            return false;
        }
        if (!ensure()) {
            return false;
        }
        if (plugin.config().worldBuildStructures() && !upToDate()) {
            plugin.getLogger().info("Building the casino world '" + world.getName() + "' (design v"
                    + DESIGN_VERSION + "); it takes a few seconds and the server keeps running meanwhile.");
            startBuild(null);
        } else {
            built = true;
            decor.start(world, layout, floorY);
        }
        plugin.getLogger().info("Casino world '" + world.getName() + "' ready: " + layout.arenas().size()
                + " pavilions inside a " + layout.size() + "x" + layout.size() + " border, ground at y="
                + (floorY + 1) + ".");
        return true;
    }

    /**
     * Creates the world when it does not exist yet and refreshes border, spawn and rules.
     */
    public boolean ensure() {
        if (world != null && Bukkit.getWorld(world.getName()) != null && layout != null) {
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
            try {
                world = new WorldCreator(name)
                        .environment(World.Environment.NORMAL)
                        .type(WorldType.FLAT)
                        .generateStructures(false)
                        .generatorSettings(FLAT_SETTINGS)
                        .createWorld();
                createdThisRun = world != null;
            } catch (RuntimeException error) {
                // A broken world must never take the whole plugin down: log it and
                // let the games run from the menu instead of aborting onEnable.
                plugin.getLogger().severe("Could not create the casino world '" + name + "': "
                        + error.getMessage());
                return false;
            }
        }
        if (world == null) {
            plugin.getLogger().severe("Could not create or load the casino world '" + name + "'.");
            return false;
        }
        probeFloor();
        layout = createLayout();
        applyBorder();
        applyRules();
        world.setSpawnLocation(0, floorY + 1, 20);
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
                        + " casino world so every pavilion fits; raise world.size in config.yml.");
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

    /**
     * A casino needs no monsters, no weather and no fire, and keeps the hour the
     * configuration asks for.
     */
    private void applyRules() {
        try {
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
            world.setGameRule(GameRules.SPAWN_PATROLS, false);
            world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
            world.setGameRule(GameRules.RAIDS, false);
            world.setGameRule(GameRules.MOB_GRIEFING, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            world.setStorm(false);
            world.setThundering(false);
            long time = plugin.config().worldTime();
            world.setGameRule(GameRules.ADVANCE_TIME, time < 0);
            if (time >= 0) {
                world.setTime(time);
            }
        } catch (RuntimeException error) {
            // A server that renamed a rule must not lose the casino over it.
            plugin.getLogger().warning("Could not apply the casino world rules: " + error.getMessage());
        }
    }

    /**
     * The ground level. Stored with the build, because trees and buildings could fool a
     * fresh measurement later on; measured in a far corner the casino never builds in
     * the first time.
     */
    private void probeFloor() {
        Integer stored = world.getPersistentDataContainer().get(floorKey, PersistentDataType.INTEGER);
        if (stored != null) {
            floorY = stored;
            return;
        }
        int probe = Math.max(1, plugin.config().worldSize() / 2 - 4);
        floorY = world.getHighestBlockYAt(probe, probe);
    }

    /**
     * Registered game ids, in registration order.
     */
    private List<String> gameIds() {
        List<String> ids = new ArrayList<>();
        for (Game game : plugin.games().all()) {
            ids.add(game.id());
        }
        return ids;
    }

    /**
     * What the current casino is made of: when it differs from what is stamped in the
     * world, the casino on disk belongs to another design and is rebuilt.
     */
    private String signature() {
        StringBuilder out = new StringBuilder("v").append(DESIGN_VERSION).append('|').append(layout.size());
        for (CasinoLayout.Arena arena : layout.arenas()) {
            out.append('|').append(arena.gameId());
            Game game = plugin.games().byId(arena.gameId()).orElse(null);
            if (game instanceof BoardGame board) {
                BoardGrid grid = board.boardGrid();
                out.append(':').append(grid.columns()).append('x').append(grid.rows())
                        .append('/').append(board.boardCells());
            }
        }
        return out.toString();
    }

    private boolean upToDate() {
        PersistentDataContainer data = world.getPersistentDataContainer();
        return signature().equals(data.get(layoutKey, PersistentDataType.STRING))
                && data.has(floorKey, PersistentDataType.INTEGER);
    }

    // ------------------------------------------------------------------ building

    /**
     * True when the casino on disk matches the current design.
     */
    public boolean looksBuilt() {
        return world != null && layout != null && upToDate();
    }

    /**
     * Builds (or rebuilds) the plaza, the boulevards and the pavilions in the background.
     *
     * @param whenDone told whether the build finished, on the main thread; may be null
     */
    public BuildStart rebuild(Consumer<Boolean> whenDone) {
        if (job != null && job.running()) {
            return BuildStart.BUSY;
        }
        if (!ensure()) {
            return BuildStart.FAILED;
        }
        startBuild(whenDone);
        return BuildStart.STARTED;
    }

    private void startBuild(Consumer<Boolean> whenDone) {
        Map<String, CasinoBlueprint.BoardPad> boards = new LinkedHashMap<>();
        for (Game game : plugin.games().all()) {
            if (game instanceof BoardGame board) {
                BoardGrid grid = board.boardGrid();
                if (!grid.fitsIn(CasinoLayout.ARENA_RADIUS)) {
                    plugin.getLogger().warning("The " + game.id() + " board is " + grid.columns()
                            + "x" + grid.rows() + " blocks, too big for a pavilion: that game will only"
                            + " use its menu until the configuration shrinks it.");
                    continue;
                }
                boards.put(game.id(), new CasinoBlueprint.BoardPad(grid, board.boardCells(),
                        board.boardTile(), board.boardFiller()));
            }
        }
        long planning = System.currentTimeMillis();
        CasinoBlueprint blueprint = CasinoBlueprint.design(layout, floorY, boards);
        plugin.getLogger().info("Casino design ready: " + blueprint.blocks() + " blocks and "
                + blueprint.trees().size() + " trees planned in " + (System.currentTimeMillis() - planning) + " ms.");
        built = false;
        decor.stop();
        String expected = signature();
        job = new CasinoBuildJob(plugin, world, blueprint, layout.size(), result -> {
            if (result.complete()) {
                PersistentDataContainer data = world.getPersistentDataContainer();
                data.set(layoutKey, PersistentDataType.STRING, expected);
                data.set(floorKey, PersistentDataType.INTEGER, floorY);
                built = true;
                plugin.getLogger().info("Casino world built in " + (result.millis() / 1000.0) + " s: "
                        + result.changed() + " blocks changed over " + result.chunks() + " chunks.");
            } else {
                plugin.getLogger().warning("The casino build did not finish (" + result.failed()
                        + " chunks could not be loaded); run /mvgam world build to try again.");
            }
            decor.start(world, layout, floorY);
            if (whenDone != null) {
                whenDone.accept(result.complete());
            }
        });
        job.start();
    }

    /**
     * True while a build is running.
     */
    public boolean building() {
        return job != null && job.running();
    }

    /**
     * Share of the running build already done, from 0 to 1.
     */
    public double buildProgress() {
        return job == null ? 1.0 : job.progress();
    }

    /**
     * Respawns the decoration, for example after the language files were reloaded.
     */
    public void refreshDecor() {
        if (ready() && !building()) {
            decor.start(world, layout, floorY);
        }
    }

    /**
     * Stops whatever runs in the background and removes the decoration.
     */
    public void shutdown() {
        if (job != null) {
            job.cancel();
        }
        decor.stop();
    }

    // ------------------------------------------------------------------- diagnosis

    /**
     * Snapshot of the world for {@code /mvgam world info}: what exists, what was built and
     * what is missing. It answers with whatever it knows, whatever state the world is in,
     * so it can be run from the console right after a failed start.
     */
    public WorldReport report() {
        String name = plugin.config().worldName();
        int games = plugin.games().all().size();
        List<String> tooBig = boardsTooBig();
        if (!plugin.config().worldEnabled()) {
            return new WorldReport(name, WorldReport.Status.DISABLED, WorldReport.Source.ABSENT,
                    false, 0, -1, 0, 0, games, 0, tooBig);
        }
        if (isMainWorldName(name)) {
            return new WorldReport(name, WorldReport.Status.MAIN_WORLD, WorldReport.Source.ABSENT,
                    false, 0, -1, 0, 0, games, 0, tooBig);
        }
        // The world may be loaded by the server even when this manager gave up on it.
        World live = world != null && Bukkit.getWorld(world.getName()) != null ? world : Bukkit.getWorld(name);
        if (live == null) {
            return new WorldReport(name, WorldReport.Status.MISSING, WorldReport.Source.ABSENT,
                    false, 0, -1, 0, 0, games, 0, tooBig);
        }
        WorldReport.Source source = createdThisRun ? WorldReport.Source.CREATED_HERE : WorldReport.Source.LOADED;
        boolean flat = live.getWorldType() == WorldType.FLAT;
        if (layout == null) {
            return new WorldReport(name, WorldReport.Status.NOT_PREPARED, source, flat,
                    0, -1, 0, 0, games, boards(), tooBig);
        }
        int builtArenas = builtArenas(live);
        WorldReport.Status status = building() ? WorldReport.Status.BUILDING
                : !looksBuilt() ? WorldReport.Status.NOT_BUILT
                : builtArenas < layout.arenas().size() ? WorldReport.Status.PARTIAL
                : WorldReport.Status.READY;
        return new WorldReport(name, status, source, flat, layout.size(), floorY + 1,
                builtArenas, layout.arenas().size(), games, boards(), tooBig);
    }

    /**
     * How many pavilions carry the accent colour the build paves their centre with, so
     * a half finished or partially cleared world is visible at a glance.
     */
    private int builtArenas(World target) {
        int count = 0;
        for (CasinoLayout.Arena arena : layout.arenas()) {
            if (!target.isChunkLoaded(arena.centerX() >> 4, arena.centerZ() >> 4)) {
                // Not loaded means not looked at: trust the stamp of the last build.
                count += looksBuilt() ? 1 : 0;
                continue;
            }
            if (target.getBlockAt(arena.centerX(), floorY, arena.centerZ()).getType()
                    == CasinoBlueprint.accent(arena)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Pavilions of the games played by clicking blocks, as planned by the layout.
     */
    private int boards() {
        int count = 0;
        for (Game game : plugin.games().all()) {
            if (game instanceof BoardGame board && board.boardGrid().fitsIn(CasinoLayout.ARENA_RADIUS)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Games whose board is too big for a pavilion: they only live in their menu until
     * the configuration shrinks the board.
     */
    private List<String> boardsTooBig() {
        List<String> tooBig = new ArrayList<>();
        for (Game game : plugin.games().all()) {
            if (game instanceof BoardGame board && !board.boardGrid().fitsIn(CasinoLayout.ARENA_RADIUS)) {
                tooBig.add(game.id());
            }
        }
        return tooBig;
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

    /**
     * Y of the ground blocks of the plaza and the pavilions; players stand one higher.
     */
    public int floorY() {
        return floorY;
    }

    /**
     * The pavilion of one game, ready to stage a show on.
     *
     * @return {@code null} when the casino world is disabled or has no pavilion for that
     * game, so callers can fall back to their menus instead of failing
     */
    public ArenaStage stage(String gameId) {
        if (!ready()) {
            return null;
        }
        CasinoLayout.Arena arena = layout.arena(gameId);
        if (arena == null) {
            return null;
        }
        return new ArenaStage(world, arena, floorY);
    }

    /**
     * Where players land when they ask for the casino world: on the plaza, facing the
     * fountain and the welcome board.
     */
    public Location spawn() {
        if (world == null) {
            return null;
        }
        return new Location(world, 0.5, floorY + 1, 20.5, 180.0f, 0.0f);
    }

    /**
     * Sends a player to the plaza, loading the spawn chunk first.
     */
    public boolean teleport(Player player) {
        if (!ensure() || !ready()) {
            return false;
        }
        Location target = spawn();
        world.getChunkAt(target).load();
        player.setFallDistance(0.0f);
        return player.teleport(target);
    }

    /**
     * Sends a player in front of the main gate of one pavilion, looking at its stage.
     *
     * @return false when the casino world is not ready or the game has no pavilion
     */
    public boolean teleportToArena(Player player, String gameId) {
        if (!ensure() || !ready()) {
            return false;
        }
        ArenaStage stage = stage(gameId);
        if (stage == null) {
            return false;
        }
        stage.load();
        player.setFallDistance(0.0f);
        // Just outside the main gate: the whole pavilion is in front of the player.
        Location spot = stage.local(0, 0, CasinoLayout.ARENA_RADIUS + 3);
        spot.setYaw(stage.frame().lookYaw(0, CasinoLayout.ARENA_RADIUS + 3, 0, 0));
        spot.setPitch(-8.0f);
        return player.teleport(spot);
    }

    /**
     * Honours {@code world.teleport-on-join} one tick after the player joins.
     */
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
