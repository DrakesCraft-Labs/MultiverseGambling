package com.chagui68.multiversegambling.economy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Values items from their recipes, free of Bukkit and of Slimefun so it can be tested:
 * the same rule used to write the Slimefun part of {@code item-values.yml}, applied at
 * runtime to every item of every addon the server has installed.
 *
 * <p>An item is worth what its ingredients are worth, times what its machine or ritual
 * adds, plus a little for each experience level its research costs, divided by how many
 * the recipe makes. Items whose "recipe" only says where they are found (a mob drop, a
 * GEO resource, a barter) and items without a recipe get a fixed base value instead, and
 * so does any item caught in a loop of recipes.</p>
 */
public final class RecipeValuer {

    /** Coins added per experience level of the research that unlocks an item. */
    public static final double LEVEL_VALUE = 0.5;
    /** Value of an item found in the world rather than made, before its research. */
    public static final double FOUND_VALUE = 25.0;

    /** What a machine or ritual adds to the materials it uses, by recipe type key. */
    private static final Map<String, Double> FACTORS = Map.ofEntries(
            Map.entry("enhanced_crafting_table", 1.05), Map.entry("armor_forge", 1.05),
            Map.entry("magic_workbench", 1.15), Map.entry("ancient_altar", 1.40),
            Map.entry("smeltery", 1.08), Map.entry("ore_crusher", 1.02), Map.entry("grind_stone", 1.02),
            Map.entry("compressor", 1.05), Map.entry("pressure_chamber", 1.05),
            Map.entry("heated_pressure_chamber", 1.15), Map.entry("juicer", 1.02), Map.entry("freezer", 1.05),
            Map.entry("refinery", 1.15), Map.entry("ore_washer", 1.0));
    private static final double DEFAULT_FACTOR = 1.05;

    /** Recipe types that only show where an item is found. */
    private static final Set<String> FOUND = Set.of("mob_drop", "barter_drop", "interact", "geo_miner",
            "gold_pan", "nuclear_reactor", "null", "");

    /** Recipe types of structures built from blocks, never carried as an item. */
    private static final Set<String> STRUCTURES = Set.of("multiblock");

    /**
     * One ingredient: a Slimefun id or a vanilla material, and how many.
     */
    public record Ingredient(boolean slimefun, String id, int count) {
    }

    /**
     * Everything the valuer needs to know about one Slimefun item.
     *
     * @param type     key of its recipe type, such as {@code enhanced_crafting_table}
     * @param material its vanilla material, for the fallback value
     */
    public record Node(String id, String name, String addon, String type, List<Ingredient> recipe, int output,
                       int researchLevels, String material) {
    }

    /** A computed value and the reason for it. */
    public record Valued(String id, String name, String addon, double value, String reason) {
    }

    private final Map<String, Node> nodes = new HashMap<>();
    private final ToDoubleFunction<String> vanilla;
    private final ToDoubleFunction<String> listed;
    private final Map<String, Valued> done = new LinkedHashMap<>();
    private final Set<String> visiting = new HashSet<>();

    /**
     * @param vanilla value of a vanilla material, 0 when unknown
     * @param listed  value an item already has in the file, 0 when it has none
     */
    public RecipeValuer(List<Node> items, ToDoubleFunction<String> vanilla, ToDoubleFunction<String> listed) {
        for (Node node : items) {
            nodes.put(node.id().toUpperCase(Locale.ROOT), node);
        }
        this.vanilla = vanilla;
        this.listed = listed;
    }

    /**
     * Values every item that the file does not list yet. Structures are left out.
     */
    public Map<String, Valued> valueAll() {
        for (Node node : nodes.values()) {
            if (listed.applyAsDouble(node.id()) <= 0 && !STRUCTURES.contains(type(node))) {
                value(node.id());
            }
        }
        Map<String, Valued> out = new LinkedHashMap<>();
        for (Map.Entry<String, Valued> entry : done.entrySet()) {
            if (listed.applyAsDouble(entry.getKey()) <= 0) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    /**
     * Value of one Slimefun item, in coins.
     */
    public double value(String rawId) {
        String id = rawId.toUpperCase(Locale.ROOT);
        double fixed = listed.applyAsDouble(id);
        if (fixed > 0) {
            return fixed;
        }
        Valued known = done.get(id);
        if (known != null) {
            return known.value();
        }
        Node node = nodes.get(id);
        if (node == null) {
            return FOUND_VALUE;
        }
        String type = type(node);
        if (STRUCTURES.contains(type)) {
            return 0;
        }
        double research = node.researchLevels() * LEVEL_VALUE;
        if (FOUND.contains(type) || node.recipe().isEmpty() || !visiting.add(id)) {
            double base = Math.max(FOUND_VALUE, vanilla.applyAsDouble(node.material()));
            return remember(node, base + research, FOUND.contains(type) ? "found: " + type : "no recipe");
        }
        try {
            double total = 0;
            for (Ingredient ingredient : node.recipe()) {
                double unit = ingredient.slimefun() ? value(ingredient.id())
                        : Math.max(0.0, vanilla.applyAsDouble(ingredient.id()));
                if (!ingredient.slimefun() && unit <= 0) {
                    unit = 1.0;
                }
                total += unit * Math.max(1, ingredient.count());
            }
            double value = (total * FACTORS.getOrDefault(type, DEFAULT_FACTOR) + research)
                    / Math.max(1, node.output());
            return remember(node, Math.max(0.5, value), type.isEmpty() ? "crafted" : type);
        } finally {
            visiting.remove(id);
        }
    }

    private double remember(Node node, double value, String reason) {
        double rounded = Math.round(value * 100.0) / 100.0;
        done.put(node.id().toUpperCase(Locale.ROOT), new Valued(node.id(), node.name(), node.addon(), rounded, reason));
        return rounded;
    }

    private static String type(Node node) {
        return node.type() == null ? "" : node.type().toLowerCase(Locale.ROOT);
    }
}
