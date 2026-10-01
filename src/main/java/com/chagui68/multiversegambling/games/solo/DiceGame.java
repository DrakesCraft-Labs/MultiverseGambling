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
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.DiceTrackShow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Dice with a target: bet that the roll comes over or under a number.
 */
public final class DiceGame extends AbstractSoloGame {

    public DiceGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("dice", "Dice", GameCategory.SOLO, Material.SLIME_BALL)
                .desc("&7Pick a target from 0.01 to 99.99 and",
                        "&7bet on roll over or roll under.",
                        "&7The harder the target, the more it pays.")
                .build());
    }

    @Override
    public boolean supportsItemBets() {
        return true;
    }

    /** A few targets, from the easy to the very hard one: the target is chosen next. */
    @Override
    public List<com.chagui68.multiversegambling.game.ItemOutcome> itemOutcomes(Player viewer) {
        List<com.chagui68.multiversegambling.game.ItemOutcome> out = new ArrayList<>();
        for (double chance : new double[]{75.0, 50.0, 25.0, 10.0, 1.0}) {
            out.add(new com.chagui68.multiversegambling.game.ItemOutcome(
                    plugin.messages().forSender(viewer, "items.outcome.dice", "chance", Text.number(chance) + "%"),
                    DiceTable.payout(chance, houseEdge()), chance / 100.0));
        }
        return out;
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

    /**
     * "over" or "under", already coloured, in the language of the reader.
     */
    String direction(Player viewer, boolean over) {
        return plugin.messages().forSender(viewer, over ? "panel.dice.over" : "panel.dice.under");
    }

    void roll(Player player, Wager wager, double target, boolean over) {
        int total = 45;
        UUID playerId = player.getUniqueId();
        // The real roll comes from the provably fair generator, and the marker on the
        // track in the arena stops exactly on it.
        // One of the 10 000 values from 0.00 to 99.99, all equally likely.
        double result = Math.floor(plugin.fair().roll(playerId) * 10000.0) / 100.0;
        boolean won = DiceTable.wins(result, target, over);

        ArenaStage stage = arenaFor(player);
        if (stage != null) {
            DiceTrackShow show = new DiceTrackShow(plugin, stage, target, result, won, over, total);
            new TimedSession(plugin, player, id(), total) {

                @Override
                protected void onStart() {
                    show.start();
                }

                @Override
                protected void onFrame(int elapsed, int duration) {
                    show.tick();
                }

                @Override
                protected void onFinish() {
                    show.settle();
                    settleRoll(playerId, wager, target, over, result, won);
                }

                @Override
                protected void onCancel() {
                    show.cancel();
                    settleRoll(playerId, wager, target, over, result, won);
                }
            }.run();
            return;
        }

        // No arena to paint on: the action bar keeps the suspense.
        new TimedSession(plugin, player, id(), total) {

            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                online.sendActionBar(Text.c(plugin.messages().forSender(online,
                        "panel.dice.rolling",
                        "value", Text.number(Math.floor(Rng.next() * 10000) / 100.0))));
                if (elapsed % 3 == 0) {
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.2f);
                }
            }

            @Override
            protected void onFinish() {
                settleRoll(playerId, wager, target, over, result, won);
            }

            @Override
            protected void onCancel() {
                settleRoll(playerId, wager, target, over, result, won);
            }
        }.run();
    }

    /** Pays a roll that was drawn when the marker set off. */
    private void settleRoll(UUID playerId, Wager wager, double target, boolean over,
                            double result, boolean won) {
        double chance = over ? DiceTable.winChanceOver(target) : DiceTable.winChanceUnder(target);
        double multiplier = won ? DiceTable.payout(chance, houseEdge()) : 0;
        Player online = plugin.getServer().getPlayer(playerId);
        if (online == null) {
            // Gone before the end: the result was already drawn, so it is paid as drawn.
            // Refunding here would let anybody cancel a round they saw coming out badly.
            settleOffline(playerId, wager, multiplier);
            return;
        }
        double payout = settle(online, wager, multiplier);

        announceResult(online, won, "&f" + Text.number(result));
        info(online, title(online));
        message(online, "panel.dice.info",
                "direction", direction(online, over),
                "target", Text.number(target), "result", Text.number(result));
        showResult(online, wager, payout);
        sound(online, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, won ? 1.3f : 0.9f);
        offerReplay(online);
    }

    private final class DiceGui extends Gui {

        private static final double STEP = 1.0;

        private final DiceGame game;
        private final Wager wager;
        private double target = 50.0;
        private boolean over = true;
        private boolean armed;

        DiceGui(MultiverseGamblingPlugin plugin, Player player, DiceGame game, Wager wager) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.dice.title",
                    "game", displayName(player)));
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
            frame(Material.CYAN_STAINED_GLASS_PANE);

            double chance = chance();
            double payout = DiceTable.payout(chance, game.houseEdge());
            double expected = wager.amount() * chance / 100.0 * payout;

            set(4, Items.of(Material.PAPER)
                    .name(label(player(), over ? "panel.dice.target-over" : "panel.dice.target-under",
                            "value", Text.number(target)))
                    .lore(
                            label(player(), "panel.dice.chance",
                                    "percent", Text.percent(chance / 100.0)),
                            label(player(), "panel.dice.pays",
                                    "multiplier", Text.multiplier(payout)),
                            label(player(), "panel.dice.would-win",
                                    "prize", stakeText(wager, wager.amount() * payout)),
                            label(player(), "panel.dice.expected",
                                    "value", stakeText(wager, expected)),
                            "",
                            label(player(), "panel.dice.house",
                                    "edge", Text.percent(game.houseEdge())))
                    .glow(true)
                    .build());

            set(10, button(Material.RED_DYE, amount(-10)), e -> shift(-10 * STEP));
            set(11, button(Material.RED_DYE, amount(-1)), e -> shift(-STEP));
            set(12, button(Material.ORANGE_DYE, amount(-0.1)), e -> shift(-0.1));
            set(14, button(Material.LIME_DYE, amount(0.1)), e -> shift(0.1));
            set(15, button(Material.LIME_DYE, amount(1)), e -> shift(STEP));
            set(16, button(Material.LIME_DYE, amount(10)), e -> shift(10 * STEP));

            set(22, Items.of(over ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                    .name(label(player(), "panel.dice.direction",
                            "direction", label(player(), over
                                    ? "panel.dice.over-label" : "panel.dice.under-label")))
                    .lore(label(player(), "panel.dice.switch"),
                            label(player(), "panel.dice.switch-2"), "",
                            label(player(), "panel.dice.click-flip"))
                    .build(), e -> {
                over = !over;
                target = DiceTable.round2(100.0 - target);
                refresh();
            });

            // The roll from 0 to 100 as a bar of seven panes: green where it wins.
            for (int i = 0; i < 7; i++) {
                double from = i * 100.0 / 7;
                double to = (i + 1) * 100.0 / 7;
                double middle = (from + to) / 2;
                boolean wins = over ? middle >= target : middle < target;
                set(28 + i, Items.of(wins ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                        .name((wins ? "&a" : "&c") + Text.number(from) + " - " + Text.number(to))
                        .build());
            }

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name(label(player(), "panel.dice.roll"))
                    .lore(
                            label(player(), "panel.dice.staking",
                                    "bet", stakeText(wager, wager.amount())),
                            label(player(), "panel.dice.need-roll",
                                    "direction", game.direction(player(), over),
                                    "target", Text.number(target)),
                            label(player(), "panel.dice.pays-short",
                                    "multiplier", Text.multiplier(payout)),
                            "",
                            label(player(), "panel.common.click-to-roll"))
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.roll(player(), wager, target, over);
            });

            set(36, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.cancel"))
                    .lore(label(player(), "panel.common.refund-lore"))
                    .build(), e -> close());
        }

        /**
         * Name of a step button such as -10 or +0.1.
         */
        private String amount(double step) {
            String shown = Text.number(Math.abs(step));
            return label(player(), step < 0 ? "panel.dice.minus" : "panel.dice.plus",
                    "amount", shown);
        }

        private org.bukkit.inventory.ItemStack button(Material material, String name) {
            return Items.of(material).name(name)
                    .lore(label(player(), "panel.common.click-to-adjust")).build();
        }

        @Override
        protected void onClose() {
            if (!armed) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "dice";
        }
    }
}
