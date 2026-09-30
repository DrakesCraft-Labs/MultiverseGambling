package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.PlinkoTable;
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

/**
 * Plinko: the ball falls down the pyramid and lands in a bucket.
 *
 * <p>The buckets are not invented. They come from the real binomial distribution of
 * the bounces, divided by the number of buckets so the return is the right one: a
 * test checks that the table never pays out more than it takes in.</p>
 */
public final class PlinkoGame extends AbstractSoloGame {

    public PlinkoGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("plinko", "Plinko", GameCategory.SOLO, Material.SNOWBALL)
                .desc("&7Drop the ball and watch which bucket",
                        "&7it lands in. The edges pay a lot",
                        "&7and the middle almost nothing.")
                .build());
    }

    private int rows() {
        return plugin.config().plinkoRows();
    }

    private double[] table() {
        return PlinkoTable.multipliers(rows(), plugin.config().houseEdge(),
                plugin.config().plinkoMaxMultiplier());
    }

    double payoutFor(int bucket) {
        return table()[Math.max(0, Math.min(rows(), bucket))];
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new PlinkoGui(plugin, player, this, wager).show();
    }

    /** Drops the ball. */
    void drop(Player player, Wager wager) {
        int rows = rows();
        // The direction of every bounce comes from the provably fair generator.
        double[] rolls = plugin.fair().rolls(player.getUniqueId(), rows);
        int finalBucket = bucketFor(rolls);
        TimedSession animation = new TimedSession(plugin, player, id(), rows * 3) {
            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                int level = Math.min(rows, elapsed / 3);
                StringBuilder path = new StringBuilder();
                for (int i = 0; i < level; i++) {
                    path.append(rolls[i] < 0.5 ? "&e>" : "&b<");
                }
                online.sendActionBar(Text.c("&7The ball falls &8" + path + " &7(" + level + "/" + rows + ")"));
                if (elapsed % 3 == 0) {
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + level * 0.05f);
                }
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(wager);
                    return;
                }
                double multiplier = PlinkoGame.this.payoutFor(finalBucket);
                double payout = settle(online, wager, multiplier);
                announceResult(online, payout > wager.amount(),
                        "&fCubo " + finalBucket + " &8· &a" + Text.multiplier(multiplier));
                info(online, title());
                info(online, "&7The ball landed in bucket &f" + finalBucket + "&7/&f" + rows
                        + " y pago &f" + Text.multiplier(multiplier));
                info(online, "&7Chance of that bucket: &f"
                        + Text.percent(PlinkoTable.bucketChance(rows, finalBucket)));
                showResult(online, wager.amount(), payout);
                sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, payout > wager.amount() ? 1.3f : 0.9f);
                offerReplay(online);
            }
        };
        animation.run();
    }

    /** Counts the bounces to the right: that is the final bucket. */
    private static int bucketFor(double[] rolls) {
        int bucket = 0;
        for (double roll : rolls) {
            if (roll < 0.5) {
                bucket++;
            }
        }
        return bucket;
    }

    private final class PlinkoGui extends Gui {

        private final PlinkoGame game;
        private final Wager wager;
        private boolean armed;

        PlinkoGui(MultiverseGamblingPlugin plugin, Player player, PlinkoGame game, Wager wager) {
            super(plugin, player, 5, "&8" + displayName(player) + " &7· &6Drop the ball");
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            int rows = game.rows();
            double[] table = game.table();
            set(4, Items.of(Material.SNOWBALL)
                    .name("&6Table of &f" + rows + " &6rows")
                    .lore(
                            "&7Bet: &f" + plugin.economy().format(wager.amount()),
                            "&7Cubos: &f" + (rows + 1),
                            "&7The edges are very rare and pay a lot;",
                            "&7the middle comes up almost always and pays little.",
                            "&7Theoretical return: &f"
                                    + Text.percent(PlinkoTable.rtp(table, rows, 0)) + "&7.")
                    .glow(true)
                    .build());

            // The 9 most representative buckets, centred.
            int shown = Math.min(9, rows + 1);
            int offset = (rows + 1 - shown) / 2;
            for (int i = 0; i < shown; i++) {
                int bucket = offset + i;
                int slot = 19 + i + (9 - shown) / 2;
                set(slot, Items.of(bucket == 0 || bucket == rows ? Material.GOLD_BLOCK : Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name("&fCubo " + bucket + ": &a" + Text.multiplier(table[bucket]))
                        .lore("&7Probabilidad: &f" + Text.percent(PlinkoTable.bucketChance(rows, bucket)))
                        .glow(bucket == 0 || bucket == rows)
                        .build());
            }

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name("&a&lDROP THE BALL")
                    .lore("&7You stake &6" + plugin.economy().format(wager.amount()),
                            "&7and the ball falls on its own.", "",
                            "&eClick to drop")
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.drop(player(), wager);
            });

            set(36, Items.of(Material.BARRIER)
                    .name("&cCancelar")
                    .lore("&7You get your stake back.")
                    .build(), e -> close());
        }

        @Override
        protected void onClose() {
            if (!armed) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "plinko";
        }
    }
}
