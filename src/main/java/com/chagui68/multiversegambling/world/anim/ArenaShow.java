package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * A tick driven show painted on one casino arena: blocks and temporary entities that
 * appear when a round starts and are put back exactly as they were when it ends.
 *
 * <p>The game keeps its own clock (the session of a solo round, the ticker of a group
 * one) and drives the show by hand, so the same show can animate a spin for a single
 * player or for a whole room. The money never lives here: a show only paints a result
 * that the provably fair generator already decided.</p>
 */
public abstract class ArenaShow {

    /** Ticks the scenery is left standing after the result, so the play can be seen. */
    private static final int LINGER_TICKS = 25;

    /** Shows whose scenery is still waiting to be taken down. */
    private static final Set<ArenaShow> LINGERING = ConcurrentHashMap.newKeySet();

    /**
     * Takes down every show still standing. Called when the plug-in stops, because the
     * delayed cleanup of a lingering show never gets to run then.
     */
    public static void clearAll() {
        for (ArenaShow show : LINGERING) {
            show.cleanup();
        }
        LINGERING.clear();
    }

    private final MultiverseGamblingPlugin plugin;
    private final ArenaStage stage;
    private final BlockPaint paint;
    private final List<Entity> props = new ArrayList<>();
    private final int duration;
    private int elapsed;
    private boolean started;
    private boolean settled;
    private boolean cancelled;
    private boolean cleaned;

    protected ArenaShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int duration) {
        this.plugin = plugin;
        this.stage = stage;
        this.paint = new BlockPaint(stage.world());
        this.duration = Math.max(1, duration);
    }

    protected final MultiverseGamblingPlugin plugin() {
        return plugin;
    }

    protected final ArenaStage stage() {
        return stage;
    }

    protected final BlockPaint paint() {
        return paint;
    }

    /**
     * How many frames the show lasts.
     */
    public final int duration() {
        return duration;
    }

    /**
     * True while the show is running and can still receive frames.
     */
    public final boolean active() {
        return started && !settled && !cancelled;
    }

    /**
     * Builds the scenery. Called once, on the tick the round starts.
     */
    public final void start() {
        if (started) {
            return;
        }
        started = true;
        stage().load();
        onStart();
    }

    /**
     * One frame of the show.
     */
    public final void tick() {
        if (!active()) {
            return;
        }
        onFrame(elapsed, duration);
        elapsed++;
    }

    /**
     * Last frame: paints the result, tells the audience and leaves the scenery standing
     * for a moment before taking it down.
     *
     * <p>The pause is what lets a player actually see the ball resting on the winning
     * pocket, the reels stopped or the tower bursting, instead of everything vanishing on
     * the tick the result is paid.</p>
     */
    public final void settle() {
        if (!started || settled || cancelled) {
            return;
        }
        onSettle();
        settled = true;
        if (!plugin.isEnabled()) {
            // Shutting down: nothing can be scheduled any more, so the scenery goes now.
            cleanup();
            return;
        }
        LINGERING.add(this);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            LINGERING.remove(this);
            cleanup();
        }, LINGER_TICKS);
    }

    /**
     * The round was abandoned: nothing is paid and the arena is left clean.
     */
    public final void cancel() {
        if (!started || settled || cancelled) {
            return;
        }
        cancelled = true;
        onCancel();
        cleanup();
    }

    private void cleanup() {
        if (cleaned) {
            return;
        }
        cleaned = true;
        for (Entity entity : props) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        props.clear();
        paint.restore();
    }

    /**
     * Progress of a frame, from 0 at the first to 1 at the last.
     */
    protected final double progress(int frame) {
        return Math.min(1, frame / (double) duration);
    }

    protected void onStart() {
    }

    protected void onFrame(int elapsed, int duration) {
    }

    protected void onSettle() {
    }

    protected void onCancel() {
    }

    /**
     * Spawns an entity that vanishes with the show.
     */
    protected final <T extends Entity> T spawn(Location location, Class<T> type, Consumer<T> setup) {
        T entity = stage().world().spawn(location, type);
        entity.setPersistent(false);
        if (setup != null) {
            setup.accept(entity);
        }
        props.add(entity);
        return entity;
    }

    /**
     * Paints one block relative to the middle of the arena: {@code dy = 0} is the layer
     * a player walks on, {@code dy = 1} sits on top of it. Also handy to clear a tile
     * back to air with {@code Material.AIR}.
     */
    protected final void tile(int dx, int dy, int dz, Material material) {
        paint().set(stage().arena().centerX() + dx, stage().floorY() + 1 + dy,
                stage().arena().centerZ() + dz, material);
    }

    /**
     * Flat disc of blocks, {@code dy} blocks above the ground of the arena.
     */
    protected final void disc(int dy, double radius, Material material) {
        int centerX = stage().arena().centerX();
        int centerZ = stage().arena().centerZ();
        int y = stage().floorY() + 1 + dy;
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    paint().set(centerX + dx, y, centerZ + dz, material);
                }
            }
        }
    }

    /**
     * One block thick circle of blocks, {@code dy} blocks above the ground.
     */
    protected final void ring(int dy, double radius, Material material) {
        int centerX = stage().arena().centerX();
        int centerZ = stage().arena().centerZ();
        int y = stage().floorY() + 1 + dy;
        double inner = Math.max(0, radius - 1);
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double distance = dx * dx + dz * dz;
                if (distance > inner * inner && distance <= radius * radius) {
                    paint().set(centerX + dx, y, centerZ + dz, material);
                }
            }
        }
    }

    /**
     * Sound for everybody gathered round the arena.
     */
    protected final void playSound(Sound sound, float volume, float pitch) {
        for (Player player : stage().nearbyPlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
