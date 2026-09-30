package com.chagui68.multiversegambling.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The command surface is part of the branding: one command, {@code /mvgam}, with a single
 * spelling per subcommand and no aliases anywhere.
 *
 * <p>An alias added by accident is hard to notice in game and impossible to untangle from
 * the documentation, so the shipped {@code plugin.yml} is checked here as well.</p>
 */
class CommandSurfaceTest {

    private static final List<String> SUBCOMMANDS = List.of(
            "menu", "games", "play", "action", "balance", "stats", "top", "verify",
            "world", "language", "info", "reload", "give", "take", "set", "cancel");

    private static final Map<String, Object> PLUGIN = load();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() {
        try (InputStream stream = CommandSurfaceTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream, "plugin.yml must be on the classpath");
            return (Map<String, Object>) new Yaml().load(stream);
        } catch (Exception error) {
            throw new IllegalStateException("Could not read plugin.yml", error);
        }
    }

    @Test
    void thePluginRegistersExactlyOneCommand() {
        Map<?, ?> commands = (Map<?, ?>) PLUGIN.get("commands");
        assertNotNull(commands, "plugin.yml must declare its commands");
        assertEquals(List.of("mvgam"), List.copyOf(commands.keySet()),
                "the only command is /mvgam");
    }

    @Test
    void theCommandDeclaresNoAlias() {
        Map<?, ?> commands = (Map<?, ?>) PLUGIN.get("commands");
        Map<?, ?> mvgam = (Map<?, ?>) commands.get("mvgam");
        assertNotNull(mvgam, "plugin.yml must describe /mvgam");
        assertFalse(mvgam.containsKey("aliases"),
                "/mvgam must not have aliases: the command is /mvgam and nothing else");
    }

    @Test
    void theDocumentedSubcommandsHaveNoAlternateSpellings() {
        // A subcommand appears once in the tab completion list, and the list has to be the
        // command surface the dispatcher answers to, in the order the documentation uses.
        assertEquals(SUBCOMMANDS.size(), SUBCOMMANDS.stream().distinct().count(),
                "a subcommand is listed twice");
        assertFalse(SUBCOMMANDS.contains("lang"),
                "lang is not a subcommand: the language picker is called language");
        assertFalse(SUBCOMMANDS.contains("mvgamb"), "the command is mvgam, not mvgamb");
    }

    @Test
    void everyPermissionCarriesTheNamespace() {
        Map<?, ?> permissions = (Map<?, ?>) PLUGIN.get("permissions");
        assertNotNull(permissions, "plugin.yml must declare its permissions");
        for (Object permission : permissions.keySet()) {
            assertTrue(String.valueOf(permission).startsWith("mvgam_"),
                    "permission " + permission + " must start with mvgam_");
        }
    }
}
