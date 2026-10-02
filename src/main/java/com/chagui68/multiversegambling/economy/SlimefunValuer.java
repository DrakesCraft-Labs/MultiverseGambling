package com.chagui68.multiversegambling.economy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Reads every item Slimefun has registered (the core and every addon installed) and
 * values the ones {@code item-values.yml} does not list, from the recipes Slimefun
 * really uses.
 *
 * <p>Slimefun is read by reflection through its own class loader, so the plugin needs
 * no compile time dependency on it and works with the DrakesCraft fork
 * ({@code com.github.drakescraft_labs.slimefun4}) as well as with the original
 * ({@code io.github.thebusybiscuit.slimefun4}). Anything unexpected is logged and the
 * file values keep working on their own.</p>
 */
public final class SlimefunValuer {

    private static final String[] PACKAGES = {
            "com.github.drakescraft_labs.slimefun4", "io.github.thebusybiscuit.slimefun4"};

    private SlimefunValuer() {
    }

    /**
     * Values the registered items the file does not list.
     *
     * @param vanilla value of a vanilla material in coins, 0 when unknown
     * @param listed  value an id already has in the file, 0 when it has none
     * @param report  where to write the values for the server owner, or {@code null}
     * @return the new values by Slimefun id, empty when Slimefun is not installed
     */
    public static Map<String, RecipeValuer.Valued> run(ToDoubleFunction<String> vanilla,
                                                       ToDoubleFunction<String> listed, File report, Logger log) {
        Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
        if (slimefun == null || !slimefun.isEnabled()) {
            return Map.of();
        }
        try {
            List<RecipeValuer.Node> nodes = read(slimefun);
            Map<String, RecipeValuer.Valued> values = new RecipeValuer(nodes, vanilla, listed).valueAll();
            if (report != null) {
                write(report, values, nodes.size());
            }
            log.info("Slimefun: " + nodes.size() + " registered items read, " + values.size()
                    + " valued from their recipes (see " + (report == null ? "-" : report.getName()) + ").");
            return values;
        } catch (ReflectiveOperationException | RuntimeException error) {
            log.warning("Could not read the Slimefun registry, only item-values.yml is used: " + error);
            return Map.of();
        }
    }

    private static List<RecipeValuer.Node> read(Plugin slimefun) throws ReflectiveOperationException {
        ClassLoader loader = slimefun.getClass().getClassLoader();
        String base = null;
        for (String candidate : PACKAGES) {
            try {
                Class.forName(candidate + ".api.items.SlimefunItem", false, loader);
                base = candidate;
                break;
            } catch (ClassNotFoundException missing) {
                // Try the next package.
            }
        }
        if (base == null) {
            throw new ClassNotFoundException("SlimefunItem");
        }
        Class<?> itemClass = Class.forName(base + ".api.items.SlimefunItem", true, loader);
        Class<?> mainClass = Class.forName(base + ".implementation.Slimefun", true, loader);
        Object registry = mainClass.getMethod("getRegistry").invoke(null);
        List<?> items = (List<?>) registry.getClass().getMethod("getAllSlimefunItems").invoke(registry);

        Method getId = itemClass.getMethod("getId");
        Method getRecipe = itemClass.getMethod("getRecipe");
        Method getRecipeType = itemClass.getMethod("getRecipeType");
        Method getOutput = itemClass.getMethod("getRecipeOutput");
        Method getResearch = itemClass.getMethod("getResearch");
        Method getAddon = itemClass.getMethod("getAddon");
        Method getName = itemClass.getMethod("getItemName");
        Method getItem = itemClass.getMethod("getItem");
        Method byItem = itemClass.getMethod("getByItem", ItemStack.class);

        List<RecipeValuer.Node> nodes = new ArrayList<>(items.size());
        for (Object item : items) {
            try {
                String id = (String) getId.invoke(item);
                ItemStack[] recipe = (ItemStack[]) getRecipe.invoke(item);
                List<RecipeValuer.Ingredient> ingredients = new ArrayList<>();
                if (recipe != null) {
                    for (ItemStack stack : recipe) {
                        if (stack == null || stack.getType().isAir()) {
                            continue;
                        }
                        Object other = byItem.invoke(null, stack);
                        if (other != null) {
                            ingredients.add(new RecipeValuer.Ingredient(true, (String) getId.invoke(other),
                                    stack.getAmount()));
                        } else {
                            ingredients.add(new RecipeValuer.Ingredient(false, stack.getType().name(),
                                    stack.getAmount()));
                        }
                    }
                }
                Object type = getRecipeType.invoke(item);
                String typeKey = "";
                if (type != null) {
                    Object key = type.getClass().getMethod("getKey").invoke(type);
                    typeKey = key instanceof NamespacedKey namespaced ? namespaced.getKey() : String.valueOf(key);
                }
                ItemStack output = (ItemStack) getOutput.invoke(item);
                Object research = getResearch.invoke(item);
                int levels = 0;
                if (research != null) {
                    Object cost = research.getClass().getMethod("getCost").invoke(research);
                    levels = cost instanceof Number number ? number.intValue() : 0;
                }
                Object addon = getAddon.invoke(item);
                String addonName = addon == null ? "Slimefun"
                        : String.valueOf(addon.getClass().getMethod("getName").invoke(addon));
                ItemStack shown = (ItemStack) getItem.invoke(item);
                nodes.add(new RecipeValuer.Node(id, strip(String.valueOf(getName.invoke(item))), addonName,
                        typeKey, ingredients, output == null ? 1 : Math.max(1, output.getAmount()), levels,
                        shown == null ? "" : shown.getType().name()));
            } catch (ReflectiveOperationException | RuntimeException broken) {
                // One odd item of one addon never stops the others from being valued.
            }
        }
        return nodes;
    }

    /**
     * The values for the server owner, grouped by addon, with the name of each item and
     * where its value came from.
     */
    private static void write(File file, Map<String, RecipeValuer.Valued> values, int read) {
        Map<String, Map<String, RecipeValuer.Valued>> byAddon = new TreeMap<>();
        for (RecipeValuer.Valued valued : values.values()) {
            byAddon.computeIfAbsent(valued.addon(), a -> new TreeMap<>()).put(valued.id(), valued);
        }
        StringBuilder out = new StringBuilder();
        out.append("# ---------------------------------------------------------------------------\n");
        out.append("#  Generated on every start from the recipes Slimefun registered: ").append(read)
                .append(" items read,\n#  ").append(values.size())
                .append(" valued here because item-values.yml does not list them.\n");
        out.append("#\n#  Same rule as item-values.yml: ingredients x what the machine adds, plus 0.5\n");
        out.append("#  coins per research level, divided by the amount made. Found items (drops,\n");
        out.append("#  GEO resources...) and items without a recipe start at ")
                .append(RecipeValuer.FOUND_VALUE).append(" coins.\n");
        out.append("#\n#  This file is overwritten: to change a value, copy its line into\n");
        out.append("#  item-values.yml under \"addons:\" and /mvgam reload.\n");
        out.append("# ---------------------------------------------------------------------------\n");
        for (Map.Entry<String, Map<String, RecipeValuer.Valued>> addon : byAddon.entrySet()) {
            out.append('\n').append(quote(addon.getKey())).append(":\n");
            for (RecipeValuer.Valued valued : addon.getValue().values()) {
                out.append("  ").append(valued.id()).append(": ")
                        .append(format(valued.value())).append("    # ")
                        .append(valued.name().replace('\n', ' ')).append(", ").append(valued.reason()).append('\n');
            }
        }
        try {
            Files.writeString(file.toPath(), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            Bukkit.getLogger().warning("Could not write " + file.getName() + ": " + error);
        }
    }

    private static String quote(String name) {
        return "'" + name.replace("'", "''") + "'";
    }

    private static String format(double value) {
        if (value >= 1000) {
            return String.valueOf(Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }

    private static String strip(String name) {
        return name.replaceAll("(?i)[&§][0-9A-FK-ORX]", "").trim();
    }
}
