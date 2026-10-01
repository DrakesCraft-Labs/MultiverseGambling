package com.chagui68.multiversegambling.economy;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.JsonStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
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
 *
 * <p>A balances.json that cannot be parsed never stops the plugin: {@link JsonStore}
 * moves it aside and the wallet starts empty.</p>
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
        Map<String, Double> raw = JsonStore.read(plugin.getLogger(), file, gson, MAP_TYPE);
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
    }

    /**
     * Dumps to disk. Safe to call from an asynchronous thread.
     */
    public void save() {
        Map<String, Double> raw = new HashMap<>();
        balances.forEach((id, value) -> raw.put(id.toString(), value));
        JsonStore.write(plugin.getLogger(), file, gson, MAP_TYPE, raw);
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
