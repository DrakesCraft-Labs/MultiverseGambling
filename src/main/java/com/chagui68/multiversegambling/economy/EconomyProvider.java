package com.chagui68.multiversegambling.economy;

import java.util.UUID;

/**
 * Money source of the casino. It exists so the plugin behaves the same with the
 * economy of the server (Vault) or with its own wallet when there is none.
 */
public interface EconomyProvider {

    /** Human readable name of the active provider, for /casino info. */
    String name();

    double balance(UUID playerId);

    /** @return true when the change was applied. */
    boolean set(UUID playerId, double amount);

    /** @return true when there was enough balance and it was withdrawn. */
    boolean withdraw(UUID playerId, double amount);

    boolean deposit(UUID playerId, double amount);

    default boolean has(UUID playerId, double amount) {
        return balance(playerId) >= amount;
    }

    /** Grants a welcome balance when the player never had an account. */
    default boolean createAccountIfMissing(UUID playerId, double startingBalance) {
        return false;
    }
}
