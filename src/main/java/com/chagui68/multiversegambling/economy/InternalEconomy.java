package com.chagui68.multiversegambling.economy;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;

/**
 * The casino's own wallet, stored in {@code balances.json}.
 *
 * <p>It is used when the server has no Vault. Every write happens in memory and the
 * dump to disk is asynchronous, because the main thread cannot afford to touch the
 * disk in the middle of a roll.</p>
 */
public final class InternalEconomy implements EconomyProvider {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Double>>() {
    }.getType();

    private final MultiverseGamblingPlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    public InternalEconomy(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.json");
        load();
    }

    @Override
    public String name() {
        return "Internal wallet";
    }

    @Override
    public double balance(UUID playerId) {
        return balances.getOrDefault(playerId, 0.0);
    }

    @Override
    public boolean set(UUID playerId, double amount) {
        balances.put(playerId, Math.max(0, amount));
        return true;
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        Double current = balances.get(playerId);
        double balance = current == null ? 0 : current;
        if (balance + 1e-9 < amount) {
            return false;
        }
        balances.put(playerId, balance - amount);
        return true;
    }

    @Override
    public boolean deposit(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        balances.merge(playerId, amount, Double::sum);
        return true;
    }

    @Override
    public boolean createAccountIfMissing(UUID playerId, double startingBalance) {
        if (balances.containsKey(playerId)) {
            return false;
        }
        balances.put(playerId, Math.max(0, startingBalance));
        return true;
    }

    public Map<UUID, Double> snapshot() {
        return new HashMap<>(balances);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, Double> raw = gson.fromJson(reader, MAP_TYPE);
            if (raw == null) {
                return;
            }
            raw.forEach((key, value) -> {
                try {
                    balances.put(UUID.fromString(key), value == null ? 0.0 : value);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid UUID in balances.json: " + key);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read balances.json: " + e.getMessage());
        }
    }

    /**
     * Dumps to disk. Safe to call from an asynchronous thread.
     */
    public void save() {
        Map<String, Double> raw = new HashMap<>();
        balances.forEach((id, value) -> raw.put(id.toString(), value));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder");
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(raw, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save balances.json: " + e.getMessage());
        }
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
