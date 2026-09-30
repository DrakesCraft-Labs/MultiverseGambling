package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.DiceTable;
import com.chagui68.multiversegambling.engine.Rng;
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
import org.bukkit.entity.Player;

/** Dados con objetivo: apostar a que sale por encima o por debajo de un numero. */
public final class DiceGame extends AbstractSoloGame {

    public DiceGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("dados", "Dados", GameCategory.SOLO, Material.SLIME_BALL)
                .desc("&7Fija un objetivo del 0.01 al 99.99 y",
                        "&7apuesta a por encima o por debajo.",
                        "&7Cuanto mas dificil, mas paga.")
                .build());
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new DiceGui(plugin, player, this, wager).show();
    }

    double houseEdge() {
        return plugin.config().houseEdge();
    }

    void roll(Player player, Wager wager, double target, boolean over) {
        TimedSession animation = new TimedSession(plugin, player, id(), 25) {

            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                online.sendActionBar(Text.c("&7Tirando... &f"
                        + Text.number(Math.floor(Rng.next() * 10000) / 100.0)));
                if (elapsed % 3 == 0) {
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.2f);
                }
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(wager);
                    return;
                }
                // La tirada real sale del generador verificable.
                double result = DiceTable.round2(plugin.fair().roll(online.getUniqueId()) * 100.0);
                boolean won = DiceTable.wins(result, target, over);
                double chance = over ? DiceTable.winChanceOver(target) : DiceTable.winChanceUnder(target);
                double multiplier = won ? DiceTable.payout(chance, houseEdge()) : 0;
                double payout = settle(online, wager, multiplier);

                announceResult(online, won, "&f" + Text.number(result));
                info(online, title());
                info(online, "&7Objetivo: " + (over ? "&amas de " : "&amenos de ") + "&f"
                        + Text.number(target) + " &8| &7Salio: &f" + Text.number(result));
                showResult(online, wager.amount(), payout);
                sound(online, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, won ? 1.3f : 0.9f);
                offerReplay(online);
            }
        };
        animation.run();
    }

    private static final class DiceGui extends Gui {

        private static final double STEP = 1.0;

        private final DiceGame game;
        private final Wager wager;
        private double target = 50.0;
        private boolean over = true;
        private boolean armed;

        DiceGui(MultiverseGamblingPlugin plugin, Player player, DiceGame game, Wager wager) {
            super(plugin, player, 5, "&8Dados &7· &6Fija tu objetivo");
            this.game = game;
            this.wager = wager;
        }

        private double chance() {
            return over ? DiceTable.winChanceOver(target) : DiceTable.winChanceUnder(target);
        }

        private void shift(double delta) {
            target = DiceTable.round2(Math.max(DiceTable.MIN_TARGET,
                    Math.min(DiceTable.MAX_TARGET, target + delta)));
            refresh();
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            double chance = chance();
            double payout = DiceTable.payout(chance, game.houseEdge());
            double expected = wager.amount() * chance / 100.0 * payout;

            set(4, Items.of(Material.PAPER)
                    .name("&6Objetivo: " + (over ? "&a>" : "&c<") + " &f" + Text.number(target))
                    .lore(
                            "&7Probabilidad de ganar: &f" + Text.percent(chance / 100.0),
                            "&7Pago: &f" + Text.multiplier(payout),
                            "&7Ganarias: &f" + plugin.economy().format(wager.amount() * payout),
                            "&7Valor medio: &f" + plugin.economy().format(expected),
                            "",
                            "&7La casa se queda un &f"
                                    + Text.percent(game.houseEdge()) + "&7 de media.")
                    .glow(true)
                    .build());

            set(10, button(Material.RED_DYE, "&c-10"), e -> shift(-10 * STEP));
            set(11, button(Material.RED_DYE, "&c-1"), e -> shift(-STEP));
            set(12, button(Material.ORANGE_DYE, "&6-0.1"), e -> shift(-0.1));
            set(14, button(Material.LIME_DYE, "&a+0.1"), e -> shift(0.1));
            set(15, button(Material.LIME_DYE, "&a+1"), e -> shift(STEP));
            set(16, button(Material.LIME_DYE, "&a+10"), e -> shift(10 * STEP));

            set(22, Items.of(over ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                    .name("&fSentido: " + (over ? "&aPOR ENCIMA" : "&cPOR DEBAJO"))
                    .lore("&7Cambia entre superar o no llegar", "&7al objetivo.", "",
                            "&ePulsa para invertir")
                    .build(), e -> {
                over = !over;
                target = DiceTable.round2(100.0 - target);
                refresh();
            });

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name("&a&lTIRAR LOS DADOS")
                    .lore(
                            "&7Apostando &6" + plugin.economy().format(wager.amount()),
                            "&7Necesitas que salga " + (over ? "&amas" : "&amenos") + " de &f"
                                    + Text.number(target),
                            "&7Paga &f" + Text.multiplier(payout),
                            "",
                            "&ePulsa para tirar")
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.roll(player(), wager, target, over);
            });

            set(36, Items.of(Material.BARRIER)
                    .name("&cCancelar")
                    .lore("&7Recuperas tu apuesta.")
                    .build(), e -> close());
        }

        private org.bukkit.inventory.ItemStack button(Material material, String name) {
            return Items.of(material).name(name).lore("&7Pulsa para ajustar").build();
        }

        @Override
        protected void onClose() {
            if (!armed) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "dados";
        }
    }
}
