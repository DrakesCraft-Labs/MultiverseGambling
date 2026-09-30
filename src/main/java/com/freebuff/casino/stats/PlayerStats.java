package com.freebuff.casino.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/** Estadisticas de un jugador. Se serializan a JSON tal cual. */
public final class PlayerStats {

    public long games;
    public long wins;
    public double wagered;
    public double returned;
    public double biggestWin;
    public double biggestLoss;
    public Map<String, Integer> plays = new LinkedHashMap<>();

    /** Ganancia neta: lo que ha devuelto el casino menos lo apostado. */
    public double profit() {
        return returned - wagered;
    }

    public double winRate() {
        return games == 0 ? 0 : (double) wins / games;
    }

    /** Retorno real del jugador. 1.0 significa que recupera todo lo apostado. */
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
