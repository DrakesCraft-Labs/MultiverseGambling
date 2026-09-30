package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.util.Items;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Main menu: two tabs, solo and group. Every label follows the player's language.
 */
public final class HubGui extends Gui {

    private static final int[] GAME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private GameCategory tab;

    public HubGui(MultiverseGamblingPlugin plugin, Player player) {
        this(plugin, player, GameCategory.SOLO);
    }

    public HubGui(MultiverseGamblingPlugin plugin, Player player, GameCategory tab) {
        super(plugin, player, 6, plugin.messages().forSender(player, "gui.hub.title"));
        this.tab = tab;
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    protected void render() {
        clearActions();
        fill(Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        Player viewer = player();

        double balance = plugin.economy().balance(viewer.getUniqueId());
        set(4, Items.of(Material.GOLD_INGOT)
                .name(messages().forSender(viewer, "gui.hub.balance",
                        "balance", plugin.economy().format(balance)))
                .lore(messages().loreFor(viewer, "gui.hub.balance-lore",
                        "provider", plugin.economy().provider().name(),
                        "min", plugin.economy().format(plugin.config().minBet())))
                .glow(true)
                .build());

        set(2, Items.of(Material.BOOK)
                .name(messages().forSender(viewer, "gui.hub.stats"))
                .lore(messages().loreFor(viewer, "gui.hub.stats-lore"))
                .build(), e -> {
            close();
            plugin.guis().openStats(viewer);
        });

        set(6, Items.of(Material.ENDER_EYE)
                .name(messages().forSender(viewer, "gui.hub.fairness"))
                .lore(messages().loreFor(viewer, "gui.hub.fairness-lore",
                        "hash", plugin.fair().serverSeedHash().substring(0, 24)))
                .build(), e -> {
            close();
            plugin.guis().sendVerify(viewer);
        });

        List<Game> games = new ArrayList<>(plugin.games().byCategory(tab));
        int index = 0;
        for (Game game : games) {
            if (index >= GAME_SLOTS.length) {
                break;
            }
            boolean enabled = game.enabled();
            List<String> lore = new ArrayList<>(game.displayDescription(viewer));
            lore.add("");
            lore.addAll(game.statusLore(viewer));
            lore.add(messages().forSender(viewer, "gui.hub.bet",
                    "min", plugin.economy().format(game.minBet()),
                    "max", plugin.economy().format(game.maxBet())));
            if (game.category() == GameCategory.GROUP) {
                lore.add(messages().forSender(viewer, "gui.hub.players",
                        "min", game.minPlayers(), "max", game.maxPlayers()));
            }
            lore.add("");
            lore.add(messages().forSender(viewer, enabled ? "gui.hub.play" : "gui.hub.disabled"));

            ItemStack icon = Items.of(enabled ? game.icon() : Material.BARRIER)
                    .name((enabled ? "&6" : "&8") + game.displayName(viewer))
                    .lore(lore)
                    .glow(enabled && game.category() == GameCategory.GROUP && game.activePlayers() > 0)
                    .build();
            set(GAME_SLOTS[index], icon, e -> {
                close();
                game.open(viewer);
            });
            index++;
        }

        // Tabs.
        for (GameCategory category : GameCategory.values()) {
            boolean selected = category == tab;
            int amount = plugin.games().byCategory(category).size();
            set(tabSlot(category), Items.of(selected ? Material.LIME_STAINED_GLASS_PANE : category.tabIcon())
                    .name((selected ? "&a&l" : "&7") + categoryName(viewer, category))
                    .lore(messages().loreFor(viewer, "gui.hub.tab-lore",
                            "description", categoryDescription(viewer, category),
                            "amount", amount,
                            "state", messages().forSender(viewer,
                                    selected ? "gui.hub.tab-selected" : "gui.hub.tab-switch")))
                    .glow(selected)
                    .build(), e -> {
                tab = category;
                refresh();
            });
        }

        set(49, Items.of(Material.BARRIER)
                .name(messages().forSender(viewer, "gui.hub.close"))
                .build(), e -> close());

        set(45, Items.of(Material.EMERALD)
                .name("&a" + categoryName(viewer, GameCategory.GROUP))
                .lore(messages().loreFor(viewer, "gui.hub.group-lore"))
                .build(), e -> {
            tab = GameCategory.GROUP;
            refresh();
        });

        set(53, Items.of(Material.DIAMOND)
                .name("&b" + categoryName(viewer, GameCategory.SOLO))
                .lore(messages().loreFor(viewer, "gui.hub.solo-lore"))
                .build(), e -> {
            tab = GameCategory.SOLO;
            refresh();
        });
    }

    private String categoryName(Player viewer, GameCategory category) {
        return messages().forSenderOr(viewer, "gui.category." + category.key() + ".name",
                category.label());
    }

    private String categoryDescription(Player viewer, GameCategory category) {
        return messages().forSenderOr(viewer, "gui.category." + category.key() + ".description",
                category.description());
    }

    private int tabSlot(GameCategory category) {
        return category == GameCategory.SOLO ? 38 : 42;
    }

    @Override
    public String sessionId() {
        return "hub";
    }
}
