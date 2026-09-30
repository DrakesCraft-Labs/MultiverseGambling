package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public Optional<Game> byId(String id) {
        return Optional.ofNullable(games.get(id));
    }

    /**
     * Looks a game up by id or by name, ignoring case and spaces. The translated name
     * counts too, so {@code /mvgam play ruleta} works on a Spanish server.
     */
    public Optional<Game> search(String query) {
        if (query == null) {
            return Optional.empty();
        }
        String needle = query.toLowerCase().replace(' ', '_');
        Game direct = games.get(needle);
        if (direct != null) {
            return Optional.of(direct);
        }
        return games.values().stream()
                .filter(game -> matches(game, needle))
                .findFirst();
    }

    private boolean matches(Game game, String needle) {
        String localized = plugin.messages().getOr("catalog." + game.id() + ".name", game.name());
        return Text.strip(localized).toLowerCase().replace(' ', '_').contains(needle)
                || Text.strip(game.name()).toLowerCase().replace(' ', '_').contains(needle)
                || game.id().contains(needle);
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
