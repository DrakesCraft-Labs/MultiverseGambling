package com.freebuff.casino.economy;

import java.util.UUID;

/**
 * Fuente de dinero del casino. Existe para que el plugin funcione igual con la
 * economia del servidor (Vault) o con su propio monedero si no hay ninguna.
 */
public interface EconomyProvider {

    /** Nombre legible del proveedor activo, para /casino info. */
    String name();

    double balance(UUID playerId);

    /** @return true si el cambio se aplico. */
    boolean set(UUID playerId, double amount);

    /** @return true si habia saldo suficiente y se desconto. */
    boolean withdraw(UUID playerId, double amount);

    boolean deposit(UUID playerId, double amount);

    default boolean has(UUID playerId, double amount) {
        return balance(playerId) >= amount;
    }

    /** Da un saldo de bienvenida si el jugador nunca ha tenido cuenta. */
    default boolean createAccountIfMissing(UUID playerId, double startingBalance) {
        return false;
    }
}
