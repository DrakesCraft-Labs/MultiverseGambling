package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.stats.PlayerStats;
import com.chagui68.multiversegambling.stats.StatsStore;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;

import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Personal summary plus the server ranking, in the player's language.
 */
public final class StatsGui extends Gui {

    private static final int TOP_SIZE = 7;

    public StatsGui(MultiverseGamblingPlugin plugin, Player player) {
        super(plugin, player, 6, plugin.messages().forSender(player, "gui.stats.title"));
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    protected void render() {
        clearActions();
        frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        Player viewer = player();

        UUID id = viewer.getUniqueId();
        PlayerStats stats = plugin.stats().of(id);
        double profit = stats.profit();

        set(4, head(viewer, Items.of(Material.PLAYER_HEAD)
                .name("&6" + viewer.getName())
                .lore(messages().loreFor(viewer, "gui.stats.lore",
                        "games", stats.games,
                        "wins", stats.wins,
                        "rate", Text.percent(stats.winRate()),
                        "wagered", plugin.economy().format(stats.wagered),
                        "returned", plugin.economy().format(stats.returned),
                        "profit", (profit >= 0 ? "&a" : "&c") + plugin.economy().format(profit),
                        "rtp", Text.percent(stats.rtp()),
                        "biggest", plugin.economy().format(stats.biggestWin),
                        "favourite", messages().gameName(viewer, stats.favouriteGame(),
                                stats.favouriteGame())))
                .glow(true)
                .build()));

        set(20, Items.of(Material.EMERALD)
                .name(messages().forSender(viewer, "gui.stats.top-profit"))
                .lore(topLore(viewer, plugin.stats().topByProfit(TOP_SIZE), PlayerStats::profit))
                .build());

        set(22, Items.of(Material.GOLD_BLOCK)
                .name(messages().forSender(viewer, "gui.stats.top-wagered"))
                .lore(topLore(viewer, plugin.stats().topByWagered(TOP_SIZE), entry -> entry.wagered))
                .build());

        set(24, Items.of(Material.DIAMOND)
                .name(messages().forSender(viewer, "gui.stats.top-prize"))
                .lore(topLore(viewer, plugin.stats().topByBiggestWin(TOP_SIZE), entry -> entry.biggestWin))
                .build());

        set(31, Items.of(Material.REDSTONE)
                .name(messages().forSender(viewer, "gui.stats.house"))
                .lore(messages().loreFor(viewer, "gui.stats.house-lore",
                        "profit", plugin.economy().format(plugin.stats().houseProfit()),
                        "edge", Text.percent(plugin.config().houseEdge())))
                .build());

        set(38, Items.of(Material.ARROW)
                .name(messages().forSender(viewer, "gui.stats.back"))
                .build(), e -> {
            close();
            plugin.guis().openHub(viewer);
        });

        set(42, Items.of(Material.BARRIER)
                .name(messages().forSender(viewer, "gui.hub.close"))
                .build(), e -> close());
    }

    private List<String> topLore(Player viewer, List<StatsStore.TopEntry> entries,
                                 ToDoubleFunction<PlayerStats> value) {
        if (entries.isEmpty()) {
            return messages().loreFor(viewer, "gui.stats.top-empty");
        }
        return entries.stream()
                .map(entry -> messages().forSender(viewer, "gui.stats.entry",
                        "player", entry.name(),
                        "value", plugin.economy().format(value.applyAsDouble(entry.stats()))))
                .toList();
    }

    private static org.bukkit.inventory.ItemStack head(Player owner, org.bukkit.inventory.ItemStack item) {
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta meta) {
            meta.setOwningPlayer(owner);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public String sessionId() {
        return "stats";
    }

    @Override
    protected boolean holdsRound() {
        return false;
    }
}
