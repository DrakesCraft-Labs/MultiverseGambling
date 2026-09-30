package com.freebuff.casino.games.solo;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.Rng;
import com.freebuff.casino.engine.RouletteTable;
import com.freebuff.casino.engine.RouletteTable.Bet;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.game.AbstractSoloGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.session.TimedSession;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * La ruleta de siempre.
 *
 * <p>La casilla ganadora la decide el generador verificable; el baile de numeros
 * solo es pintura. Cualquiera puede recalcular la tirada con {@code /casino verificar}.</p>
 */
public final class ClassicRouletteGame extends AbstractSoloGame {

    public ClassicRouletteGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("ruleta", "Ruleta Clasica", GameCategory.SOLO, Material.TARGET)
                .desc("&7Rojo, negro, docenas, columnas o un",
                        "&7numero exacto: de &f2x &7a &f36x&7.",
                        "&7El cero es verde y solo paga a caballo.")
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

    /** Devuelve una apuesta que aun no ha girado. */
    void release(Wager wager) {
        refund(wager);
    }

    /** Reembolsa la apuesta del tablero y pide una nueva para ir directo a un numero. */
    void betOnPocket(Player player, Wager previous, int pocket) {
        refund(previous);
        plugin.guis().openBetSelector(player, this, bet -> {
            Wager wager = stake(player, bet);
            if (wager != null) {
                spin(player, wager, Bet.NUMERO, pocket);
            }
        });
    }

    /** Gira la rueda y liquida la apuesta. */
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
                // Numero de relleno: puro espectaculo, no decide nada.
                int shown = Rng.intBetween(0, wheel.pocketCount() - 1);
                double progress = (double) elapsed / duration;
                int wait = 1 + (int) (progress * progress * 12);
                if (elapsed % wait == 0) {
                    online.sendActionBar(Text.c("&7La rueda gira... &f" + RouletteTable.label(shown)));
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
                // Aqui esta el dinero: la casilla sale del generador verificable.
                int result = wheel.pockets().get(
                        plugin.fair().rollInt(online.getUniqueId(), wheel.pocketCount()));
                double multiplier = RouletteTable.payoutOf(type, selection, result);
                double payout = settle(online, wager, multiplier);

                String colour = colourCode(result);
                announceResult(online, payout > wager.amount(),
                        colour + RouletteTable.label(result) + " " + colourName(result));
                info(online, title());
                info(online, "&7Apostaste a &f" + RouletteTable.describeBet(type, selection)
                        + " &7y salio " + colour + RouletteTable.label(result)
                        + " &7(" + colourName(result) + "&7)");
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
            case ROJO -> "&c";
            case NEGRO -> "&8";
            case VERDE -> "&a";
        };
    }

    static String colourName(int pocket) {
        return RouletteTable.colorOf(pocket).name().toLowerCase();
    }

    static ItemStack filler() {
        return Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
    }

    // ------------------------------------------------------------------ tablero

    /** Tablero de apuestas: se elige el punto y se gira. */
    private static final class RouletteGui extends Gui {

        private final ClassicRouletteGame game;
        private final Wager wager;
        private Bet type = Bet.COLOR;
        private int selection = 0;
        private boolean armed;

        RouletteGui(CasinoPlugin plugin, Player player, ClassicRouletteGame game, Wager wager) {
            super(plugin, player, 6, "&8Ruleta &7· &6Coloca tu apuesta");
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(filler());

            set(4, Items.of(Material.GOLD_INGOT)
                    .name("&6Apuesta: &f" + plugin.economy().format(wager.amount()))
                    .lore(
                            "&7Punto elegido: &f" + RouletteTable.describeBet(type, selection),
                            "&7Paga: &f" + Text.multiplier(type.payout()),
                            "",
                            "&7Elige otro punto si quieres cambiarlo.")
                    .glow(true)
                    .build());

            set(10, spot(Material.RED_WOOL, "&cROJO", Bet.COLOR, 0));
            set(11, spot(Material.BLACK_WOOL, "&8NEGRO", Bet.COLOR, 1));
            set(12, spot(Material.GREEN_WOOL, "&aVERDE (0)", Bet.NUMERO, 0));
            set(14, Items.of(Material.PAPER)
                    .name("&fElegir numero exacto")
                    .lore("&7Abre la rejilla del 0 al 36.", "&7Un acierto paga &f36x&7.")
                    .build(), e -> {
                armed = true;
                close();
                new NumberGrid(plugin, player(), game, wager).show();
            });

            set(19, spot(Material.LIGHT_BLUE_DYE, "&bPAR", Bet.PARIDAD, 0));
            set(20, spot(Material.ORANGE_DYE, "&6IMPAR", Bet.PARIDAD, 1));
            set(21, spot(Material.LIME_DYE, "&a1 - 18", Bet.MITAD, 0));
            set(22, spot(Material.MAGENTA_DYE, "&d19 - 36", Bet.MITAD, 1));

            set(24, spot(Material.YELLOW_WOOL, "&eDocena 1-12", Bet.DOCENA, 0));
            set(25, spot(Material.YELLOW_WOOL, "&eDocena 13-24", Bet.DOCENA, 1));
            set(26, spot(Material.YELLOW_WOOL, "&eDocena 25-36", Bet.DOCENA, 2));
            set(29, spot(Material.CYAN_WOOL, "&3Columna 1", Bet.COLUMNA, 0));
            set(30, spot(Material.CYAN_WOOL, "&3Columna 2", Bet.COLUMNA, 1));
            set(31, spot(Material.CYAN_WOOL, "&3Columna 3", Bet.COLUMNA, 2));

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name("&a&lGIRAR LA RUEDA")
                    .lore(
                            "&7Apuesta: &6" + plugin.economy().format(wager.amount()),
                            "&7Punto: &f" + RouletteTable.describeBet(type, selection),
                            "&7Si acierta recuperas &f" + Text.multiplier(type.payout()) + "&7.",
                            "",
                            "&ePulsa para girar")
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager, type, selection);
            });

            set(49, Items.of(Material.BARRIER)
                    .name("&cCancelar")
                    .lore("&7Recuperas tus &f" + plugin.economy().format(wager.amount()) + "&7.")
                    .build(), e -> close());
        }

        private ItemStack spot(Material material, String name, Bet bet, int index) {
            boolean selected = type == bet && selection == index;
            return Items.of(material)
                    .name((selected ? "&a> " : "") + name)
                    .lore("&7Paga &f" + Text.multiplier(bet.payout()),
                            selected ? "&aSeleccionado" : "&7Pulsa para apostar aqui")
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
            return "ruleta";
        }
    }

    /** Rejilla del 0 al 36 para la apuesta directa. */
    private static final class NumberGrid extends Gui {

        private final ClassicRouletteGame game;
        private final Wager wager;

        NumberGrid(CasinoPlugin plugin, Player player, ClassicRouletteGame game, Wager wager) {
            super(plugin, player, 6, "&8Ruleta &7· &6Elige un numero");
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(filler());
            set(4, Items.of(Material.PAPER)
                    .name("&6Apuesta directa")
                    .lore("&7Pulsa un numero del 0 al 36.",
                            "&7Acierta y cobras &f36x&7.")
                    .build());

            for (int pocket = 0; pocket <= 36; pocket++) {
                Material material = switch (RouletteTable.colorOf(pocket)) {
                    case ROJO -> Material.RED_WOOL;
                    case NEGRO -> Material.BLACK_WOOL;
                    case VERDE -> Material.GREEN_WOOL;
                };
                final int chosen = pocket;
                set(9 + pocket, Items.of(material)
                        .name(ClassicRouletteGame.colourCode(pocket) + RouletteTable.label(pocket))
                        .lore("&7Paga &f36x")
                        .build(), e -> {
                    close();
                    game.betOnPocket(player(), wager, chosen);
                });
            }

            set(49, Items.of(Material.ARROW)
                    .name("&eVolver al tablero")
                    .lore("&7Mantienes tu apuesta actual.")
                    .build(), e -> {
                close();
                new RouletteGui(plugin, player(), game, wager).show();
            });
        }

        @Override
        public String sessionId() {
            return "ruleta-numero";
        }
    }
}
