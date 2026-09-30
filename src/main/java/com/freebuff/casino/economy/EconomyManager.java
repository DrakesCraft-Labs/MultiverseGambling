package com.freebuff.casino.economy;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.util.Text;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Unico punto por el que entra y sale dinero del casino. */
public final class EconomyManager {

    private final CasinoPlugin plugin;
    private EconomyProvider provider;
    private double startingBalance = 1000;

    public EconomyManager(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    /** Detecta Vault o cae al monedero interno. Se vuelve a llamar en cada /casino reload. */
    public void setup() {
        startingBalance = plugin.config().startingBalance();
        String mode = plugin.config().economyProvider();
        EconomyProvider vault = "interno".equalsIgnoreCase(mode) ? null : VaultEconomy.tryHook();
        if (vault != null) {
            provider = vault;
            plugin.getLogger().info("Economia enlazada: " + provider.name());
        } else {
            if (!"interno".equalsIgnoreCase(mode) && !"auto".equalsIgnoreCase(mode)) {
                plugin.getLogger().warning("Proveedor de economia '" + mode + "' desconocido, se usa el interno.");
            }
            provider = new InternalEconomy(plugin);
            plugin.getLogger().info("Usando el monedero interno del casino (balances.json).");
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

    /** Da la cuenta de bienvenida si es la primera vez que el jugador entra. */
    public void ensureAccount(Player player) {
        if (provider.createAccountIfMissing(player.getUniqueId(), startingBalance)) {
            plugin.messages().send(player, "economia.cuenta-creada", "saldo", format(startingBalance));
        }
    }

    /**
     * Retira la apuesta del monedero y la envuelve en un {@link Wager}.
     *
     * @return {@code null} si el jugador no tiene saldo suficiente.
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

    /** Crea una apuesta ya cobrada (para pagos internos o pruebas). */
    public Wager wager(UUID playerId, double amount) {
        return new Wager(this, playerId, amount);
    }

    public String format(double amount) {
        String format = plugin.config().currencyFormat();
        String formatted = Text.fill(format,
                "amount", Text.number(amount),
                "moneda", amount == 1 ? plugin.config().currencyName() : plugin.config().currencyName());
        return formatted;
    }

    /** Formato compacto para lore de menu. */
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
