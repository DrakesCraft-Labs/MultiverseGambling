package com.chagui68.multiversegambling.world;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.world.anim.Props;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

/**
 * Turns a {@link CasinoBlueprint} into blocks, a little every tick.
 *
 * <p>The casino covers about a thousand chunks, far too many to place in one go without
 * freezing the server. The job asks the server for the chunks in the background, then
 * spends at most a few milliseconds per tick walking them: every column from the ground
 * up to {@link CasinoBlueprint#CLEAR_HEIGHT} is compared with the design and only the
 * blocks that differ are changed, so a rebuild also wipes whatever an older version of
 * the casino left behind. Trees are grown last, once every block is down.</p>
 */
final class CasinoBuildJob {

    /** Time the job may use per tick. */
    private static final long BUDGET_NANOS = 18_000_000L;
    /** Chunks requested from the server at the same time. */
    private static final int IN_FLIGHT = 24;

    /**
     * Outcome of a finished build.
     */
    record Result(boolean complete, long millis, int changed, int chunks, int failed) {
    }

    private final MultiverseGamblingPlugin plugin;
    private final World world;
    private final CasinoBlueprint blueprint;
    private final int half;
    private final Consumer<Result> done;
    private final Deque<int[]> pending = new ArrayDeque<>();
    private final Deque<Chunk> ready = new ArrayDeque<>();
    private final Map<BlockData, Boolean> stateful = new IdentityHashMap<>();
    private final BlockData grass = Material.GRASS_BLOCK.createBlockData();
    private final BlockData air = Material.AIR.createBlockData();
    private final long started = System.currentTimeMillis();
    private Iterator<CasinoBlueprint.TreeSpot> trees;
    private BukkitTask task;
    private int inFlight;
    private int changed;
    private int processed;
    private int failed;
    private int total;
    private boolean finished;

    CasinoBuildJob(MultiverseGamblingPlugin plugin, World world, CasinoBlueprint blueprint, int size,
                   Consumer<Result> done) {
        this.plugin = plugin;
        this.world = world;
        this.blueprint = blueprint;
        this.half = size / 2;
        this.done = done;
        int minChunk = (-half) >> 4;
        int maxChunk = half >> 4;
        // From the middle outwards, so the plaza is ready first.
        java.util.List<int[]> chunks = new java.util.ArrayList<>();
        for (int x = minChunk; x <= maxChunk; x++) {
            for (int z = minChunk; z <= maxChunk; z++) {
                chunks.add(new int[]{x, z});
            }
        }
        chunks.sort(java.util.Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
        pending.addAll(chunks);
        total = chunks.size();
    }

    void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    boolean running() {
        return !finished;
    }

    /**
     * Share of the chunks already done, for progress messages.
     */
    double progress() {
        return total == 0 ? 1.0 : processed / (double) total;
    }

    /**
     * Stops the job where it is, for a plugin that is shutting down.
     */
    void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
        }
        for (Chunk chunk : ready) {
            chunk.removePluginChunkTicket(plugin);
        }
        ready.clear();
    }

    private void tick() {
        if (finished) {
            return;
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        try {
            while (inFlight < IN_FLIGHT && !pending.isEmpty()) {
                int[] coords = pending.poll();
                inFlight++;
                world.getChunkAtAsync(coords[0], coords[1], true).whenComplete((chunk, error) -> {
                    inFlight--;
                    if (finished) {
                        return;
                    }
                    if (error != null || chunk == null) {
                        failed++;
                        processed++;
                        return;
                    }
                    chunk.addPluginChunkTicket(plugin);
                    ready.add(chunk);
                });
            }
            while (!ready.isEmpty() && System.nanoTime() < deadline) {
                Chunk chunk = ready.poll();
                try {
                    paint(chunk);
                } finally {
                    chunk.removePluginChunkTicket(plugin);
                    processed++;
                }
            }
            if (!pending.isEmpty() || inFlight > 0 || !ready.isEmpty()) {
                return;
            }
            if (trees == null) {
                trees = blueprint.trees().iterator();
            }
            while (trees.hasNext() && System.nanoTime() < deadline) {
                grow(trees.next());
            }
            if (!trees.hasNext()) {
                finish(true);
            }
        } catch (RuntimeException error) {
            plugin.getLogger().severe("The casino build stopped: " + error);
            error.printStackTrace();
            finish(false);
        }
    }

    private void finish(boolean complete) {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
        }
        done.accept(new Result(complete && failed == 0, System.currentTimeMillis() - started, changed, total, failed));
    }

    /**
     * Makes one chunk look exactly like the design, from the ground up.
     */
    private void paint(Chunk chunk) {
        Map<Long, BlockData> design = blueprint.chunk(chunk.getX(), chunk.getZ());
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int floor = blueprint.floor();
        int top = Math.min(world.getMaxHeight() - 1, floor + CasinoBlueprint.CLEAR_HEIGHT);
        for (int lx = 0; lx < 16; lx++) {
            int x = (chunk.getX() << 4) + lx;
            if (x < -half || x > half) {
                continue;
            }
            for (int lz = 0; lz < 16; lz++) {
                int z = (chunk.getZ() << 4) + lz;
                if (z < -half || z > half) {
                    continue;
                }
                for (int y = floor; y <= top; y++) {
                    BlockData wanted = design.get(CasinoBlueprint.blockKey(x, y, z));
                    if (wanted == null) {
                        wanted = y == floor ? grass : air;
                    }
                    Material current = snapshot.getBlockType(lx, y, lz);
                    if (current == wanted.getMaterial()
                            && (!hasState(wanted) || snapshot.getBlockData(lx, y, lz).equals(wanted))) {
                        continue;
                    }
                    chunk.getBlock(lx, y, lz).setBlockData(wanted, false);
                    changed++;
                }
            }
        }
        // Leftovers of a show interrupted by a crash, and items dropped by the clearing.
        for (Entity entity : chunk.getEntities()) {
            if (entity.getScoreboardTags().contains(Props.TAG) || entity instanceof Item) {
                entity.remove();
            }
        }
    }

    private boolean hasState(BlockData data) {
        return stateful.computeIfAbsent(data, key -> key.getAsString().contains("["));
    }

    private void grow(CasinoBlueprint.TreeSpot spot) {
        int floor = blueprint.floor();
        world.getChunkAt(spot.x() >> 4, spot.z() >> 4);
        if (world.getBlockAt(spot.x(), floor, spot.z()).getType() != Material.GRASS_BLOCK) {
            return;
        }
        // Whatever tuft of grass stands there makes room for the trunk.
        world.getBlockAt(spot.x(), floor + 1, spot.z()).setType(Material.AIR, false);
        world.generateTree(new Location(world, spot.x(), floor + 1, spot.z()), spot.type());
    }
}
