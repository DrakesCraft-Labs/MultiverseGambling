package com.chagui68.multiversegambling.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Statistics of one player. Serialised to JSON as they are.
 */
public final class PlayerStats {

    public long games;
    public long wins;
    public double wagered;
    public double returned;
    public double biggestWin;
    public double biggestLoss;
    public Map<String, Integer> plays = new LinkedHashMap<>();

    /**
     * Net profit: what the casino returned minus what was staked.
     */
    public double profit() {
        return returned - wagered;
    }

    public double winRate() {
        return games == 0 ? 0 : (double) wins / games;
    }

    /**
     * Real return of the player. 1.0 means they got back everything they staked.
     */
    public double rtp() {
        return wagered <= 0 ? 0 : returned / wagered;
    }

    public String favouriteGame() {
        return plays.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
    }

    public double averageBet() {
        return games == 0 ? 0 : wagered / games;
    }
}
