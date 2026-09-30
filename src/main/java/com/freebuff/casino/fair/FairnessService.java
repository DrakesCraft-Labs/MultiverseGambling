package com.freebuff.casino.fair;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.ProvablyFair;
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
 * Azar verificable del casino.
 *
 * <p>Al arrancar el servidor se genera un secreto y se publica su hash. Todas las
 * tiradas se derivan de ese secreto mas la semilla del jugador y un contador. Al
 * rotar el secreto se revela el anterior, y entonces cualquiera puede recalcular
 * las tiradas y comprobar que la casa no las retoco.</p>
 */
public final class FairnessService {

    /**
     * Identidad del casino cuando la tirada no es de un jugador concreto: turnos
     * de bomba, carreras, sorteos. Tener un UUID fijo hace que la auditoria de una
     * partida en grupo siga siendo reproducible.
     */
    public static final UUID HOUSE = new UUID(0L, 0L);

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private final CasinoPlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, String> clientSeeds = new HashMap<>();

    private String serverSeed;
    private String previousSeed;
    private long nonce;

    public FairnessService(CasinoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fairness.json");
        load();
        rotateServerSeed();
    }

    public boolean enabled() {
        return plugin.config().provablyFairEnabled();
    }

    /** Genera un secreto nuevo y deja el anterior disponible para auditoria. */
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

    /** Proxima tirada uniforme en [0,1) atribuida al jugador. */
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

    /** Texto listo para /casino verificar. */
    public String[] auditLines(UUID playerId) {
        String revealed = previousSeed == null ? null : ProvablyFair.sha256(previousSeed);
        return new String[] {
                "&7Secreto actual (hash): &f" + serverSeedHash(),
                "&7Secreto anterior: &f" + (previousSeed == null ? "ninguno todavia" : previousSeed),
                "&7Hash que dejo el anterior: &f" + (revealed == null ? "-" : revealed),
                "&7Tu semilla de cliente: &f" + clientSeed(playerId),
                "&7Tiradas emitidas en esta sesion: &f" + nonce,
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
                    // Entrada corrupta: se ignora sin romper la carga.
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo leer fairness.json: " + e.getMessage());
        }
    }

    public void save() {
        Map<String, String> raw = new HashMap<>();
        clientSeeds.forEach((id, seed) -> raw.put(id.toString(), seed));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("No se pudo crear la carpeta de datos del casino");
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(raw, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar fairness.json: " + e.getMessage());
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
