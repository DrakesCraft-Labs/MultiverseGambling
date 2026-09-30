package com.freebuff.casino.session;

import java.util.UUID;

/**
 * Estado de juego que necesita avanzar tick a tick y que debe poder cerrarse
 * limpiamente si el jugador se va o el plugin se apaga.
 */
public interface SoloSession {

    UUID playerId();

    /** Identificador del juego, para saber si hay algo en curso. */
    String sessionId();

    /** Un tick del servidor. */
    void tick();

    /** @return false cuando la sesion ya no debe seguir recibiendo ticks. */
    boolean active();

    /** Cierre ordenado: debe devolver dinero y cerrar inventarios si toca. */
    void cancel();
}
