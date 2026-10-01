package com.chagui68.multiversegambling.game;

/**
 * One possible result of a game, as the item bet menu lists it: what it is called, what
 * it multiplies the stake by and how likely it is.
 *
 * @param label      text of the result, already in the language of the reader
 * @param multiplier what the stake is multiplied by; 0 when it is lost
 * @param chance     probability from 0 to 1, or a negative value when it depends on the
 *                   choices of the player
 */
public record ItemOutcome(String label, double multiplier, double chance) {
}
