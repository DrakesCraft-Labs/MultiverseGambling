package com.chagui68.multiversegambling.economy;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Bridge to whatever economy the server already has. Vault is never bundled. */
public final class VaultEconomy implements EconomyProvider {

    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    /** @return the bridge, or {@code null} when there is no Vault or no economy registered. */
    public static EconomyProvider tryHook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        try {
            RegisteredServiceProvider<Economy> registration =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration == null || registration.getProvider() == null) {
                return null;
            }
            return new VaultEconomy(registration.getProvider());
        } catch (Throwable error) {
            Bukkit.getLogger().warning("[MultiverseGambling] Vault is present but the economy did not respond: " + error.getMessage());
            return null;
        }
    }

    @Override
    public String name() {
        return economy.getName() + " (Vault)";
    }

    private OfflinePlayer offline(UUID playerId) {
        return Bukkit.getOfflinePlayer(playerId);
    }

    @Override
    public double balance(UUID playerId) {
        return economy.getBalance(offline(playerId));
    }

    @Override
    public boolean set(UUID playerId, double amount) {
        double current = balance(playerId);
        if (amount > current) {
            return economy.depositPlayer(offline(playerId), amount - current).transactionSuccess();
        }
        if (amount < current) {
            return economy.withdrawPlayer(offline(playerId), current - amount).transactionSuccess();
        }
        return true;
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        EconomyResponse response = economy.withdrawPlayer(offline(playerId), amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean deposit(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        EconomyResponse response = economy.depositPlayer(offline(playerId), amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean has(UUID playerId, double amount) {
        return economy.has(offline(playerId), amount);
    }

    @Override
    public boolean createAccountIfMissing(UUID playerId, double startingBalance) {
        if (economy.hasAccount(offline(playerId))) {
            return false;
        }
        return economy.createPlayerAccount(offline(playerId));
    }
}
