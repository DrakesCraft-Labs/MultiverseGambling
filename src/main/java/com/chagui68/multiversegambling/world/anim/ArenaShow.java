package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

/**
 * A show staged on one casino pavilion: display entities (and, when a show really
 * needs them, blocks) that appear when a round starts and vanish when it ends.
 *
 * <p>The game keeps its own clock (the session of a solo round, the ticker of a group
 * one) and drives the progress of the show by hand through {@link #tick()}, so the same
 * show can animate a spin for a single player or for a whole room. On top of that every
 * show owns a small clock of its own, {@link #onAnimate(int)}, for the things that move
 * no matter what the game is doing: blinking bulbs, a cylinder that is still turning, a
 * bomb that keeps throbbing. The money never lives here: a show only paints a result
 * that the provably fair generator already decided.</p>
 */
public abstract class ArenaShow {

    /** Ticks the scenery is left standing after the result, so the play can be seen. */
    private static final int LINGER_TICKS = 50;

    /** Shows whose scenery is still standing. */
    private static final Set<ArenaShow> STANDING = ConcurrentHashMap.newKeySet();

    /** The show standing on each pavilion, keyed by game id. */
    private static final Map<String, ArenaShow> BY_ARENA = new ConcurrentHashMap<>();

    /**
     * True while a round of that game is being shown on its pavilion. A second round
     * started meanwhile keeps its text animation instead of painting over the first one;
     * a show that is only lingering after its result does not count, it gives way.
     */
    public static boolean busy(String gameId) {
        ArenaShow show = BY_ARENA.get(gameId);
        return show != null && show.active();
    }

    /**
     * Takes down every show still standing. Called when the plug-in stops, because the
     * delayed cleanup of a lingering show never gets to run then.
     */
    public static void clearAll() {
        for (ArenaShow show : STANDING) {
            // One show that cannot be taken down must not leave the scenery of the
            // others standing, so each is cleared on its own.
            try {
                show.cleanup();
            } catch (RuntimeException error) {
                show.plugin.getLogger().severe("Could not take down the scenery of "
                        + show.stage.arena().gameId() + ": " + error);
            }
        }
        STANDING.clear();
        BY_ARENA.clear();
    }

    private final MultiverseGamblingPlugin plugin;
    private final ArenaStage stage;
    private final BlockPaint paint;
    private final List<Entity> props = new ArrayList<>();
    private final List<HoloButton> buttons = new ArrayList<>();
    private final int duration;
    private BukkitTask clock;
    private int age;
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
     * True once the result has been painted.
     */
    protected final boolean settled() {
        return settled;
    }

    /**
     * Builds the scenery. Called once, on the tick the round starts.
     */
    public final void start() {
        if (started) {
            return;
        }
        started = true;
        String gameId = stage.arena().gameId();
        ArenaShow previous = BY_ARENA.put(gameId, this);
        if (previous != null && previous != this && !previous.active()) {
            // The last round was only lingering: its scenery makes room for this one.
            previous.cleanup();
        }
        STANDING.add(this);
        stage().load();
        onStart();
        if (plugin.isEnabled()) {
            clock = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animate, 1L, 1L);
        }
    }

    private void animate() {
        if (cleaned) {
            stopClock();
            return;
        }
        age++;
        try {
            onAnimate(age);
        } catch (RuntimeException error) {
            // A broken flourish must not spam the console every tick: the show keeps its
            // result on screen and simply stops moving.
            plugin.getLogger().warning("The show of " + stage.arena().gameId() + " stopped animating: " + error);
            stopClock();
        }
    }

    private void stopClock() {
        if (clock != null) {
            clock.cancel();
            clock = null;
        }
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
     * pocket, the reels stopped or the rocket bursting, instead of everything vanishing
     * on the tick the result is paid.</p>
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
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, LINGER_TICKS + extraLinger());
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
        STANDING.remove(this);
        BY_ARENA.remove(stage.arena().gameId(), this);
        stopClock();
        for (Entity entity : props) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        props.clear();
        for (HoloButton button : buttons) {
            button.forget();
        }
        buttons.clear();
        paint.restore();
    }

    /**
     * Progress of a frame, from 0 at the first to 1 at the last.
     */
    protected final double progress(int frame) {
        return Math.min(1, frame / (double) duration);
    }

    /**
     * Extra ticks a show wants to stay up after its result, for a longer celebration.
     */
    protected int extraLinger() {
        return 0;
    }

    protected void onStart() {
    }

    protected void onFrame(int elapsed, int duration) {
    }

    /**
     * Called every server tick from the start of the show until it is taken down, the
     * lingering after the result included.
     *
     * @param age ticks since the show started, from 1
     */
    protected void onAnimate(int age) {
    }

    protected void onSettle() {
    }

    protected void onCancel() {
    }

    // ----------------------------------------------------------------- props

    /**
     * Spawns an entity that vanishes with the show. The setup runs before the entity is
     * added to the world, so the audience never sees it in its default shape.
     */
    protected final <T extends Entity> T spawn(Location location, Class<T> type, Consumer<T> setup) {
        T entity = stage().world().spawn(location, type, spawned -> {
            spawned.setPersistent(false);
            spawned.addScoreboardTag(Props.TAG);
            if (spawned instanceof Display display) {
                display.setBrightness(Props.FULL_BRIGHT);
                display.setViewRange(1.5f);
                display.setShadowRadius(0.0f);
            }
            if (setup != null) {
                setup.accept(spawned);
            }
        });
        props.add(entity);
        return entity;
    }

    /**
     * Location of a point of the show in the local frame of the stage.
     */
    protected final Location local(double x, double y, double z) {
        return stage().local(x, y, z);
    }

    /**
     * A block piece of the scenery, posed relative to the anchor.
     */
    protected final BlockDisplay block(Location anchor, Material material, Transformation pose) {
        return spawn(anchor, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(pose);
        });
    }

    /**
     * An item piece of the scenery, posed relative to the anchor.
     */
    protected final ItemDisplay item(Location anchor, ItemStack stack, Transformation pose) {
        return spawn(anchor, ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(pose);
        });
    }

    /**
     * An item piece made of a plain material.
     */
    protected final ItemDisplay item(Location anchor, Material material, Transformation pose) {
        return item(anchor, new ItemStack(material), pose);
    }

    /**
     * A text label: transparent background, drop shadow, facing the audience unless a
     * billboard says otherwise.
     */
    protected final TextDisplay text(Location anchor, Component content, Transformation pose,
                                     Display.Billboard billboard) {
        return spawn(anchor, TextDisplay.class, display -> {
            display.text(content);
            display.setBillboard(billboard);
            display.setShadowed(true);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(400);
            display.setTransformation(pose);
        });
    }

    void track(HoloButton button) {
        buttons.add(button);
    }

    /**
     * Distance from the middle of the stage to the row of buttons, in front of the main
     * watching spot and within reach of whoever stands there.
     */
    public final double controlZ() {
        return Math.max(3.0, plugin.config().worldAnimationsViewDistance() - 2.3);
    }

    /**
     * A floating button in the local frame of the stage, usually on the control row
     * ({@link #controlZ()}), at about chest height.
     *
     * @param owner the only player who can press it, or {@code null} for anybody
     */
    public final HoloButton addButton(double x, double y, double z, Component text, Color background,
                                      float scale, java.util.UUID owner, Consumer<Player> action) {
        return HoloButton.create(this, local(x, y, z), text, background, scale, owner, action);
    }

    /** Space left between two neighbouring buttons of a row, in blocks. */
    private static final double BUTTON_GAP = 0.55;
    /** Distance from the player to a row of buttons: well inside the three block reach. */
    private static final double BUTTON_REACH = 2.6;
    /** Farthest a row may bend round the player, either side. */
    private static final double MAX_ROW_ANGLE = Math.toRadians(70);

    /**
     * A row of floating buttons on an arc in front of the main watching spot, the middle
     * of the row straight ahead.
     *
     * <p>The row is measured first: every button is as wide as its label and they are
     * laid side by side with a clear gap between them, so labels never touch. A row too
     * long for the arc is pushed out a little (never out of reach) and, only if it still
     * does not fit, its buttons are drawn a bit smaller.</p>
     */
    public final java.util.List<HoloButton> addControlRow(double y, java.util.UUID owner,
                                                         java.util.List<HoloButton.Spec> specs) {
        int count = specs.size();
        double[] widths = new double[count];
        double length = BUTTON_GAP * Math.max(0, count - 1);
        for (int i = 0; i < count; i++) {
            widths[i] = HoloButton.widthOf(specs.get(i).text(), specs.get(i).scale());
            length += widths[i];
        }
        double maxArc = 2 * MAX_ROW_ANGLE;
        double reach = Math.min(3.3, Math.max(BUTTON_REACH, length / maxArc));
        double shrink = Math.min(1.0, reach * maxArc / length);
        double watcher = plugin.config().worldAnimationsViewDistance();
        java.util.List<HoloButton> made = new java.util.ArrayList<>(count);
        double along = -length * shrink / 2;
        for (int i = 0; i < count; i++) {
            HoloButton.Spec spec = specs.get(i);
            double width = widths[i] * shrink;
            double angle = (along + width / 2) / reach;
            along += width + BUTTON_GAP * shrink;
            double x = Math.sin(angle) * reach;
            double z = watcher - Math.cos(angle) * reach;
            made.add(addButton(x, y, z, spec.text(), spec.background(), (float) (spec.scale() * shrink),
                    owner, spec.action()));
        }
        return made;
    }

    /**
     * Removes one prop before the end of the show.
     */
    protected final void discard(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        props.remove(entity);
    }

    /**
     * Runs a step later, unless the show was taken down in the meantime.
     */
    protected final void later(int ticks, Runnable step) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!cleaned) {
                step.run();
            }
        }, Math.max(1, ticks));
    }

    // ------------------------------------------------------------------ blocks

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

    // ---------------------------------------------------------------- effects

    /**
     * Sound for everybody gathered round the arena.
     */
    protected final void playSound(Sound sound, float volume, float pitch) {
        for (Player player : stage().nearbyPlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * A puff of particles at a local point of the show.
     */
    protected final void particles(Particle particle, double x, double y, double z,
                                   int count, double spread, double speed) {
        stage().world().spawnParticle(particle, local(x, y, z), count, spread, spread, spread, speed);
    }

    /**
     * Coloured dust at a local point of the show.
     */
    protected final void dust(Color colour, double x, double y, double z, int count, double spread, float size) {
        stage().world().spawnParticle(Particle.DUST, local(x, y, z), count, spread, spread, spread, 0.0,
                new Particle.DustOptions(colour, size));
    }

    /**
     * The usual party for a win: confetti, totem sparkles and a cheer.
     */
    protected final void celebrate(double x, double y, double z) {
        particles(Particle.TOTEM_OF_UNDYING, x, y, z, 60, 0.8, 0.45);
        particles(Particle.FIREWORK, x, y, z, 40, 0.6, 0.12);
        particles(Particle.END_ROD, x, y + 0.5, z, 20, 1.0, 0.05);
        playSound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.9f, 1.1f);
        playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.4f);
    }
}
