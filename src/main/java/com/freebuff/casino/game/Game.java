package com.freebuff.casino.game;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Un juego del casino.
 *
 * <p>{@link #open(Player)} es siempre la puerta de entrada: en los juegos en
 * solitario abre el selector de apuesta, y en los de grupo mete al jugador en la
 * sala de espera de la ronda en curso.</p>
 */
public interface Game {

    GameMeta meta();

    default String id() {
        return meta().id();
    }

    default String name() {
        return meta().name();
    }

    default GameCategory category() {
        return meta().category();
    }

    default Material icon() {
        return meta().icon();
    }

    default List<String> description() {
        return meta().description();
    }

    default String permission() {
        return meta().permission();
    }

    default int minPlayers() {
        return meta().minPlayers();
    }

    default int maxPlayers() {
        return meta().maxPlayers();
    }

    default boolean solo() {
        return category() == GameCategory.SOLO;
    }

    double minBet();

    double maxBet();

    boolean enabled();

    /** Entra al juego: abre la apuesta o la sala de espera. */
    void open(Player player);

    /**
     * Accion lanzada desde un boton del chat ({@code /casino accion ...}).
     * Por defecto no se hace nada: solo los juegos interactivos la usan.
     */
    default void handleAction(Player player, String action, String[] args) {
    }

    /**
     * @return true si el jugador esta metido en este juego ahora mismo. Es lo que
     *         permite que un boton de chat sepa a que partida pertenece.
     */
    default boolean ownsPlayer(java.util.UUID playerId) {
        return false;
    }

    /** Linea extra en el menu con el estado actual. */
    default List<String> statusLore() {
        return List.of();
    }

    /** Cuanta gente hay esperando o jugando ahora mismo. */
    default int activePlayers() {
        return 0;
    }

    /** Cierre ordenado: devuelve el dinero de las partidas a medias. */
    default void shutdown() {
    }
}
