package com.chagui68.multiversegambling.fair;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.ProvablyFair;
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

import org.bukkit.entity.Player;

/**
 * Provably fair randomness of the casino.
 *
 * <p>A secret is generated when the server starts and its hash is published. Every roll
 * is derived from that secret plus the player seed and a counter. When the secret
 * rotates the previous one is revealed, and then anybody can recompute the rolls and
 * check that the house did not touch them.</p>
 */
public final class FairnessService {

    /**
     * The identity of the casino when a roll does not belong to a specific player: bomb
     * turns, races, draws. Having a fixed UUID keeps the audit of a group round
     * reproducible.
     */
    public static final UUID HOUSE = new UUID(0L, 0L);

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private final MultiverseGamblingPlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, String> clientSeeds = new HashMap<>();

    private String serverSeed;
    private String previousSeed;
    private long nonce;

    public FairnessService(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fairness.json");
        load();
        rotateServerSeed();
    }

    public boolean enabled() {
        return plugin.config().provablyFairEnabled();
    }

    /**
     * Generates a new secret and leaves the previous one available for auditing.
     */
    public void rotateServerSeed() {
        previousSeed = serverSeed;
        serverSeed = ProvablyFair.randomSeed();
        nonce = 0;
    }

    public String serverSeedHash() {
        return ProvablyFair.sha256(serverSeed);
    }

    public String revealedSeed() {
        return previousSeed;
    }

    public String clientSeed(UUID playerId) {
        return clientSeeds.computeIfAbsent(playerId, id -> ProvablyFair.randomClientSeed());
    }

    public void setClientSeed(Player player, String seed) {
        String clean = seed == null ? "" : seed.replaceAll("[^\\x20-\\x7E]", "").trim();
        if (clean.isEmpty()) {
            clientSeeds.put(player.getUniqueId(), ProvablyFair.randomClientSeed());
        } else {
            clientSeeds.put(player.getUniqueId(), clean.length() > 64 ? clean.substring(0, 64) : clean);
        }
        saveAsync();
    }

    public long nextNonce() {
        return nonce++;
    }

    /**
     * Next uniform roll in [0,1) attributed to the player.
     */
    public double roll(UUID playerId) {
        if (!enabled()) {
            return Math.random();
        }
        return ProvablyFair.roll(serverSeed, clientSeed(playerId), nextNonce(), 0);
    }

    public double[] rolls(UUID playerId, int count) {
        if (!enabled()) {
            double[] out = new double[count];
            for (int i = 0; i < count; i++) {
                out[i] = Math.random();
            }
            return out;
        }
        return ProvablyFair.rollMany(serverSeed, clientSeed(playerId), nextNonce(), count);
    }

    public int rollInt(UUID playerId, int bound) {
        if (!enabled()) {
            return java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
        }
        return ProvablyFair.rollInt(serverSeed, clientSeed(playerId), nextNonce(), 0, bound);
    }

    /**
     * Text ready for /mvgam verify.
     */
    public String[] auditLines(UUID playerId) {
        String revealed = previousSeed == null ? null : ProvablyFair.sha256(previousSeed);
        return new String[]{
                "&7Current secret (hash): &f" + serverSeedHash(),
                "&7Previous secret: &f" + (previousSeed == null ? "none yet" : previousSeed),
                "&7Hash left by the previous secret: &f" + (revealed == null ? "-" : revealed),
                "&7Your client seed: &f" + clientSeed(playerId),
                "&7Rolls issued in this session: &f" + nonce,
        };
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
                try {
                    clientSeeds.put(UUID.fromString(key), value);
                } catch (IllegalArgumentException ignored) {
                    // Corrupt entry: ignored without breaking the load.
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read fairness.json: " + e.getMessage());
        }
    }

    public void save() {
        Map<String, String> raw = new HashMap<>();
        clientSeeds.forEach((id, seed) -> raw.put(id.toString(), seed));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder");
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(raw, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save fairness.json: " + e.getMessage());
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
