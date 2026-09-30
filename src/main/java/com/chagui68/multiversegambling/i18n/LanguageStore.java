package com.chagui68.multiversegambling.i18n;

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

/**
 * Remembers which language each player picked with {@code /casino language}.
 *
 * <p>Kept apart from the statistics on purpose: a player may wipe their casino
 * history and still want their language (or the other way around). Stored as a
 * plain {@code uuid -> code} map in {@code languages.json}.</p>
 */
public final class LanguageStore {

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private final MultiverseGamblingPlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, String> chosen = new HashMap<>();

    public LanguageStore(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "languages.json");
        load();
    }

    /** Chosen code, or {@code null} when the player never picked one. */
    public String codeOf(UUID playerId) {
        return chosen.get(playerId);
    }

    /** Stores the choice and writes it in the background. */
    public void set(UUID playerId, String code) {
        if (code == null) {
            chosen.remove(playerId);
        } else {
            chosen.put(playerId, code.toLowerCase(java.util.Locale.ROOT));
        }
        saveAsync();
    }

    /** Forgets the choice, so the player falls back to their client language. */
    public void clear(UUID playerId) {
        if (chosen.remove(playerId) != null) {
            saveAsync();
        }
    }

    public int players() {
        return chosen.size();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, String> raw = gson.fromJson(reader, MAP_TYPE);
            if (raw == null) {
                return;
            }
            raw.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                try {
                    chosen.put(UUID.fromString(key), value.toLowerCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid UUID in languages.json: " + key);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read languages.json: " + e.getMessage());
        }
    }

    public void save() {
        Map<String, String> raw = new HashMap<>();
        chosen.forEach((id, code) -> raw.put(id.toString(), code));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder");
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(raw, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save languages.json: " + e.getMessage());
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
