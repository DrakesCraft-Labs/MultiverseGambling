package com.chagui68.multiversegambling.session;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.scheduler.BukkitTask;

/**
 * The single clock of the solo games.
 *
 * <p>One scheduler for the whole plugin instead of one task per player: that way
 * disabling the plugin closes every round at once and no inventory is left orphaned.</p>
 */
public final class SessionManager {

    private final MultiverseGamblingPlugin plugin;
    private final Map<UUID, SoloSession> sessions = new LinkedHashMap<>();
    private final Map<String, AbstractGroupTicker> tickers = new LinkedHashMap<>();
    private BukkitTask task;

    /**
     * Lets the group games hook into the same clock.
     */
    public interface AbstractGroupTicker {
        void tick();
    }

    public SessionManager(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void register(String key, AbstractGroupTicker ticker) {
        tickers.put(key, ticker);
    }

    public void track(SoloSession session) {
        SoloSession previous = sessions.get(session.playerId());
        if (previous != null && previous != session) {
            previous.cancel();
        }
        sessions.put(session.playerId(), session);
    }

    public Optional<SoloSession> of(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public boolean busy(UUID playerId) {
        SoloSession session = sessions.get(playerId);
        return session != null && session.active();
    }

    /**
     * Closes the session of one player, for example when they disconnect.
     */
    public void cancel(UUID playerId) {
        SoloSession session = sessions.remove(playerId);
        if (session != null) {
            session.cancel();
        }
    }

    private void tick() {
        for (SoloSession session : new ArrayList<>(sessions.values())) {
            if (!session.active()) {
                sessions.remove(session.playerId());
                continue;
            }
            try {
                session.tick();
            } catch (RuntimeException error) {
                plugin.getLogger().severe("Error in the session " + session.sessionId()
                        + " of " + session.playerId() + ": " + error);
                error.printStackTrace();
                session.cancel();
                sessions.remove(session.playerId());
            }
        }
        for (AbstractGroupTicker ticker : tickers.values()) {
            try {
                ticker.tick();
            } catch (RuntimeException error) {
                plugin.getLogger().severe("Error in a group game: " + error);
                error.printStackTrace();
            }
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // One round that cannot be closed must not leave the others holding a bet:
        // every session is cancelled on its own.
        for (SoloSession session : new ArrayList<>(sessions.values())) {
            try {
                session.cancel();
            } catch (RuntimeException error) {
                plugin.getLogger().severe("Could not close the session " + session.sessionId()
                        + " of " + session.playerId() + ": " + error);
            }
        }
        sessions.clear();
        tickers.clear();
    }
}
