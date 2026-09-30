package com.chagui68.multiversegambling.economy;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.Text;
import java.util.UUID;
import org.bukkit.entity.Player;

/** The single place where casino money comes in and goes out. */
public final class EconomyManager {

    private final MultiverseGamblingPlugin plugin;
    private EconomyProvider provider;
    private double startingBalance = 1000;

    public EconomyManager(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    /** Detects Vault or falls back to the internal wallet. Called again on /casino reload. */
    public void setup() {
        startingBalance = plugin.config().startingBalance();
        String mode = plugin.config().economyProvider();
        EconomyProvider vault = "internal".equalsIgnoreCase(mode) ? null : VaultEconomy.tryHook();
        if (vault != null) {
            provider = vault;
            plugin.getLogger().info("Economia enlazada: " + provider.name());
        } else {
            if (!"internal".equalsIgnoreCase(mode) && !"auto".equalsIgnoreCase(mode)) {
                plugin.getLogger().warning("Unknown economy provider '" + mode + "'; using the internal wallet.");
            }
            provider = new InternalEconomy(plugin);
            plugin.getLogger().info("Using the internal casino wallet (balances.json).");
        }
    }

    public EconomyProvider provider() {
        return provider;
    }

    public boolean usingVault() {
        return provider instanceof VaultEconomy;
    }

    public double startingBalance() {
        return startingBalance;
    }

    public double balance(UUID playerId) {
        return provider.balance(playerId);
    }

    public boolean has(UUID playerId, double amount) {
        return provider.has(playerId, amount);
    }

    public boolean deposit(UUID playerId, double amount) {
        return provider.deposit(playerId, amount);
    }

    public boolean set(UUID playerId, double amount) {
        return provider.set(playerId, amount);
    }

    /** Grants the welcome account the first time a player comes in. */
    public void ensureAccount(Player player) {
        if (provider.createAccountIfMissing(player.getUniqueId(), startingBalance)) {
            plugin.messages().send(player, "economy.account-created", "balance", format(startingBalance));
        }
    }

    /**
     * Takes the bet out of the wallet and wraps it in a {@link Wager}.
     *
     * @return {@code null} when the player cannot afford it.
     */
    public Wager stake(Player player, double amount) {
        if (amount <= 0 || !provider.has(player.getUniqueId(), amount)) {
            return null;
        }
        if (!provider.withdraw(player.getUniqueId(), amount)) {
            return null;
        }
        return new Wager(this, player.getUniqueId(), amount);
    }

    /** Creates an already charged bet (for internal payouts or tests). */
    public Wager wager(UUID playerId, double amount) {
        return new Wager(this, playerId, amount);
    }

    public String format(double amount) {
        String format = plugin.config().currencyFormat();
        String formatted = Text.fill(format,
                "amount", Text.number(amount),
                "currency", amount == 1 ? plugin.config().currencyName() : plugin.config().currencyName());
        return formatted;
    }

    /** Compact format for menu lore. */
    public String shortFormat(double amount) {
        if (Math.abs(amount) >= 1_000_000_000) {
            return String.format("%,.1fMM", amount / 1_000_000_000);
        }
        if (Math.abs(amount) >= 1_000_000) {
            return String.format("%,.1fM", amount / 1_000_000);
        }
        if (Math.abs(amount) >= 10_000) {
            return String.format("%,.1fK", amount / 1_000);
        }
        return Text.number(amount);
    }

    public void save() {
        if (provider instanceof InternalEconomy internal) {
            internal.save();
        }
    }

    public void saveAsync() {
        if (provider instanceof InternalEconomy internal) {
            internal.saveAsync();
        }
    }
}
