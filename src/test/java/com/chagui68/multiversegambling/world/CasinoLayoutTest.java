package com.chagui68.multiversegambling.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class CasinoLayoutTest {

    private static final List<String> GAMES = List.of(
            "roulette", "slots", "crash", "mines", "towers", "blackjack", "high-low",
            "dice", "plinko", "scratch", "lucky-wheel", "coin-flip", "color-roulette",
            "jackpot", "hot-bomb", "bomb-board", "russian-roulette", "race", "duel",
            "raffle", "dice-poker");

    @Test
    void everyRegisteredGameGetsAnArena() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        assertEquals(GAMES.size(), layout.arenas().size());
        for (String game : GAMES) {
            assertNotNull(layout.arena(game), "no arena for " + game);
        }
        assertNull(layout.arena("not-a-game"));
    }

    @Test
    void theDefaultWorldHoldsTheWholeGrid() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        assertEquals(5, layout.columns());
        // 2 * (2 * SPACING + ARENA_RADIUS + MARGIN) = 408, so 500 blocks are enough and
        // leave a green belt round the edge.
        assertTrue(layout.extent() * 2 <= 500,
                "extent " + layout.extent() + " does not fit in 500 blocks");
        assertEquals(204, layout.extent());
    }

    @Test
    void arenasStayInsideTheWorldAndNeverOverlap() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        int limit = layout.size() / 2 - CasinoLayout.MARGIN - CasinoLayout.ARENA_RADIUS;
        List<CasinoLayout.Arena> arenas = layout.arenas();
        for (CasinoLayout.Arena arena : arenas) {
            assertTrue(Math.abs(arena.centerX()) <= limit, arena.gameId() + " leaves the world on X");
            assertTrue(Math.abs(arena.centerZ()) <= limit, arena.gameId() + " leaves the world on Z");
        }
        for (int i = 0; i < arenas.size(); i++) {
            for (int j = i + 1; j < arenas.size(); j++) {
                CasinoLayout.Arena a = arenas.get(i);
                CasinoLayout.Arena b = arenas.get(j);
                boolean apart = Math.abs(a.centerX() - b.centerX()) > 2 * CasinoLayout.ARENA_RADIUS
                        || Math.abs(a.centerZ() - b.centerZ()) > 2 * CasinoLayout.ARENA_RADIUS;
                assertTrue(apart, a.gameId() + " overlaps " + b.gameId());
            }
        }
    }

    @Test
    void theCentreIsLeftFreeForThePlaza() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        for (CasinoLayout.Arena arena : layout.arenas()) {
            assertTrue(arena.centerX() != 0 || arena.centerZ() != 0,
                    arena.gameId() + " sits on top of the plaza");
        }
        // Nothing covers spawn, not even a single block of an arena platform.
        for (CasinoLayout.Arena arena : layout.arenas()) {
            assertTrue(!arena.contains(0, 0), arena.gameId() + " covers spawn");
        }
    }

    @Test
    void theFirstGamesSitClosestToThePlaza() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        int previous = -1;
        for (CasinoLayout.Arena arena : layout.arenas()) {
            int ring = Math.max(Math.abs(arena.centerX()), Math.abs(arena.centerZ()))
                    / CasinoLayout.SPACING;
            assertTrue(ring >= previous, arena.gameId() + " is placed before a closer arena");
            previous = ring;
        }
        assertEquals(2, previous, "21 games reach the ring around the 3x3 core");
    }

    @Test
    void theEntranceAlwaysFacesThePlaza() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        for (CasinoLayout.Arena arena : layout.arenas()) {
            CasinoLayout.Edge edge = CasinoLayout.entrance(arena);
            boolean towardsCentre = switch (edge) {
                case NORTH -> arena.centerZ() > 0;
                case SOUTH -> arena.centerZ() < 0;
                case EAST -> arena.centerX() < 0;
                case WEST -> arena.centerX() > 0;
            };
            assertTrue(towardsCentre, arena.gameId() + " opens away from the plaza");
        }
    }

    @Test
    void everyArenaSitsOnTwoBoulevards() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        List<CasinoLayout.Road> roads = layout.roads();
        for (CasinoLayout.Road road : roads) {
            assertTrue(road.alongX() || road.alongZ(), "roads are straight lines");
        }
        for (CasinoLayout.Arena arena : layout.arenas()) {
            long through = roads.stream().filter(road -> road.passes(arena.centerX(), arena.centerZ())).count();
            assertTrue(through >= 2, arena.gameId() + " is not on a crossing of two boulevards");
        }
    }

    @Test
    void everyBoulevardMeetsTheOnesLeavingThePlaza() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        List<CasinoLayout.Road> roads = layout.roads();
        List<CasinoLayout.Road> fromPlaza = new ArrayList<>();
        for (CasinoLayout.Road road : roads) {
            if (road.passes(0, 0)) {
                fromPlaza.add(road);
            }
        }
        assertEquals(2, fromPlaza.size(), "one boulevard along each axis crosses the plaza");
        for (CasinoLayout.Road road : roads) {
            boolean meets = false;
            for (CasinoLayout.Road axis : fromPlaza) {
                int x = road.alongX() ? axis.fromX() : road.fromX();
                int z = road.alongX() ? road.fromZ() : axis.fromZ();
                if (road == axis || (road.passes(x, z) && axis.passes(x, z))) {
                    meets = true;
                }
            }
            assertTrue(meets, "a boulevard is cut off from the plaza: " + road);
        }
    }

    @Test
    void theRingRoadRunsOutsideEveryPavilionAndInsideTheWorld() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        int ring = layout.ringRoad();
        for (CasinoLayout.Arena arena : layout.arenas()) {
            assertTrue(Math.abs(arena.centerX()) + CasinoLayout.ARENA_RADIUS + CasinoLayout.ROAD_WIDTH / 2 < ring,
                    arena.gameId() + " touches the ring road");
            assertTrue(Math.abs(arena.centerZ()) + CasinoLayout.ARENA_RADIUS + CasinoLayout.ROAD_WIDTH / 2 < ring,
                    arena.gameId() + " touches the ring road");
        }
        assertTrue(ring + CasinoLayout.ROAD_WIDTH / 2 <= layout.extent(), "the ring road leaves the casino");
    }

    @Test
    void theCellsNobodyUsesBecomeGardens() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        assertEquals(CasinoLayout.capacity(layout.columns()) - GAMES.size(), layout.gardens().size());
        for (CasinoLayout.Garden garden : layout.gardens()) {
            for (CasinoLayout.Arena arena : layout.arenas()) {
                assertTrue(garden.centerX() != arena.centerX() || garden.centerZ() != arena.centerZ(),
                        "a garden was laid over " + arena.gameId());
            }
            assertTrue(garden.centerX() != 0 || garden.centerZ() != 0, "a garden was laid over the plaza");
        }
    }

    @Test
    void thePlazaLeavesRoomBeforeTheFirstPavilions() {
        // The inner edge of the closest pavilions must stay clear of the plaza hedge.
        assertTrue(CasinoLayout.SPACING - CasinoLayout.ARENA_RADIUS > CasinoLayout.PLAZA_RADIUS + 4);
        assertTrue(CasinoLayout.STAGE_RADIUS < CasinoLayout.ARENA_RADIUS - 4,
                "the stage must leave room for the walkway and the bleachers");
    }

    @Test
    void aSingleGameStillGetsARingArena() {
        CasinoLayout layout = CasinoLayout.of(500, List.of("roulette"));
        assertEquals(1, layout.arenas().size());
        CasinoLayout.Arena arena = layout.arena("roulette");
        assertEquals(CasinoLayout.SPACING, Math.abs(arena.centerX()) + Math.abs(arena.centerZ()));
    }

    @Test
    void nineGamesDoNotFallOffTheGrid() {
        // An odd grid gives its centre cell to the plaza, so nine games need a wider
        // one: this used to fail with "a 3x3 grid holds 8 arenas".
        List<String> nine = GAMES.subList(0, 9);
        CasinoLayout layout = CasinoLayout.of(500, nine);
        assertEquals(9, layout.arenas().size());
        assertTrue(CasinoLayout.capacity(3) == 8, "the centre cell is not usable");
        assertTrue(CasinoLayout.capacity(layout.columns()) >= nine.size(),
                "the chosen grid must hold every arena");
    }

    @Test
    void aWorldThatIsTooSmallIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CasinoLayout.of(200, GAMES));
        assertTrue(error.getMessage().contains("too small"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> CasinoLayout.of(0, GAMES));
        assertThrows(IllegalArgumentException.class, () -> CasinoLayout.of(500, List.of()));
    }

    @Test
    void theFlatWorldSettingsAreJsonWithABiome() {
        // Servers since 1.18.2 read these settings as JSON, so the old
        // "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;1;minecraft:plains"
        // string was rejected with a MalformedJsonException that aborted world
        // creation and stopped the whole plugin from enabling. JSON is also valid
        // YAML, so SnakeYAML verifies the shape without pulling in another parser.
        Object settings = new Yaml().load(CasinoWorldManager.FLAT_SETTINGS);
        assertInstanceOf(Map.class, settings, "world settings are not a JSON object");
        Map<?, ?> json = (Map<?, ?>) settings;
        Object layers = json.get("layers");
        assertInstanceOf(List.class, layers, "no layer list in the flat world settings");
        for (Object layer : (List<?>) layers) {
            assertInstanceOf(Map.class, layer, "a layer is not a JSON object");
            Map<?, ?> entry = (Map<?, ?>) layer;
            String block = String.valueOf(entry.get("block"));
            assertTrue(block.startsWith("minecraft:"), block + " is not a namespaced block");
            assertInstanceOf(Number.class, entry.get("height"), "no height for " + block);
            assertTrue(((Number) entry.get("height")).intValue() >= 1, "empty layer " + block);
        }
        assertEquals("minecraft:plains", json.get("biome"),
                "a modern flat world needs a biome, or the server cannot pick a surface");
    }
}
