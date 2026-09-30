package com.chagui68.multiversegambling.session;

import java.util.UUID;

/**
 * Game state that must advance tick by tick and must be able to close cleanly when the
 * player leaves or the plugin shuts down.
 */
public interface SoloSession {

    UUID playerId();

    /**
     * Game identifier, to know whether something is running.
     */
    String sessionId();

    /**
     * One server tick.
     */
    void tick();

    /**
     * @return false when the session should stop receiving ticks.
     */
    boolean active();

    /**
     * Orderly shutdown: it must refund money and close inventories when needed.
     */
    void cancel();
}
