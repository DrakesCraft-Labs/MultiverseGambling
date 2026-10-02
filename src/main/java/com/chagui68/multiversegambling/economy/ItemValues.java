package com.chagui68.multiversegambling.economy;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The reference value of items, read from {@code item-values.yml}: what an item is worth
 * in coins when it is turned into chips at the poker table.
 *
 * <p>An item is recognised by what it really is, never by its name or its look: a
 * Slimefun item by the id Slimefun stores in it, a MultiverseCreatures item by the
 * {@code msc_} key that plugin tags it with, and anything else by its vanilla material.
 * A custom item of either plugin that the file does not list has no value, so a renamed
 * piece of dirt can never pass for a legendary sword, and an unknown custom item is
 * refused rather than priced as the plain material it happens to be made of.</p>
 */
public final class ItemValues {

    /** Where an item comes from, which decides the table its value is looked up in. */
    public enum Source {
        VANILLA, SLIMEFUN, MULTIVERSE_CREATURES
    }

    /**
     * What an item is: its source and its id there.
     */
    public record Identity(Source source, String id) {
    }

    private static final String FILE = "item-values.yml";
    private static final String ADDONS_REPORT = "item-values-addons.yml";
    private static final NamespacedKey SLIMEFUN_ID = new NamespacedKey("slimefun", "slimefun_item");
    private static final String CREATURES = "multiversecreatures";

    private final MultiverseGamblingPlugin plugin;
    private Table table = Table.EMPTY;

    public ItemValues(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * (Re)reads the file, writing the default one first when it is missing.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Entries the server file lacks (a plugin update added items) come from the
        // default file shipped in the jar.
        try (Reader reader = new InputStreamReader(plugin.getResource(FILE), StandardCharsets.UTF_8)) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception error) {
            plugin.getLogger().warning("Could not read the default item values: " + error);
        }
        table = Table.of(yaml);
        plugin.getLogger().info("Item values: " + table.size() + " items listed in " + FILE + ".");
        // Addons register their items when they are enabled, which may be after this
        // plugin: the registry is read once the server has finished starting.
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, this::valueSlimefunRegistry);
        }
    }

    /**
     * Values every Slimefun item of the core and of every addon that the file does not
     * list, from the recipes Slimefun registered, and writes them to
     * {@code item-values-addons.yml} for the server owner to review.
     */
    public void valueSlimefunRegistry() {
        Table current = table;
        Map<String, RecipeValuer.Valued> values = SlimefunValuer.run(
                material -> current.coins(new Identity(Source.VANILLA, material)),
                id -> current.listedCoins(id),
                new File(plugin.getDataFolder(), ADDONS_REPORT), plugin.getLogger());
        Map<String, Double> generated = new HashMap<>();
        for (Map.Entry<String, RecipeValuer.Valued> entry : values.entrySet()) {
            generated.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().value());
        }
        table = current.withGenerated(generated);
    }

    /**
     * What the item is, judging by the data it carries.
     */
    public static Identity identify(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            String slimefun = data.get(SLIMEFUN_ID, PersistentDataType.STRING);
            if (slimefun != null && !slimefun.isEmpty()) {
                return new Identity(Source.SLIMEFUN, slimefun.toUpperCase(Locale.ROOT));
            }
            for (NamespacedKey key : data.getKeys()) {
                if (CREATURES.equals(key.getNamespace()) && key.getKey().startsWith("msc_")) {
                    return new Identity(Source.MULTIVERSE_CREATURES, key.getKey());
                }
            }
        }
        return new Identity(Source.VANILLA, item.getType().name());
    }

    /**
     * Value of one unit of the item in hundredths of a coin, or -1 when it has none.
     */
    public long centsOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return -1;
        }
        long cents = table.cents(item.getItemMeta() == null ? new Identity(Source.VANILLA, item.getType().name())
                : identifyAll(item));
        if (cents <= 0) {
            return -1;
        }
        if (table.durability() && item.getItemMeta() instanceof Damageable damageable) {
            int max = item.getType().getMaxDurability();
            if (max > 0 && damageable.hasDamage()) {
                double left = Math.max(0.0, (max - damageable.getDamage()) / (double) max);
                cents = (long) Math.floor(cents * left);
            }
        }
        return cents > 0 ? cents : -1;
    }

    /**
     * The identity of an item, trying every MultiverseCreatures key it carries: an item
     * can hold several of them (a state marker next to its id), and only the id is listed.
     */
    private Identity identifyAll(ItemStack item) {
        Identity identity = identify(item);
        if (identity.source() != Source.MULTIVERSE_CREATURES) {
            return identity;
        }
        for (NamespacedKey key : item.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (CREATURES.equals(key.getNamespace()) && table.cents(new Identity(Source.MULTIVERSE_CREATURES,
                    key.getKey())) > 0) {
                return new Identity(Source.MULTIVERSE_CREATURES, key.getKey());
            }
        }
        return identity;
    }

    public int size() {
        return table.size();
    }

    /**
     * The values of the file, free of Bukkit items so they can be checked by tests.
     */
    public record Table(double scale, boolean durability, Map<String, Double> vanilla,
                        Map<String, Double> slimefun, Map<String, Double> creatures,
                        Map<String, Double> addons, Map<String, Double> generated) {

        static final Table EMPTY = new Table(1.0, true, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        /**
         * Reads the sections of the file. {@code addons} may group its ids by addon.
         */
        public static Table of(YamlConfiguration yaml) {
            Map<String, Double> addons = new HashMap<>();
            ConfigurationSection section = yaml.getConfigurationSection("addons");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (section.isConfigurationSection(key)) {
                        ConfigurationSection addon = section.getConfigurationSection(key);
                        for (String id : addon.getKeys(false)) {
                            double value = addon.getDouble(id, 0.0);
                            if (value > 0) {
                                addons.put(id.toUpperCase(Locale.ROOT), value);
                            }
                        }
                    } else if (section.getDouble(key, 0.0) > 0) {
                        addons.put(key.toUpperCase(Locale.ROOT), section.getDouble(key));
                    }
                }
            }
            return new Table(Math.max(0.0, yaml.getDouble("scale", 1.0)),
                    yaml.getBoolean("scale-by-durability", true),
                    section(yaml, "vanilla", true), section(yaml, "slimefun", true),
                    section(yaml, "multiversecreatures", false), addons, Map.of());
        }

        /**
         * The same table plus the values computed from the Slimefun registry.
         */
        public Table withGenerated(Map<String, Double> values) {
            return new Table(scale, durability, vanilla, slimefun, creatures, addons, Map.copyOf(values));
        }

        /**
         * Value in coins, before the scale, that the file itself gives a Slimefun id.
         */
        public double listedCoins(String id) {
            String key = id.toUpperCase(Locale.ROOT);
            Double value = addons.get(key);
            if (value == null) {
                value = slimefun.get(key);
            }
            return value == null ? 0.0 : value;
        }

        /**
         * Value in coins, before the scale, or 0 when unknown.
         */
        public double coins(Identity identity) {
            Double value = switch (identity.source()) {
                case VANILLA -> vanilla.get(identity.id().toUpperCase(Locale.ROOT));
                case SLIMEFUN -> {
                    double listed = listedCoins(identity.id());
                    yield listed > 0 ? Double.valueOf(listed) : generated.get(identity.id().toUpperCase(Locale.ROOT));
                }
                case MULTIVERSE_CREATURES -> creatures.get(identity.id().toLowerCase(Locale.ROOT));
            };
            return value == null ? 0.0 : value;
        }

        private static Map<String, Double> section(YamlConfiguration yaml, String path, boolean upper) {
            Map<String, Double> out = new HashMap<>();
            ConfigurationSection section = yaml.getConfigurationSection(path);
            if (section == null) {
                return out;
            }
            for (String key : section.getKeys(false)) {
                double value = section.getDouble(key, 0.0);
                if (value > 0) {
                    out.put(upper ? key.toUpperCase(Locale.ROOT) : key.toLowerCase(Locale.ROOT), value);
                }
            }
            return out;
        }

        /**
         * Value in hundredths of a coin of one unit of that item, or 0 when unlisted.
         */
        public long cents(Identity identity) {
            double value = coins(identity);
            if (value <= 0) {
                return 0;
            }
            return Math.max(0, Math.round(value * scale * 100.0));
        }

        public int size() {
            return vanilla.size() + slimefun.size() + creatures.size() + addons.size() + generated.size();
        }
    }
}
