package com.chagui68.multiversegambling.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /mvgam play} answers to the exact game id and nothing else: a display name, a
 * translated name or a partial id must never open a game.
 */
class GameRegistryLookupTest {

    private GameRegistry registry;

    @BeforeEach
    void setUp() {
        // The plugin is only needed by register() for group games; these stubs are solo
        // ones, so the lookup rule can be exercised without a running server.
        registry = new GameRegistry(null);
        registry.register(new StubGame("lucky-wheel", "Lucky Wheel"));
        registry.register(new StubGame("roulette", "Classic Roulette"));
    }

    @Test
    void theExactIdResolvesTheGame() {
        assertEquals("lucky-wheel", registry.byId("lucky-wheel").orElseThrow().id());
        assertEquals("roulette", registry.byId("roulette").orElseThrow().id());
    }

    @Test
    void theIdIsCaseInsensitive() {
        assertEquals("roulette", registry.byId("ROULETTE").orElseThrow().id());
        assertEquals("lucky-wheel", registry.byId("Lucky-Wheel").orElseThrow().id());
    }

    @Test
    void displayNamesAndPartialsAreNotAccepted() {
        assertTrue(registry.byId("Lucky Wheel").isEmpty(), "spaces are not part of an id");
        assertTrue(registry.byId("lucky").isEmpty(), "a partial id must not match");
        assertTrue(registry.byId("wheel").isEmpty(), "a partial id must not match");
        assertTrue(registry.byId("Classic Roulette").isEmpty(), "a display name must not match");
    }

    @Test
    void translatedNamesAndUnknownIdsReturnEmpty() {
        assertTrue(registry.byId("ruleta").isEmpty(), "the translated name must not match");
        assertTrue(registry.byId("no-such-game").isEmpty());
        assertTrue(registry.byId(null).isEmpty());
    }

    @Test
    void thereIsNoFuzzyLookupLeft() {
        List<String> lookups = Arrays.stream(GameRegistry.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertFalse(lookups.contains("search"),
                "play must resolve the exact id: no fuzzy search() helper");
    }

    private record StubGame(String id, String name) implements Game {

        @Override
        public GameMeta meta() {
            throw new UnsupportedOperationException("not needed for registry lookups");
        }

        @Override
        public String displayName(CommandSender viewer) {
            return name;
        }

        @Override
        public List<String> displayDescription(CommandSender viewer) {
            return List.of();
        }

        @Override
        public double minBet() {
            return 1;
        }

        @Override
        public double maxBet() {
            return 100;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public void open(Player player) {
        }
    }
}
