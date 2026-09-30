package com.chagui68.multiversegambling.world;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        // 2 * (2 * SPACING + ARENA_RADIUS + MARGIN) = 480, so 500 blocks are enough.
        assertTrue(layout.extent() * 2 <= 500,
                "extent " + layout.extent() + " does not fit in 500 blocks");
        assertEquals(240, layout.extent());
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
    void everyArenaIsReachableByRoad() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        List<CasinoLayout.Road> roads = layout.roads();
        long onAnAxis = layout.arenas().stream()
                .filter(arena -> arena.centerX() == 0 || arena.centerZ() == 0)
                .count();
        assertEquals(2L * layout.arenas().size() - onAnAxis, roads.size(),
                "arenas on an axis need a single leg, the rest need two");
        for (CasinoLayout.Road road : roads) {
            assertTrue(road.alongX() || road.alongZ(), "roads are straight lines");
        }
        List<String> reached = new ArrayList<>();
        for (CasinoLayout.Road road : roads) {
            if (!road.alongX()) {
                reached.add(road.toX() + ":" + road.toZ());
            } else if (road.toZ() == 0 && road.fromX() == 0) {
                reached.add(road.toX() + ":0");
            }
        }
        for (CasinoLayout.Arena arena : layout.arenas()) {
            if (arena.centerX() == 0 || arena.centerZ() == 0) {
                assertTrue(reached.contains(arena.centerX() + ":" + arena.centerZ()),
                        arena.gameId() + " has no road");
            }
        }
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
    void theRoadsStartAtThePlaza() {
        CasinoLayout layout = CasinoLayout.of(500, GAMES);
        assertAll(() -> layout.roads().forEach(road -> assertTrue(
                (road.fromX() == 0 && road.fromZ() == 0) || road.alongX() || road.alongZ(),
                "road does not start at the plaza")));
    }
}
