package com.chagui68.multiversegambling.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of the casino world for {@code /mvgam world info}.
 *
 * <p>The record holds facts only: what the configuration asks for, what the plugin managed
 * to prepare and what the world looks like right now. Deciding which hints an
 * administrator needs is kept here as well, so the wording stays in the language files
 * while the logic "is something missing?" is unit tested.</p>
 */
public record WorldReport(
        String name,
        Status status,
        Source source,
        boolean flat,
        int size,
        int groundY,
        int arenasBuilt,
        int arenasPlanned,
        int games,
        int boards,
        List<String> boardsTooBig) {

    /**
     * Where the world stands right now.
     */
    public enum Status {
        /** {@code world.enabled} is false, so the plugin never touched it. */
        DISABLED,
        /** {@code world.name} points at the server's main world and was refused. */
        MAIN_WORLD,
        /** Nothing is loaded under that name: never created, or creation failed. */
        MISSING,
        /** The world is loaded, but the plugin could not measure and prepare it. */
        NOT_PREPARED,
        /** Measured, but the plaza and the arenas are not built yet. */
        NOT_BUILT,
        /** The build is running in the background right now. */
        BUILDING,
        /** Built, but fewer arenas are painted than the layout plans. */
        PARTIAL,
        /** Everything is in place. */
        READY
    }

    /**
     * Who chose the terrain.
     */
    public enum Source {
        /** Created by the plugin on this start, with its own flat settings. */
        CREATED_HERE,
        /** Already on disk and loaded by the server before the plugin looked at it. */
        LOADED,
        /** Nothing is loaded, so there is no terrain to report. */
        ABSENT
    }

    public WorldReport {
        boardsTooBig = List.copyOf(boardsTooBig);
    }

    /**
     * True when the plugin measured the world, which is what makes the size, the ground
     * level and the arena counts meaningful.
     */
    public boolean measured() {
        return size > 0;
    }

    /**
     * Language keys of the hints to show under the report, in the order they are read. A
     * report that finds nothing missing returns an empty list.
     */
    public List<String> hintKeys() {
        List<String> hints = new ArrayList<>();
        switch (status) {
            case DISABLED -> hints.add("world.info.hint-disabled");
            case MAIN_WORLD -> hints.add("world.info.hint-main-world");
            case MISSING -> hints.add("world.info.hint-missing");
            case NOT_PREPARED -> hints.add("world.info.hint-not-prepared");
            case NOT_BUILT -> hints.add("world.info.hint-unbuilt");
            case BUILDING -> hints.add("world.info.hint-building");
            case PARTIAL -> hints.add("world.info.hint-partial");
            case READY -> {
            }
        }
        if (!boardsTooBig.isEmpty()) {
            hints.add("world.info.hint-board-too-big");
        }
        // The layout grows the world on its own, so a plan that holds fewer arenas than
        // registered games means the world could not grow: world.size has to go up.
        if (arenasPlanned > 0 && arenasPlanned < games) {
            hints.add("world.info.hint-size");
        }
        return hints;
    }
}
