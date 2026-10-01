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

/**
 * Menu facade: the rest of the plugin only asks "open this".
 */
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

    /**
     * Opens the bet selector, remembering how much the player staked last time.
     */
    public void openBetSelector(Player player, Game game, DoubleConsumer onConfirm) {
        openBetSelector(player, game, onConfirm, null);
    }

    /**
     * Same selector with a second confirm button that bets against the house.
     */
    public void openBetSelector(Player player, Game game, DoubleConsumer onConfirm, DoubleConsumer onHouse) {
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
        }, onHouse == null ? null : bet -> {
            lastBets.put(player.getUniqueId(), bet);
            onHouse.accept(bet);
        }).show();
    }

    /**
     * Sends the provably fair audit block.
     */
    public void sendVerify(Player player) {
        if (!player.isOnline()) {
            return;
        }
        plugin.messages().sendRaw(player, plugin.messages().forSender(player, "gui.verify.title"));
        for (String line : plugin.fair().auditLines(player.getUniqueId())) {
            plugin.messages().sendRaw(player, line);
        }
        plugin.messages().sendRaw(player, "");
        plugin.messages().send(player, "fairness.explanation");
        plugin.messages().send(player, "fairness.your-seed", "seed",
                plugin.fair().clientSeed(player.getUniqueId()));
    }

    /**
     * Reusable red warning.
     */
    public void error(Player player, String key, Object... replacements) {
        player.sendMessage(Text.c(plugin.messages().prefixedFor(player, key, replacements)));
    }
}
