package com.freebuff.casino.gui;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.game.Game;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.util.Items;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Menu principal: dos pestañas, solitario y en grupo. */
public final class HubGui extends Gui {

    private static final int[] GAME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private GameCategory tab;

    public HubGui(CasinoPlugin plugin, Player player) {
        this(plugin, player, GameCategory.SOLO);
    }

    public HubGui(CasinoPlugin plugin, Player player, GameCategory tab) {
        super(plugin, player, 6, "&8Casino &7· &6Menu principal");
        this.tab = tab;
    }

    @Override
    protected void render() {
        clearActions();
        fill(Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());

        double balance = plugin.economy().balance(player().getUniqueId());
        set(4, Items.of(Material.GOLD_INGOT)
                .name("&6Tu saldo: &f" + plugin.economy().format(balance))
                .lore(
                        "&7Economia: &f" + plugin.economy().provider().name(),
                        "&7Apuesta minima: &f" + plugin.economy().format(plugin.config().minBet()),
                        "",
                        "&7Elige un juego abajo para empezar.")
                .glow(true)
                .build());

        set(2, Items.of(Material.BOOK)
                .name("&eTus estadisticas")
                .lore("&7Partidas, ganancia neta y ranking.", "", "&ePulsa para verlas")
                .build(), e -> {
            close();
            plugin.guis().openStats(player());
        });

        set(6, Items.of(Material.ENDER_EYE)
                .name("&dAzar verificable")
                .lore(
                        "&7El casino publica el hash del secreto",
                        "&7antes de jugar: podras comprobar cada",
                        "&7tirada cuando rote.",
                        "",
                        "&7Secreto actual:",
                        "&f" + plugin.fair().serverSeedHash().substring(0, 24) + "...",
                        "",
                        "&e/casino verificar")
                .build(), e -> {
            close();
            plugin.guis().sendVerify(player());
        });

        List<Game> games = new ArrayList<>(plugin.games().byCategory(tab));
        int index = 0;
        for (Game game : games) {
            if (index >= GAME_SLOTS.length) {
                break;
            }
            boolean enabled = game.enabled();
            List<String> lore = new ArrayList<>();
            lore.addAll(game.description());
            lore.add("");
            lore.addAll(game.statusLore());
            lore.add("&7Apuesta: &f" + plugin.economy().format(game.minBet())
                    + " &7- &f" + plugin.economy().format(game.maxBet()));
            if (game.category() == GameCategory.GRUPO) {
                lore.add("&7Jugadores: &f" + game.minPlayers() + " - " + game.maxPlayers());
            }
            lore.add("");
            lore.add(enabled ? "&ePulsa para jugar" : "&cDesactivado en la configuracion");

            ItemStack icon = Items.of(enabled ? game.icon() : Material.BARRIER)
                    .name((enabled ? "&6" : "&8") + game.name())
                    .lore(lore)
                    .glow(enabled && game.category() == GameCategory.GRUPO && game.activePlayers() > 0)
                    .build();
            set(GAME_SLOTS[index], icon, e -> {
                close();
                game.open(player());
            });
            index++;
        }

        // Pestanas.
        for (GameCategory category : GameCategory.values()) {
            boolean selected = category == tab;
            int amount = plugin.games().byCategory(category).size();
            set(tabSlot(category), Items.of(selected ? Material.LIME_STAINED_GLASS_PANE : category.tabIcon())
                    .name((selected ? "&a&l" : "&7") + category.label())
                    .lore(
                            "&7" + category.description(),
                            "&7Juegos disponibles: &f" + amount,
                            "",
                            selected ? "&aEstas viendo esta pestana" : "&ePulsa para cambiar")
                    .glow(selected)
                    .build(), e -> {
                tab = category;
                refresh();
            });
        }

        set(49, Items.of(Material.BARRIER)
                .name("&cCerrar")
                .build(), e -> close());

        set(45, Items.of(Material.EMERALD)
                .name("&aJuegos en grupo")
                .lore("&7Retos contra otros jugadores,", "&7con bote comun y ronda automatica.")
                .build(), e -> {
            tab = GameCategory.GRUPO;
            refresh();
        });

        set(53, Items.of(Material.DIAMOND)
                .name("&bJuegos en solitario")
                .lore("&7Apuestas contra la casa,", "&7sin esperar a nadie.")
                .build(), e -> {
            tab = GameCategory.SOLO;
            refresh();
        });
    }

    private int tabSlot(GameCategory category) {
        return category == GameCategory.SOLO ? 38 : 42;
    }

    @Override
    public String sessionId() {
        return "hub";
    }
}
