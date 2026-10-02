package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * The value file shipped with the plugin must load, keep the diamond as its anchor and
 * rank items the way their recipes say: a crafted item is never worth less than what
 * it is made of.
 */
class ItemValuesTest {

    private static ItemValues.Table table() throws Exception {
        var stream = ItemValuesTest.class.getClassLoader().getResourceAsStream("item-values.yml");
        assertNotNull(stream, "item-values.yml is missing from the resources");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return ItemValues.Table.of(YamlConfiguration.loadConfiguration(reader));
        }
    }

    private static long cents(ItemValues.Table table, ItemValues.Source source, String id) {
        return table.cents(new ItemValues.Identity(source, id));
    }

    @Test
    void theFileLoadsEverySection() throws Exception {
        ItemValues.Table table = table();
        assertEquals(1.0, table.scale());
        assertTrue(table.slimefun().size() >= 500, "Slimefun items: " + table.slimefun().size());
        assertTrue(table.creatures().size() >= 55, "MultiverseCreatures items: " + table.creatures().size());
        assertTrue(table.vanilla().size() >= 150, "vanilla items: " + table.vanilla().size());
    }

    @Test
    void theDiamondIsTheAnchor() throws Exception {
        ItemValues.Table table = table();
        assertEquals(10000, cents(table, ItemValues.Source.VANILLA, "DIAMOND"));
        assertEquals(10000, cents(table, ItemValues.Source.VANILLA, "diamond"), "ids ignore case");
        assertEquals(0, cents(table, ItemValues.Source.VANILLA, "NOT_AN_ITEM"));
    }

    @Test
    void craftedItemsAreWorthMoreThanTheirParts() throws Exception {
        ItemValues.Table table = table();
        ItemValues.Source sf = ItemValues.Source.SLIMEFUN;
        assertTrue(cents(table, sf, "STEEL_INGOT") > cents(table, sf, "IRON_DUST"));
        assertTrue(cents(table, sf, "REINFORCED_ALLOY_INGOT") > cents(table, sf, "STEEL_INGOT"));
        assertTrue(cents(table, sf, "CARBONADO") > cents(table, sf, "SYNTHETIC_DIAMOND"));
        assertTrue(cents(table, sf, "ADVANCED_CIRCUIT_BOARD") > cents(table, sf, "BASIC_CIRCUIT_BOARD"));
        ItemValues.Source msc = ItemValues.Source.MULTIVERSE_CREATURES;
        assertTrue(cents(table, msc, "msc_chaos_core") > cents(table, msc, "msc_chaos_fragment"));
        assertTrue(cents(table, msc, "msc_chaos_fragment") > cents(table, msc, "msc_chaos_orb"));
        assertTrue(cents(table, msc, "msc_multiversal_core") > cents(table, msc, "msc_star_core"));
        assertTrue(cents(table, msc, "msc_excalibur_sword") > cents(table, msc, "msc_star_core") * 16);
    }

    @Test
    void stateMarkersAreNotItems() throws Exception {
        ItemValues.Table table = table();
        assertEquals(0, cents(table, ItemValues.Source.MULTIVERSE_CREATURES, "msc_wheel_charges"));
        assertEquals(0, cents(table, ItemValues.Source.MULTIVERSE_CREATURES, "msc_chaos_reforged"));
        // Multiblock structures are built from blocks, never carried as an item.
        assertEquals(0, cents(table, ItemValues.Source.SLIMEFUN, "SMELTERY"));
    }
}
