package com.chagui68.multiversegambling.stats;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registro de todo lo que pasa en el casino, en {@code stats.json}. */
public final class StatsStore {

    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerStats>>() {
    }.getType();

    public record TopEntry(UUID playerId, String name, PlayerStats stats) {
    }

    private final MultiverseGamblingPlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    public StatsStore(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.json");
        load();
    }

    public PlayerStats of(UUID playerId) {
        return stats.computeIfAbsent(playerId, id -> new PlayerStats());
    }

    public PlayerStats peek(UUID playerId) {
        return stats.get(playerId);
    }

    /**
     * Anota una jugada ya liquidada.
     *
     * @param bet    lo que se retiro del monedero
     * @param payout lo que se devolvio al jugador
     */
    public void record(UUID playerId, String gameId, double bet, double payout) {
        PlayerStats entry = of(playerId);
        entry.games++;
        entry.wagered += bet;
        entry.returned += payout;
        entry.plays.merge(gameId, 1, Integer::sum);
        if (payout > bet) {
            entry.wins++;
        }
        entry.biggestWin = Math.max(entry.biggestWin, payout);
        entry.biggestLoss = Math.max(entry.biggestLoss, bet - payout);
    }

    /** Ranking por ganancia neta. */
    public List<TopEntry> topByProfit(int limit) {
        return ranking(Comparator.comparingDouble((TopEntry entry) -> entry.stats().profit()).reversed(), limit);
    }

    public List<TopEntry> topByWagered(int limit) {
        return ranking(Comparator.comparingDouble((TopEntry entry) -> entry.stats().wagered).reversed(), limit);
    }

    public List<TopEntry> topByBiggestWin(int limit) {
        return ranking(Comparator.comparingDouble((TopEntry entry) -> entry.stats().biggestWin).reversed(), limit);
    }

    private List<TopEntry> ranking(Comparator<TopEntry> comparator, int limit) {
        List<TopEntry> entries = new ArrayList<>();
        stats.forEach((id, value) -> entries.add(new TopEntry(id, nameOf(id), value)));
        entries.sort(comparator);
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private String nameOf(UUID playerId) {
        var player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        // Un jugador puede no tener nombre resoluble (cuenta borrada del proxy),
        // y un "null" en el ranking queda fatal.
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    /** Suma de todo el dinero que ha ganado la casa. */
    public double houseProfit() {
        double profit = 0;
        for (PlayerStats entry : stats.values()) {
            profit -= entry.profit();
        }
        return profit;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, PlayerStats> raw = gson.fromJson(reader, MAP_TYPE);
            if (raw == null) {
                return;
            }
            raw.forEach((key, value) -> {
                try {
                    stats.put(UUID.fromString(key), value == null ? new PlayerStats() : value);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("UUID invalido en stats.json: " + key);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo leer stats.json: " + e.getMessage());
        }
    }

    public void save() {
        Map<String, PlayerStats> raw = new HashMap<>();
        stats.forEach((id, value) -> raw.put(id.toString(), value));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("No se pudo crear la carpeta de datos del casino");
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(raw, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar stats.json: " + e.getMessage());
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
