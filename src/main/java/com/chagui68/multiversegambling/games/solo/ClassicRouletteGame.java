package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.RouletteTable;
import com.chagui68.multiversegambling.engine.RouletteTable.Bet;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The classic roulette.
 *
 * <p>The winning pocket is decided by the provably fair generator; the dancing numbers
 * are only paint. Anybody can recompute the roll with {@code /mvgam verify}.</p>
 */
public final class ClassicRouletteGame extends AbstractSoloGame {

    public ClassicRouletteGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("roulette", "Classic Roulette", GameCategory.SOLO, Material.TARGET)
                .desc("&7Red, black, dozens, columns or an",
                        "&7exact number: from &f2x &7to &f36x&7.",
                        "&7Zero is green and only pays on the number itself.")
                .build());
    }

    private RouletteTable table() {
        return plugin.config().rouletteAmerican() ? RouletteTable.american() : RouletteTable.european();
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new RouletteGui(plugin, player, this, wager).show();
    }

    /** Refunds a bet that has not spun yet. */
    void release(Wager wager) {
        refund(wager);
    }

    /** Refunds the board bet and asks for a new one to go straight to a number. */
    void betOnPocket(Player player, Wager previous, int pocket) {
        refund(previous);
        plugin.guis().openBetSelector(player, this, bet -> {
            Wager wager = stake(player, bet);
            if (wager != null) {
                spin(player, wager, Bet.NUMBER, pocket);
            }
        });
    }

    /** Spins the wheel and settles the bet. */
    void spin(Player player, Wager wager, Bet type, int selection) {
        RouletteTable wheel = table();
        int total = plugin.config().rouletteSpinTicks();

        TimedSession animation = new TimedSession(plugin, player, id(), total) {

            @Override
            protected void onStart() {
                sound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
            }

            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                // Filler number: pure show, it decides nothing.
                int shown = Rng.intBetween(0, wheel.pocketCount() - 1);
                double progress = (double) elapsed / duration;
                int wait = 1 + (int) (progress * progress * 12);
                if (elapsed % wait == 0) {
                    actionBarKey(online, "panel.roulette.spinning",
                            "number", RouletteTable.label(shown));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.8f + (float) progress * 1.2f);
                }
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(wager);
                    return;
                }
                // Here is the money: the pocket comes from the provably fair generator.
                int result = wheel.pockets().get(
                        plugin.fair().rollInt(online.getUniqueId(), wheel.pocketCount()));
                double multiplier = RouletteTable.payoutOf(type, selection, result);
                double payout = settle(online, wager, multiplier);

                String colour = colourCode(result);
                announceResult(online, payout > wager.amount(),
                        label(online, "panel.roulette.subtitle",
                                "colour", colour,
                                "number", RouletteTable.label(result),
                                "name", colourName(online, result)));
                info(online, title(online));
                message(online, "panel.roulette.result",
                        "spot", betDescription(online, type, selection),
                        "colour", colour,
                        "number", RouletteTable.label(result),
                        "name", colourName(online, result));
                showResult(online, wager.amount(), payout);
                sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, payout > wager.amount() ? 1.2f : 0.9f);
                offerReplay(online);
            }
        };
        animation.run();
    }

    static String colourCode(int pocket) {
        return switch (RouletteTable.colorOf(pocket)) {
            case RED -> "&c";
            case BLACK -> "&8";
            case GREEN -> "&a";
        };
    }

    /** Colour of a pocket, in the language of the reader. */
    String colourName(CommandSender viewer, int pocket) {
        return plugin.messages().forSender(viewer, "panel.colour."
                + RouletteTable.colorOf(pocket).name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Name of a board spot, in the language of the reader. */
    String betName(CommandSender viewer, Bet bet, int selection) {
        return switch (bet) {
            case NUMBER -> label(viewer, "panel.roulette.bet-number",
                    "number", RouletteTable.label(selection));
            case COLOR -> label(viewer, selection == 0
                    ? "panel.roulette.bet-red" : "panel.roulette.bet-black");
            case PARITY -> label(viewer, selection == 0
                    ? "panel.roulette.bet-even" : "panel.roulette.bet-odd");
            case HALF -> label(viewer, selection == 0
                    ? "panel.roulette.bet-low" : "panel.roulette.bet-high");
            case DOZEN -> label(viewer, "panel.roulette.bet-dozen",
                    "from", selection * 12 + 1, "to", selection * 12 + 12);
            case COLUMN -> label(viewer, "panel.roulette.bet-column", "number", selection + 1);
        };
    }

    /** Spot description for the chat, in the language of the reader. */
    String betDescription(CommandSender viewer, Bet bet, int selection) {
        return switch (bet) {
            case NUMBER -> label(viewer, "panel.roulette.spot-number",
                    "number", RouletteTable.label(selection));
            case COLOR -> label(viewer, selection == 0
                    ? "panel.roulette.spot-red" : "panel.roulette.spot-black");
            case PARITY -> label(viewer, selection == 0
                    ? "panel.roulette.spot-even" : "panel.roulette.spot-odd");
            case HALF -> label(viewer, selection == 0
                    ? "panel.roulette.spot-low" : "panel.roulette.spot-high");
            case DOZEN -> label(viewer, "panel.roulette.spot-dozen",
                    "number", selection + 1,
                    "from", selection * 12 + 1, "to", selection * 12 + 12);
            case COLUMN -> label(viewer, "panel.roulette.spot-column", "number", selection + 1);
        };
    }

    static ItemStack filler() {
        return Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
    }

    // ------------------------------------------------------------------ tablero

    /** Betting board: pick a spot and spin. */
    private final class RouletteGui extends Gui {

        private final ClassicRouletteGame game;
        private final Wager wager;
        private Bet type = Bet.COLOR;
        private int selection = 0;
        private boolean armed;

        RouletteGui(MultiverseGamblingPlugin plugin, Player player, ClassicRouletteGame game, Wager wager) {
            super(plugin, player, 6, plugin.messages().forSender(player, "panel.roulette.title",
                    "game", displayName(player)));
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(filler());

            set(4, Items.of(Material.GOLD_INGOT)
                    .name(label(player(), "panel.common.bet",
                            "bet", plugin.economy().format(wager.amount())))
                    .lore(
                            label(player(), "panel.roulette.point",
                                    "spot", game.betName(player(), type, selection)),
                            label(player(), "panel.common.pays",
                                    "multiplier", Text.multiplier(type.payout())),
                            "",
                            label(player(), "panel.roulette.change"))
                    .glow(true)
                    .build());

            set(10, spot(Material.RED_WOOL, Bet.COLOR, 0));
            set(11, spot(Material.BLACK_WOOL, Bet.COLOR, 1));
            set(12, spot(Material.GREEN_WOOL, Bet.NUMBER, 0));
            set(14, Items.of(Material.PAPER)
                    .name(label(player(), "panel.roulette.pick-exact"))
                    .lore(label(player(), "panel.roulette.exact-lore"),
                            label(player(), "panel.roulette.exact-lore-2"))
                    .build(), e -> {
                armed = true;
                close();
                new NumberGrid(plugin, player(), game, wager).show();
            });

            set(19, spot(Material.LIGHT_BLUE_DYE, Bet.PARITY, 0));
            set(20, spot(Material.ORANGE_DYE, Bet.PARITY, 1));
            set(21, spot(Material.LIME_DYE, Bet.HALF, 0));
            set(22, spot(Material.MAGENTA_DYE, Bet.HALF, 1));

            set(24, spot(Material.YELLOW_WOOL, Bet.DOZEN, 0));
            set(25, spot(Material.YELLOW_WOOL, Bet.DOZEN, 1));
            set(26, spot(Material.YELLOW_WOOL, Bet.DOZEN, 2));
            set(29, spot(Material.CYAN_WOOL, Bet.COLUMN, 0));
            set(30, spot(Material.CYAN_WOOL, Bet.COLUMN, 1));
            set(31, spot(Material.CYAN_WOOL, Bet.COLUMN, 2));

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name(label(player(), "panel.roulette.spin"))
                    .lore(
                            label(player(), "panel.roulette.spin-bet",
                                    "bet", plugin.economy().format(wager.amount())),
                            label(player(), "panel.roulette.spin-point",
                                    "spot", game.betDescription(player(), type, selection)),
                            label(player(), "panel.roulette.spin-hit",
                                    "multiplier", Text.multiplier(type.payout())),
                            "",
                            label(player(), "panel.common.click-to-spin"))
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager, type, selection);
            });

            set(49, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.cancel"))
                    .lore(label(player(), "panel.roulette.cancel-lore",
                            "bet", plugin.economy().format(wager.amount())))
                    .build(), e -> close());
        }

        private ItemStack spot(Material material, Bet bet, int index) {
            boolean selected = type == bet && selection == index;
            return Items.of(material)
                    .name((selected ? "&a> " : "") + game.betName(player(), bet, index))
                    .lore(label(player(), "panel.common.pays",
                                    "multiplier", Text.multiplier(bet.payout())),
                            selected ? label(player(), "panel.common.selected")
                                    : label(player(), "panel.common.click-to-pick"))
                    .glow(selected)
                    .build();
        }

        private void pick(Bet bet, int index) {
            type = bet;
            selection = index;
            refresh();
        }

        @Override
        protected void onClose() {
            if (!armed) {
                game.release(wager);
            }
        }

        @Override
        public String sessionId() {
            return "roulette";
        }
    }

    /** Grid from 0 to 36 for the straight up bet. */
    private final class NumberGrid extends Gui {

        private final ClassicRouletteGame game;
        private final Wager wager;

        NumberGrid(MultiverseGamblingPlugin plugin, Player player, ClassicRouletteGame game, Wager wager) {
            super(plugin, player, 6, plugin.messages().forSender(player, "panel.roulette.grid-title",
                    "game", displayName(player)));
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(filler());
            set(4, Items.of(Material.PAPER)
                    .name(label(player(), "panel.roulette.straight"))
                    .lore(label(player(), "panel.roulette.straight-lore"),
                            label(player(), "panel.roulette.straight-lore-2"))
                    .build());

            for (int pocket = 0; pocket <= 36; pocket++) {
                Material material = switch (RouletteTable.colorOf(pocket)) {
                    case RED -> Material.RED_WOOL;
                    case BLACK -> Material.BLACK_WOOL;
                    case GREEN -> Material.GREEN_WOOL;
                };
                final int chosen = pocket;
                set(9 + pocket, Items.of(material)
                        .name(ClassicRouletteGame.colourCode(pocket) + RouletteTable.label(pocket))
                        .lore(label(player(), "panel.roulette.pays-36"))
                        .build(), e -> {
                    close();
                    game.betOnPocket(player(), wager, chosen);
                });
            }

            set(49, Items.of(Material.ARROW)
                    .name(label(player(), "panel.common.back-to-board"))
                    .lore(label(player(), "panel.common.keep-bet"))
                    .build(), e -> {
                close();
                new RouletteGui(plugin, player(), game, wager).show();
            });
        }

        @Override
        public String sessionId() {
            return "roulette-number";
        }
    }
}
