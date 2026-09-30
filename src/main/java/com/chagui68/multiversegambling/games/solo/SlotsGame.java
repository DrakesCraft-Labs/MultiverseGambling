package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.SlotsTable;
import com.chagui68.multiversegambling.engine.SlotsTable.Symbol;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Tragaperras de tres rodillos.
 *
 * <p>El resultado sale del generador verificable y se paga con la tabla de
 * {@link SlotsTable}, cuyo retorno teorico esta fijado por un test en ~94.75%.
 * Durante el giro los rodillos solo son decoracion.</p>
 */
public final class SlotsGame extends AbstractSoloGame {

    public SlotsGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("tragamonedas", "Tragamonedas", GameCategory.SOLO, Material.LEVER)
                .desc("&7Tres rodillos, siete simbolos y un",
                        "&7jackpot de &f600x&7 en la corona.",
                        "&7Devuelve cerca del &f95%&7 de lo apostado.")
                .build());
    }

    private SlotsTable table() {
        return SlotsTable.defaults();
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager != null) {
            spin(player, wager);
        }
    }

    /** Gira una vez y abre el panel con el resultado. */
    void spin(Player player, Wager wager) {
        SlotsTable table = table();
        // Los rodillos reales salen del azar verificable; los del giro son pintura.
        List<Symbol> result = table.spin(() -> plugin.fair().roll(player.getUniqueId()));
        int total = plugin.config().slotsSpinTicks();

        TimedSession animation = new TimedSession(plugin, player, id(), total) {

            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                double progress = (double) elapsed / duration;
                int wait = 1 + (int) (progress * progress * 6);
                if (elapsed % wait == 0) {
                    List<Symbol> filler = table.spin();
                    online.sendActionBar(Text.c("&8[ &r" + glyph(filler.get(0)) + " &8| &r"
                            + glyph(filler.get(1)) + " &8| &r" + glyph(filler.get(2)) + " &8]"));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + (float) progress * 0.8f);
                }
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(wager);
                    return;
                }
                double multiplier = table.payout(result);
                double payout = settle(online, wager, multiplier);
                String reels = "&8[ &r" + glyph(result.get(0)) + " &8| &r" + glyph(result.get(1))
                        + " &8| &r" + glyph(result.get(2)) + " &8]";
                online.sendActionBar(Text.c(reels));

                announceResult(online, payout > wager.amount(),
                        multiplier > 0 ? Text.multiplier(multiplier) : "sin premio");
                info(online, title());
                info(online, reels);
                if (multiplier > 0) {
                    info(online, "&7Combinacion ganadora: &f" + Text.multiplier(multiplier));
                }
                showResult(online, wager.amount(), payout);
                sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, payout > wager.amount() ? 1.3f : 0.9f);

                new SlotsGui(plugin, online, SlotsGame.this, result, payout).show();
            }
        };
        animation.run();
    }

    static String glyph(Symbol symbol) {
        return symbol.glyph() + " &7" + symbol.id();
    }

    private static Material iconOf(String id) {
        return switch (id) {
            case "cereza" -> Material.RED_DYE;
            case "limon" -> Material.YELLOW_DYE;
            case "campana" -> Material.BELL;
            case "diamante" -> Material.DIAMOND;
            case "siete" -> Material.GOLD_INGOT;
            case "estrella" -> Material.NETHER_STAR;
            default -> Material.EMERALD;
        };
    }

    /** Panel de la maquina: muestra los rodillos y permite volver a tirar. */
    private static final class SlotsGui extends Gui {

        private final SlotsGame game;
        private final List<Symbol> reels;
        private final double lastPayout;

        SlotsGui(MultiverseGamblingPlugin plugin, Player player, SlotsGame game, List<Symbol> reels, double lastPayout) {
            super(plugin, player, 5, "&8Tragamonedas");
            this.game = game;
            this.reels = reels;
            this.lastPayout = lastPayout;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            int[] slots = {21, 23, 25};
            for (int i = 0; i < 3; i++) {
                Symbol symbol = reels == null ? null : reels.get(i);
                set(slots[i], Items.of(symbol == null ? Material.GRAY_DYE : iconOf(symbol.id()))
                        .name(symbol == null ? "&8?" : "&f" + symbol.glyph() + " " + symbol.id())
                        .lore(symbol == null ? "&7Pulsa TIRAR" : "&7Tres iguales: &f"
                                + Text.multiplier(symbol.triple()))
                        .glow(symbol != null)
                        .build());
            }

            double bet = Math.min(game.maxBet(), Math.max(game.minBet(), plugin.config().minBet() * 10));
            double balance = plugin.economy().balance(player().getUniqueId());
            boolean afford = balance >= game.minBet();

            set(4, Items.of(Material.PAPER)
                    .name("&6Tabla de premios")
                    .lore(game.table().symbols().stream()
                            .map(s -> "&7" + s.glyph() + " " + s.id() + ": &f"
                                    + Text.multiplier(s.triple()) + " &8(al " + Text.percent(
                                            game.table().chanceOf(s.id())) + ")")
                            .toList())
                    .build());

            set(40, Items.of(afford ? Material.EMERALD_BLOCK : Material.RED_CONCRETE)
                    .name(afford ? "&a&lTIRAR" : "&c&lSIN SALDO")
                    .lore(
                            "&7Se apostaran &6" + plugin.economy().format(bet),
                            "&7Saldo: &f" + plugin.economy().format(balance),
                            lastPayout > 0 ? "&7Ultimo premio: &f" + plugin.economy().format(lastPayout) : "",
                            "",
                            afford ? "&ePulsa para girar" : "&7Necesitas mas saldo")
                    .glow(afford)
                    .build(), e -> {
                if (!afford) {
                    return;
                }
                Wager wager = game.stake(player(), bet);
                if (wager == null) {
                    return;
                }
                close();
                game.spin(player(), wager);
            });

            set(36, Items.of(Material.BARRIER)
                    .name("&cSalir")
                    .build(), e -> close());

            set(44, Items.of(Material.ARROW)
                    .name("&eCambiar la apuesta")
                    .build(), e -> {
                close();
                plugin.guis().openBetSelector(player(), game, amount -> game.start(player(), amount));
            });
        }

        @Override
        public String sessionId() {
            return "tragamonedas";
        }
    }
}
