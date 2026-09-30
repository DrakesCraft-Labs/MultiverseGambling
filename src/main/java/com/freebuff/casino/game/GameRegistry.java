package com.freebuff.casino.game;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Catalogo de juegos y punto unico para tick, cierre y anuncios. */
public final class GameRegistry {

    private final CasinoPlugin plugin;
    private final Map<String, Game> games = new LinkedHashMap<>();

    public GameRegistry(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Game game) {
        games.put(game.id(), game);
        if (game instanceof AbstractGroupGame group) {
            // Los juegos en grupo comparten el reloj central del plugin.
            plugin.sessions().register(game.id(), group::tick);
        }
    }

    public Optional<Game> byId(String id) {
        return Optional.ofNullable(games.get(id));
    }

    /** Busca por id o por nombre, ignorando mayusculas y espacios. */
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
                .filter(game -> Text.strip(game.name()).toLowerCase().replace(' ', '_').contains(needle)
                        || game.id().contains(needle))
                .findFirst();
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

    /** El juego en el que esta metido un jugador ahora mismo, si hay alguno. */
    public Optional<Game> activeGameOf(java.util.UUID playerId) {
        for (Game game : games.values()) {
            if (game.ownsPlayer(playerId)) {
                return Optional.of(game);
            }
        }
        return Optional.empty();
    }

    /** Anuncia premios gordos para que el servidor lo vea. */
    public void announceWin(Player player, double bet, double payout) {
        double profit = payout - bet;
        if (!plugin.config().announceWins() || profit < plugin.config().announceThreshold()) {
            return;
        }
        plugin.getServer().broadcast(Text.c(plugin.messages().get("juegos.anuncio-global",
                "jugador", player.getName(),
                "premio", plugin.economy().format(payout),
                "beneficio", plugin.economy().format(profit))));
    }

    /** Devuelve el dinero de las partidas a medias y para todos los relojes. */
    public void shutdownAll() {
        for (Game game : games.values()) {
            try {
                game.shutdown();
            } catch (RuntimeException error) {
                plugin.getLogger().severe("Error cerrando el juego " + game.id() + ": " + error);
            }
        }
    }
}
