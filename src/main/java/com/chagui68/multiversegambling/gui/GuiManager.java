package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import org.bukkit.entity.Player;

/** Fachada de menus: el resto del plugin solo pide "abre esto". */
public final class GuiManager {

    private final MultiverseGamblingPlugin plugin;
    private final Map<UUID, Double> lastBets = new HashMap<>();

    public GuiManager(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    public void openHub(Player player) {
        if (!player.isOnline()) {
            return;
        }
        new HubGui(plugin, player, GameCategory.SOLO).show();
    }

    public void openHub(Player player, GameCategory tab) {
        if (!player.isOnline()) {
            return;
        }
        new HubGui(plugin, player, tab).show();
    }

    public void openStats(Player player) {
        if (!player.isOnline()) {
            return;
        }
        new StatsGui(plugin, player).show();
    }

    /** Abre el selector de apuesta recordando cuanto aposto la ultima vez. */
    public void openBetSelector(Player player, Game game, DoubleConsumer onConfirm) {
        if (!player.isOnline()) {
            return;
        }
        double balance = plugin.economy().balance(player.getUniqueId());
        double fallback = Math.min(game.maxBet(), Math.max(game.minBet(), plugin.config().minBet() * 10));
        double remembered = lastBets.getOrDefault(player.getUniqueId(), fallback);
        double initial = Math.max(game.minBet(), Math.min(Math.min(game.maxBet(), balance), remembered));
        new BetSelectorGui(plugin, player, game, initial, bet -> {
            lastBets.put(player.getUniqueId(), bet);
            onConfirm.accept(bet);
        }).show();
    }

    /** Envia el bloque de auditoria de azar verificable. */
    public void sendVerify(Player player) {
        if (!player.isOnline()) {
            return;
        }
        plugin.messages().sendRaw(player, "&8&m        &r &6Azar verificable &8&m        ");
        for (String line : plugin.fair().auditLines(player.getUniqueId())) {
            plugin.messages().sendRaw(player, line);
        }
        plugin.messages().sendRaw(player, "");
        plugin.messages().send(player, "azar.explicacion");
        plugin.messages().send(player, "azar.tu-semilla", "semilla", plugin.fair().clientSeed(player.getUniqueId()));
    }

    /** Aviso rojo reutilizable. */
    public void error(Player player, String key, Object... replacements) {
        player.sendMessage(Text.c(plugin.messages().prefix() + plugin.messages().get(key, replacements)));
    }
}
