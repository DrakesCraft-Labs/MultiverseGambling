package com.chagui68.multiversegambling.economy;

import java.util.UUID;

/**
 * Una apuesta ya retirada del monedero del jugador.
 *
 * <p>Existe para hacer imposible el fallo clasico de un casino: pagar dos veces
 * la misma jugada. Todo el dinero de una partida pasa por aqui, y liquidar dos
 * veces no hace nada la segunda vez.</p>
 */
public final class Wager {

    private final EconomyManager economy;
    private final UUID playerId;
    private final double amount;
    private boolean settled;

    Wager(EconomyManager economy, UUID playerId, double amount) {
        this.economy = economy;
        this.playerId = playerId;
        this.amount = amount;
    }

    public UUID playerId() {
        return playerId;
    }

    public double amount() {
        return amount;
    }

    public boolean isSettled() {
        return settled;
    }

    /** Paga {@code amount * multiplier}. Devuelve el dinero retirado al ganar. */
    public boolean pay(double multiplier) {
        if (multiplier <= 0) {
            lose();
            return false;
        }
        return payAbsolute(amount * multiplier);
    }

    /** Devuelve el capital (empate o anulacion de la ronda). */
    public boolean refund() {
        return payAbsolute(amount);
    }

    /** Paga una cantidad concreta, ignorando la apuesta original. */
    public boolean payAbsolute(double payout) {
        if (settled) {
            return false;
        }
        settled = true;
        return economy.provider().deposit(playerId, Math.max(0, payout));
    }

    /** Marca la apuesta como perdida: el dinero ya salio del monedero. */
    public void lose() {
        settled = true;
    }

    /** Ganancia neta de esta apuesta, o 0 si aun no se ha liquidado. */
    public double profit() {
        return settled ? -amount : 0;
    }
}
