package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * Game catalogue and the single place that ticks, closes and announces.
 */
public final class GameRegistry {

    private final MultiverseGamblingPlugin plugin;
    private final Map<String, Game> games = new LinkedHashMap<>();

    public GameRegistry(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Game game) {
        games.put(game.id(), game);
        if (game instanceof AbstractGroupGame group) {
            // Group games share the single plugin clock.
            plugin.sessions().register(game.id(), group::tick);
        }
    }

    /**
     * Looks a game up by its exact id, ignoring case. Display names, translated names and
     * partial ids are deliberately rejected: {@code /mvgam play} only answers to the
     * stable id shown by {@code /mvgam games}.
     */
    public Optional<Game> byId(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(games.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<Game> all() {
        return new ArrayList<>(games.values());
    }

    public List<Game> byCategory(GameCategory category) {
        return games.values().stream().filter(game -> game.category() == category).toList();
    }

    public List<Game> enabled() {
        return games.values().stream().filter(Game::enabled).toList();
    }

    /**
     * The game a player is currently inside, if any.
     */
    public Optional<Game> activeGameOf(UUID playerId) {
        for (Game game : games.values()) {
            if (game.ownsPlayer(playerId)) {
                return Optional.of(game);
            }
        }
        return Optional.empty();
    }

    /**
     * Announces big wins so the whole server sees them.
     */
    public void announceWin(Player player, double bet, double payout) {
        double profit = payout - bet;
        if (!plugin.config().announceWins() || profit < plugin.config().announceThreshold()) {
            return;
        }
        plugin.getServer().broadcast(Text.c(plugin.messages().get("games.global-announcement",
                "player", player.getName(),
                "prize", plugin.economy().format(payout),
                "profit", plugin.economy().format(profit))));
    }

    /**
     * Refunds unfinished games and stops every clock.
     */
    public void shutdownAll() {
        for (Game game : games.values()) {
            try {
                game.shutdown();
            } catch (RuntimeException error) {
                plugin.getLogger().severe("Error while closing the game " + game.id() + ": " + error);
            }
        }
    }
}
