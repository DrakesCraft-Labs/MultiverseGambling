package com.freebuff.casino.session;

import com.freebuff.casino.CasinoPlugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

/**
 * Reloj unico de las partidas en solitario.
 *
 * <p>Un solo planificador para todo el plugin en vez de una tarea por jugador:
 * asi apagar el plugin cierra todas las partidas a la vez y ningun inventario se
 * queda huerfano.</p>
 */
public final class SessionManager {

    private final CasinoPlugin plugin;
    private final Map<UUID, SoloSession> sessions = new LinkedHashMap<>();
    private final Map<String, AbstractGroupTicker> tickers = new LinkedHashMap<>();
    private BukkitTask task;

    /** Permite que los juegos en grupo se enganchen al mismo reloj. */
    public interface AbstractGroupTicker {
        void tick();
    }

    public SessionManager(CasinoPlugin plugin) {
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

    /** Cierra la sesion de un jugador, por ejemplo al desconectarse. */
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
                plugin.getLogger().severe("Error en la sesion " + session.sessionId()
                        + " de " + session.playerId() + ": " + error);
                error.printStackTrace();
                session.cancel();
                sessions.remove(session.playerId());
            }
        }
        for (AbstractGroupTicker ticker : tickers.values()) {
            try {
                ticker.tick();
            } catch (RuntimeException error) {
                plugin.getLogger().severe("Error en un juego en grupo: " + error);
                error.printStackTrace();
            }
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (SoloSession session : new ArrayList<>(sessions.values())) {
            session.cancel();
        }
        sessions.clear();
        tickers.clear();
    }
}
