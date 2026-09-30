package com.freebuff.casino.gui;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.stats.PlayerStats;
import com.freebuff.casino.stats.StatsStore;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Resumen personal mas el ranking del servidor. */
public final class StatsGui extends Gui {

    private static final int TOP_SIZE = 7;

    public StatsGui(CasinoPlugin plugin, Player player) {
        super(plugin, player, 6, "&8Casino &7· &6Estadisticas");
    }

    @Override
    protected void render() {
        clearActions();
        fill(Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        UUID id = player().getUniqueId();
        PlayerStats stats = plugin.stats().of(id);

        set(4, Items.of(Material.PLAYER_HEAD)
                .name("&6" + player().getName())
                .lore(
                        "&7Partidas jugadas: &f" + stats.games,
                        "&7Victorias: &f" + stats.wins + " &7(" + Text.percent(stats.winRate()) + ")",
                        "&7Apostado: &f" + plugin.economy().format(stats.wagered),
                        "&7Recuperado: &f" + plugin.economy().format(stats.returned),
                        (stats.profit() >= 0 ? "&7Beneficio: &a" : "&7Beneficio: &c")
                                + plugin.economy().format(stats.profit()),
                        "&7Retorno real: &f" + Text.percent(stats.rtp()),
                        "&7Mayor premio: &f" + plugin.economy().format(stats.biggestWin),
                        "&7Juego favorito: &f" + stats.favouriteGame())
                .glow(true)
                .build());

        set(20, Items.of(Material.EMERALD)
                .name("&aTop beneficios")
                .lore(topLore(plugin.stats().topByProfit(TOP_SIZE), PlayerStats::profit))
                .build());

        set(22, Items.of(Material.GOLD_BLOCK)
                .name("&6Top apostado")
                .lore(topLore(plugin.stats().topByWagered(TOP_SIZE), entry -> entry.wagered))
                .build());

        set(24, Items.of(Material.DIAMOND)
                .name("&bTop mayor premio")
                .lore(topLore(plugin.stats().topByBiggestWin(TOP_SIZE), entry -> entry.biggestWin))
                .build());

        set(31, Items.of(Material.REDSTONE)
                .name("&cGanancia de la casa")
                .lore(
                        "&7Total ganado por el casino: &f"
                                + plugin.economy().format(plugin.stats().houseProfit()),
                        "&7Ventaja configurada: &f"
                                + Text.percent(plugin.config().houseEdge()))
                .build());

        set(38, Items.of(Material.ARROW)
                .name("&eVolver al menu")
                .build(), e -> {
            close();
            plugin.guis().openHub(player());
        });

        set(42, Items.of(Material.BARRIER)
                .name("&cCerrar")
                .build(), e -> close());
    }

    private List<String> topLore(List<StatsStore.TopEntry> entries, ToDoubleFunction<PlayerStats> value) {
        if (entries.isEmpty()) {
            return List.of("&7Todavia no hay datos.");
        }
        return entries.stream()
                .map(entry -> "&7" + entry.name() + ": &f"
                        + plugin.economy().format(value.applyAsDouble(entry.stats())))
                .toList();
    }

    @Override
    public String sessionId() {
        return "stats";
    }
}
