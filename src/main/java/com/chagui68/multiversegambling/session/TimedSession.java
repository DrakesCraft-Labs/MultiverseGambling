package com.chagui68.multiversegambling.session;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Animacion de duracion fija.
 *
 * <p>Sirve para todo lo que necesita suspense: el giro de la ruleta, el rodillo de
 * la tragaperras, la bolita del plinko o la sacudida de la bomba. La sesion se
 * apaga sola al terminar y no deja tareas colgadas.</p>
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

    /** Arranca la animacion registrandola en el reloj central. */
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

    /** Primer tick: sonido inicial, titulo, etc. */
    protected void onStart() {
    }

    /** Se llama en cada tick con el indice actual y la duracion total. */
    protected abstract void onFrame(int elapsed, int duration);

    /** Ultimo tick: aqui se entrega el resultado. */
    protected abstract void onFinish();

    /** El jugador se fue o el plugin se apago antes de terminar. */
    protected void onCancel() {
    }

    /** Facilidad para animaciones que solo quieren saber el progreso. */
    protected double progress(int elapsed) {
        return (double) elapsed / duration;
    }
}
