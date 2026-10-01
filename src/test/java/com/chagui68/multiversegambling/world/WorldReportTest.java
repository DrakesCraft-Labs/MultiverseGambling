package com.chagui68.multiversegambling.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The hints of {@code /mvgam world info} are chosen from the report alone, so they are
 * pinned here: a healthy world must not nag, and every broken state must say what to do
 * about it.
 */
class WorldReportTest {

    private static WorldReport report(WorldReport.Status status, int built, int planned, int games,
                                      List<String> boardsTooBig) {
        boolean measured = status == WorldReport.Status.NOT_BUILT
                || status == WorldReport.Status.BUILDING
                || status == WorldReport.Status.PARTIAL
                || status == WorldReport.Status.READY;
        return new WorldReport("mvgam_casino", status, WorldReport.Source.LOADED, true,
                measured ? 500 : 0, measured ? 5 : -1, built, planned, games, 4, boardsTooBig);
    }

    @Test
    void aHealthyWorldHasNoHints() {
        WorldReport report = report(WorldReport.Status.READY, 21, 21, 21, List.of());
        assertTrue(report.measured(), "a ready world is a measured one");
        assertTrue(report.hintKeys().isEmpty(),
                "a healthy world must not ask for anything: " + report.hintKeys());
    }

    @Test
    void everyBrokenStateExplainsItself() {
        assertEquals(List.of("world.info.hint-disabled"),
                report(WorldReport.Status.DISABLED, 0, 0, 21, List.of()).hintKeys());
        assertEquals(List.of("world.info.hint-main-world"),
                report(WorldReport.Status.MAIN_WORLD, 0, 0, 21, List.of()).hintKeys());
        assertEquals(List.of("world.info.hint-missing"),
                report(WorldReport.Status.MISSING, 0, 0, 21, List.of()).hintKeys());
        assertEquals(List.of("world.info.hint-not-prepared"),
                report(WorldReport.Status.NOT_PREPARED, 0, 0, 21, List.of()).hintKeys());
        assertEquals(List.of("world.info.hint-unbuilt"),
                report(WorldReport.Status.NOT_BUILT, 0, 21, 21, List.of()).hintKeys());
        assertEquals(List.of("world.info.hint-partial"),
                report(WorldReport.Status.PARTIAL, 18, 21, 21, List.of()).hintKeys());
        assertEquals(List.of("world.info.hint-building"),
                report(WorldReport.Status.BUILDING, 4, 21, 21, List.of()).hintKeys());
    }

    @Test
    void aWorldThatCannotHoldEveryGameAsksForABiggerOne() {
        // The layout grows the world by itself, so a plan smaller than the catalogue means
        // the world could not grow: the reader is told which arenas are missing and that
        // world.size has to go up.
        assertEquals(List.of("world.info.hint-partial", "world.info.hint-size"),
                report(WorldReport.Status.PARTIAL, 18, 18, 21, List.of()).hintKeys());
    }

    @Test
    void aBoardThatDoesNotFitIsNamedEvenWhenTheWorldIsFine() {
        assertEquals(List.of("world.info.hint-board-too-big"),
                report(WorldReport.Status.READY, 21, 21, 21, List.of("towers")).hintKeys());
    }

    @Test
    void aDisabledWorldStillReportsBoardProblems() {
        // Nothing about the world was measured, but a board that cannot fit an arena is a
        // configuration problem worth knowing before turning the world on.
        assertEquals(List.of("world.info.hint-disabled", "world.info.hint-board-too-big"),
                report(WorldReport.Status.DISABLED, 0, 0, 21, List.of("towers", "mines")).hintKeys());
    }

    @Test
    void theListOfBoardsCannotBeChangedFromOutside() {
        List<String> boardsTooBig = new ArrayList<>(List.of("towers"));
        WorldReport report = report(WorldReport.Status.READY, 21, 21, 21, boardsTooBig);
        boardsTooBig.add("mines");
        assertEquals(List.of("towers"), report.boardsTooBig());
    }
}
