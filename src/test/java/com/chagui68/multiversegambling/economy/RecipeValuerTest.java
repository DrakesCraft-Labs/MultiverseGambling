package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Addon items are valued at runtime from the recipes Slimefun registered; the rule has
 * to follow the recipes, respect the values the server owner wrote, survive loops and
 * leave structures out.
 */
class RecipeValuerTest {

    private static RecipeValuer.Ingredient sf(String id, int count) {
        return new RecipeValuer.Ingredient(true, id, count);
    }

    private static RecipeValuer.Ingredient mc(String material, int count) {
        return new RecipeValuer.Ingredient(false, material, count);
    }

    private static double vanilla(String material) {
        return switch (material) {
            case "IRON_INGOT" -> 5;
            case "DIAMOND" -> 100;
            default -> 0;
        };
    }

    @Test
    void anItemIsWorthItsRecipe() {
        List<RecipeValuer.Node> nodes = List.of(
                new RecipeValuer.Node("PLATE", "Plate", "Addon", "enhanced_crafting_table",
                        List.of(mc("IRON_INGOT", 4)), 1, 0, "IRON_INGOT"),
                new RecipeValuer.Node("CORE", "Core", "Addon", "magic_workbench",
                        List.of(sf("PLATE", 2), mc("DIAMOND", 1)), 2, 10, "NETHER_STAR"));
        Map<String, RecipeValuer.Valued> values = new RecipeValuer(nodes, RecipeValuerTest::vanilla, id -> 0).valueAll();
        assertEquals(21.0, values.get("PLATE").value(), 1e-9, "4 iron x 1.05");
        // (2 x 21 + 100) x 1.15 + 10 levels x 0.5, made two at a time.
        assertEquals(((2 * 21 + 100) * 1.15 + 5) / 2, values.get("CORE").value(), 0.01);
    }

    @Test
    void listedValuesWinAndAreNotRepeated() {
        List<RecipeValuer.Node> nodes = List.of(
                new RecipeValuer.Node("STEEL_INGOT", "Steel", "Slimefun", "smeltery", List.of(mc("IRON_INGOT", 1)), 1, 0,
                        "IRON_INGOT"),
                new RecipeValuer.Node("BEAM", "Beam", "Addon", "enhanced_crafting_table", List.of(sf("STEEL_INGOT", 3)),
                        1, 0, "IRON_BLOCK"));
        Map<String, RecipeValuer.Valued> values = new RecipeValuer(nodes, RecipeValuerTest::vanilla,
                id -> "STEEL_INGOT".equals(id) ? 30 : 0).valueAll();
        assertFalse(values.containsKey("STEEL_INGOT"), "already in item-values.yml");
        assertEquals(94.5, values.get("BEAM").value(), 1e-9, "3 x 30 from the file, x 1.05");
    }

    @Test
    void foundItemsLoopsAndStructuresAreHandled() {
        List<RecipeValuer.Node> nodes = List.of(
                new RecipeValuer.Node("ESSENCE", "Essence", "Addon", "mob_drop", List.of(mc("ZOMBIE_HEAD", 1)), 1, 4,
                        "GHAST_TEAR"),
                new RecipeValuer.Node("A", "A", "Addon", "enhanced_crafting_table", List.of(sf("B", 1)), 1, 0, "STONE"),
                new RecipeValuer.Node("B", "B", "Addon", "enhanced_crafting_table", List.of(sf("A", 1)), 1, 0, "STONE"),
                new RecipeValuer.Node("FORGE", "Forge", "Addon", "multiblock", List.of(mc("DIAMOND", 9)), 1, 0, "ANVIL"));
        Map<String, RecipeValuer.Valued> values = new RecipeValuer(nodes, RecipeValuerTest::vanilla, id -> 0).valueAll();
        assertEquals(RecipeValuer.FOUND_VALUE + 2, values.get("ESSENCE").value(), 1e-9);
        assertTrue(values.get("A").value() > 0 && values.get("B").value() > 0, "a loop still ends with values");
        assertFalse(values.containsKey("FORGE"), "a multiblock is built, never carried");
    }
}
