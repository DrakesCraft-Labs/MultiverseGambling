package com.freebuff.casino.config;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.util.Text;
import java.io.File;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/** Mensajes configurables. Cualquier clave ausente se ve de inmediato, no falla en silencio. */
public final class Messages {

    private static final String MISSING_PREFIX = "&c[faltante en messages.yml: ";

    private final CasinoPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    public Messages(CasinoPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration raw() {
        return cfg;
    }

    private String lookup(String key) {
        String value = cfg.getString(key);
        if (value == null) {
            return MISSING_PREFIX + key + "]";
        }
        return value;
    }

    public String prefix() {
        return lookup("prefijo");
    }

    /** Mensaje ya resuelto, con prefijo opcional. */
    public String get(String key, Object... replacements) {
        return Text.fill(lookup(key), replacements);
    }

    public String prefixed(String key, Object... replacements) {
        return prefix() + get(key, replacements);
    }

    public Component component(String key, Object... replacements) {
        return Text.c(get(key, replacements));
    }

    public List<String> lore(String key) {
        List<String> lines = cfg.getStringList(key);
        if (lines.isEmpty()) {
            String single = cfg.getString(key);
            return single == null ? List.of() : List.of(single);
        }
        return lines;
    }

    public boolean has(String key) {
        return cfg.isSet(key);
    }

    public void send(CommandSender to, String key, Object... replacements) {
        to.sendMessage(Text.c(prefixed(key, replacements)));
    }

    /** Envia sin prefijo, para lineas decorativas. */
    public void sendPlain(CommandSender to, String key, Object... replacements) {
        to.sendMessage(Text.c(get(key, replacements)));
    }

    public void sendRaw(CommandSender to, String legacyText) {
        to.sendMessage(Text.c(legacyText));
    }

    public void broadcastTo(Iterable<CommandSender> audience, String key, Object... replacements) {
        for (CommandSender sender : audience) {
            send(sender, key, replacements);
        }
    }
}
