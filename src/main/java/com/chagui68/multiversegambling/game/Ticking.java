package com.chagui68.multiversegambling.game;

/**
 * A game that keeps its own state between player actions and needs the plugin clock:
 * the group rounds and the poker table. The registry hooks every such game into the
 * single scheduler of the plugin.
 */
public interface Ticking {

    /**
     * One server tick.
     */
    void tick();
}
