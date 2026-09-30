package com.chagui68.multiversegambling.game;

import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * One casino game.
 *
 * <p>{@link #open(Player)} is always the single entry point: solo games open the bet
 * selector, group games drop the player in the waiting room of the round in
 * progress.</p>
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

    /** Game name in the language of the viewer. */
    String displayName(CommandSender viewer);

    /** Description in the language of the viewer, line by line. */
    List<String> displayDescription(CommandSender viewer);

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

    /** Enters the game: opens the bet selector or the waiting room. */
    void open(Player player);

    /**
     * Action fired from a chat button ({@code /casino action ...}). Does nothing by
     * default: only the interactive games use it.
     */
    default void handleAction(Player player, String action, String[] args) {
    }

    /**
     * @return true when the player is currently inside this game. This is what lets a
     * chat button know which round it belongs to.
     */
    default boolean ownsPlayer(UUID playerId) {
        return false;
    }

    /** Extra menu line with the current state. */
    default List<String> statusLore() {
        return List.of();
    }

    /** How many people are waiting or playing right now. */
    default int activePlayers() {
        return 0;
    }

    /** Orderly shutdown: refunds the money of unfinished games. */
    default void shutdown() {
    }
}
