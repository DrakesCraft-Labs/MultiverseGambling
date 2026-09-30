package com.chagui68.multiversegambling.session;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Animation with a fixed duration.
 *
 * <p>It serves anything that needs suspense: the spin of the roulette, the slot reels,
 * the plinko ball or the shaking bomb. The session shuts itself down when it finishes
 * and leaves no dangling tasks.</p>
 */
public abstract class TimedSession implements SoloSession {

    private final MultiverseGamblingPlugin plugin;
    private final UUID playerId;
    private final String sessionId;
    private final int duration;
    private int elapsed;
    private boolean started;
    private boolean cancelled;

    protected TimedSession(MultiverseGamblingPlugin plugin, Player player, String sessionId, int duration) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.sessionId = sessionId;
        this.duration = Math.max(1, duration);
    }

    /** Starts the animation, registering it on the central clock. */
    public final void run() {
        plugin.sessions().track(this);
    }

    @Override
    public final UUID playerId() {
        return playerId;
    }

    @Override
    public final String sessionId() {
        return sessionId;
    }

    @Override
    public final void tick() {
        if (cancelled) {
            return;
        }
        if (!started) {
            started = true;
            onStart();
        }
        if (elapsed >= duration) {
            cancelled = true;
            onFinish();
            return;
        }
        onFrame(elapsed, duration);
        elapsed++;
    }

    @Override
    public final boolean active() {
        return !cancelled;
    }

    @Override
    public void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        onCancel();
    }

    protected Player player() {
        return plugin.getServer().getPlayer(playerId);
    }

    /** First tick: opening sound, title and so on. */
    protected void onStart() {
    }

    /** Called on every tick with the current index and the total duration. */
    protected abstract void onFrame(int elapsed, int duration);

    /** Last tick: the result is handed over here. */
    protected abstract void onFinish();

    /** The player left or the plugin shut down before finishing. */
    protected void onCancel() {
    }

    /** Helper for animations that only want to know the progress. */
    protected double progress(int elapsed) {
        return (double) elapsed / duration;
    }
}
