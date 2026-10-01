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
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.PlinkoBoard;
import com.chagui68.multiversegambling.world.anim.PlinkoShow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
    public boolean supportsItemBets() {
        return true;
    }

    /** Every bucket and what it multiplies the stake by. */
    @Override
    public List<com.chagui68.multiversegambling.game.ItemOutcome> itemOutcomes(Player viewer) {
        int rows = rows();
        double[] table = table();
        List<com.chagui68.multiversegambling.game.ItemOutcome> out = new ArrayList<>();
        for (int bucket = 0; bucket <= rows; bucket++) {
            out.add(new com.chagui68.multiversegambling.game.ItemOutcome(
                    plugin.messages().forSender(viewer, "items.outcome.bucket", "bucket", bucket),
                    table[bucket], PlinkoTable.bucketChance(rows, bucket)));
        }
        return out;
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new PlinkoGui(plugin, player, this, wager).show();
    }

    /** Drops the ball without calling a bucket. */
    void drop(Player player, Wager wager) {
        drop(player, wager, -1);
    }

    /**
     * Drops the ball.
     *
     * @param called bucket the player called before the drop, or {@code -1} when they
     *               dropped the ball straight away. Calling a bucket never changes what
     *               is paid: the rolls do, and the roll is drawn here.
     */
    void drop(Player player, Wager wager, int called) {
        int rows = rows();
        // The direction of every bounce comes from the provably fair generator, and the
        // same rolls drive the ball down the wall drawn in the arena.
        double[] rolls = plugin.fair().rolls(player.getUniqueId(), rows);
        int bucket = PlinkoBoard.bucketOf(rolls);
        UUID playerId = player.getUniqueId();
        int ticks = Math.max(30, rows * 4);

        ArenaStage stage = arenaFor(player, ArenaStage.BOARD_PITCH);
        if (stage != null) {
            PlinkoShow show = new PlinkoShow(plugin, stage, rolls, bucket, ticks).multipliers(table());
            new TimedSession(plugin, player, id(), ticks) {

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
                    settleDrop(playerId, wager, bucket, called);
                }

                @Override
                protected void onCancel() {
                    show.cancel();
                    settleDrop(playerId, wager, bucket, called);
                }
            }.run();
            return;
        }

        // No arena to paint on: the action bar walks the same path in text.
        new TimedSession(plugin, player, id(), ticks) {

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
                online.sendActionBar(Text.c(plugin.messages().forSender(online,
                        "panel.plinko.falling",
                        "path", "&8" + path, "level", level, "rows", rows)));
                if (elapsed % 3 == 0) {
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + level * 0.05f);
                }
            }

            @Override
            protected void onFinish() {
                settleDrop(playerId, wager, bucket, called);
            }

            @Override
            protected void onCancel() {
                settleDrop(playerId, wager, bucket, called);
            }
        }.run();
    }

    /** Pays a drop whose bounces were drawn when the ball started falling. */
    private void settleDrop(UUID playerId, Wager wager, int bucket, int called) {
        double multiplier = payoutFor(bucket);
        Player online = plugin.getServer().getPlayer(playerId);
        if (online == null) {
            // Gone before the end: the result was already drawn, so it is paid as drawn.
            // Refunding here would let anybody cancel a round they saw coming out badly.
            settleOffline(playerId, wager, multiplier);
            return;
        }
        int rows = rows();
        double payout = settle(online, wager, multiplier);
        announceResult(online, payout > wager.amount(),
                plugin.messages().forSender(online, "panel.plinko.bucket-subtitle",
                        "bucket", bucket, "multiplier", Text.multiplier(multiplier)));
        info(online, title(online));
        message(online, "panel.plinko.landed",
                "bucket", bucket, "rows", rows,
                "multiplier", Text.multiplier(multiplier));
        message(online, "panel.plinko.chance",
                "percent", Text.percent(PlinkoTable.bucketChance(rows, bucket)));
        if (called >= 0) {
            // The call is told apart from the payout on purpose: the money came from the
            // table, the call is the player's own guess and is only ever acknowledged.
            message(online, called == bucket ? "panel.plinko.call-hit" : "panel.plinko.call-miss",
                    "bucket", bucket, "called", called);
        }
        showResult(online, wager, payout);
        sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, payout > wager.amount() ? 1.3f : 0.9f);
        offerReplay(online);
    }

    private final class PlinkoGui extends Gui {

        /** Columns inside the frame, where the buckets go. */
        private static final int PER_ROW = 7;

        private final PlinkoGame game;
        private final Wager wager;
        private boolean armed;
        /** Bucket the player called, or -1 while they have not picked one. */
        private int called = -1;

        PlinkoGui(MultiverseGamblingPlugin plugin, Player player, PlinkoGame game, Wager wager) {
            super(plugin, player, 6, plugin.messages().forSender(player, "panel.plinko.title",
                    "game", displayName(player)));
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
            ItemStack frame = Items.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE).name(" ").build();
            for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52}) {
                set(slot, frame);
            }

            int rows = game.rows();
            double[] table = game.table();
            set(4, Items.of(Material.SNOWBALL)
                    .name(label(player(), "panel.plinko.table", "rows", rows))
                    .lore(
                            label(player(), "panel.common.bet", "bet", stakeText(wager, wager.amount())),
                            label(player(), "panel.plinko.buckets", "count", rows + 1),
                            label(player(), "panel.plinko.rtp",
                                    "percent", Text.percent(PlinkoTable.rtp(table, rows, 0))),
                            "",
                            called >= 0
                                    ? label(player(), "panel.plinko.called", "bucket", called)
                                    : label(player(), "panel.plinko.call-bucket"))
                    .glow(true)
                    .build());

            // Every bucket, left to right, in rows of seven centred in the frame and
            // centred vertically between the header and the buttons.
            int buckets = rows + 1;
            int lines = (buckets + PER_ROW - 1) / PER_ROW;
            int firstLine = lines >= 3 ? 1 : lines == 2 ? 2 : 2;
            for (int bucket = 0; bucket < buckets; bucket++) {
                int line = bucket / PER_ROW;
                int inLine = Math.min(PER_ROW, buckets - line * PER_ROW);
                int column = 1 + (PER_ROW - inLine) / 2 + bucket % PER_ROW;
                int slot = (firstLine + line) * 9 + column;
                set(slot, bucketItem(bucket, table[bucket], rows), e -> {
                    called = called == bucketOf(e.getSlot()) ? -1 : bucketOf(e.getSlot());
                    game.sound(player(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
                    refresh();
                });
            }

            set(45, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.cancel"))
                    .lore(label(player(), "panel.common.refund-lore"))
                    .build(), e -> close());
            set(49, Items.of(Material.EMERALD_BLOCK)
                    .name(label(player(), "panel.plinko.drop"))
                    .lore(label(player(), "panel.plinko.will-stake",
                                    "bet", stakeText(wager, wager.amount())),
                            called >= 0 ? label(player(), "panel.plinko.called", "bucket", called)
                                    : label(player(), "panel.plinko.falls-alone"),
                            "",
                            label(player(), "panel.plinko.click-drop"))
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.drop(player(), wager, called);
            });
            set(53, Items.of(Material.BOOK)
                    .name(label(player(), "panel.plinko.how"))
                    .lore(label(player(), "panel.plinko.edges"),
                            label(player(), "panel.plinko.middle"),
                            label(player(), "panel.plinko.call-free"))
                    .build());
        }

        private ItemStack bucketItem(int bucket, double multiplier, int rows) {
            boolean calledBucket = bucket == called;
            Material material = calledBucket ? Material.LIME_CONCRETE
                    : multiplier >= 10 ? Material.GOLD_BLOCK
                    : multiplier >= 2 ? Material.ORANGE_CONCRETE
                    : multiplier >= 1 ? Material.YELLOW_CONCRETE
                    : Material.GRAY_CONCRETE;
            return Items.of(material)
                    .name((calledBucket ? "&a\u25B6 " : "")
                            + label(player(), "panel.plinko.bucket-name",
                            "bucket", bucket, "multiplier", Text.multiplier(multiplier)))
                    .lore(label(player(), "panel.plinko.bucket-chance",
                                    "percent", Text.percent(PlinkoTable.bucketChance(rows, bucket))),
                            label(player(), "panel.lucky-wheel.pays",
                                    "prize", stakeText(wager, wager.amount() * multiplier)),
                            "",
                            calledBucket ? label(player(), "panel.plinko.call-cancel")
                                    : label(player(), "panel.plinko.call-hint"))
                    .glow(calledBucket || multiplier >= 10)
                    .build();
        }

        /**
         * Bucket drawn on a slot of the menu, the reverse of the layout in render.
         */
        private int bucketOf(int slot) {
            int buckets = game.rows() + 1;
            int lines = (buckets + PER_ROW - 1) / PER_ROW;
            int firstLine = lines >= 3 ? 1 : 2;
            int line = slot / 9 - firstLine;
            int inLine = Math.min(PER_ROW, buckets - line * PER_ROW);
            int column = slot % 9 - 1 - (PER_ROW - inLine) / 2;
            return line * PER_ROW + column;
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
