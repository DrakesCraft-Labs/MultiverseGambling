package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.config.Messages;
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
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.SlotReelsShow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Three reel slots.
 *
 * <p>The result comes from the provably fair generator and is paid with the
 * {@link SlotsTable} table, whose theoretical return is pinned by a test at ~94.75%.
 * While the reels spin they are only decoration.</p>
 */
public final class SlotsGame extends AbstractSoloGame {

    public SlotsGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("slots", "Slots", GameCategory.SOLO, Material.LEVER)
                .desc("&7Three reels, seven symbols and a",
                        "&7jackpot of &f600x&7 on the crown.",
                        "&7It returns close to &f95%&7 of what is staked.")
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

    /**
     * Spins once and opens the panel with the result.
     */
    void spin(Player player, Wager wager) {
        SlotsTable table = table();
        // The real reels come from the provably fair roll; the spinning ones are paint.
        List<Symbol> result = table.spin(() -> plugin.fair().roll(player.getUniqueId()));
        int total = plugin.config().slotsSpinTicks();
        UUID playerId = player.getUniqueId();

        ArenaStage stage = arenaFor(player);
        if (stage != null) {
            SlotReelsShow show = new SlotReelsShow(plugin, stage,
                    blocks(result), blocks(table.symbols()), total);
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
                    settleSpin(playerId, wager, result);
                }

                @Override
                protected void onCancel() {
                    show.cancel();
                    refund(wager);
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
                double progress = (double) elapsed / duration;
                int wait = 1 + (int) (progress * progress * 6);
                if (elapsed % wait == 0) {
                    List<Symbol> filler = table.spin();
                    online.sendActionBar(Text.c("&8[ &r" + glyph(online, filler.get(0)) + " &8| &r"
                            + glyph(online, filler.get(1)) + " &8| &r"
                            + glyph(online, filler.get(2)) + " &8]"));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + (float) progress * 0.8f);
                }
            }

            @Override
            protected void onFinish() {
                settleSpin(playerId, wager, result);
            }

            @Override
            protected void onCancel() {
                refund(wager);
            }
        }.run();
    }

    /**
     * Pays a spin whose reels came from the provably fair generator.
     */
    private void settleSpin(UUID playerId, Wager wager, List<Symbol> result) {
        Player online = plugin.getServer().getPlayer(playerId);
        if (online == null) {
            refund(wager);
            return;
        }
        SlotsTable table = table();
        double multiplier = table.payout(result);
        double payout = settle(online, wager, multiplier);
        String reels = "&8[ &r" + glyph(online, result.get(0)) + " &8| &r"
                + glyph(online, result.get(1)) + " &8| &r" + glyph(online, result.get(2))
                + " &8]";
        online.sendActionBar(Text.c(reels));

        announceResult(online, payout > wager.amount(),
                multiplier > 0 ? Text.multiplier(multiplier)
                        : plugin.messages().forSender(online, "panel.common.no-prize"));
        info(online, title(online));
        info(online, reels);
        if (multiplier > 0) {
            message(online, "panel.slots.winning-combination",
                    "multiplier", Text.multiplier(multiplier));
        }
        showResult(online, wager.amount(), payout);
        sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, payout > wager.amount() ? 1.3f : 0.9f);

        new SlotsGui(plugin, online, SlotsGame.this, result, payout).show();
    }

    /**
     * Block that stands for a reel symbol in the arena.
     */
    static Material blockOf(String id) {
        return switch (id) {
            case "cherry" -> Material.RED_CONCRETE;
            case "lemon" -> Material.YELLOW_CONCRETE;
            case "bell" -> Material.YELLOW_GLAZED_TERRACOTTA;
            case "diamond" -> Material.DIAMOND_BLOCK;
            case "seven" -> Material.REDSTONE_BLOCK;
            case "star" -> Material.SEA_LANTERN;
            default -> Material.GOLD_BLOCK;
        };
    }

    /**
     * Same mapping, for a whole combination or for the whole table.
     */
    static List<Material> blocks(List<Symbol> symbols) {
        List<Material> blocks = new ArrayList<>(symbols.size());
        for (Symbol symbol : symbols) {
            blocks.add(blockOf(symbol.id()));
        }
        return blocks;
    }

    /**
     * Reel look with the symbol name translated for the reader.
     */
    String glyph(CommandSender viewer, Symbol symbol) {
        return symbol.glyph() + " &7" + symbolName(viewer, symbol);
    }

    static String symbolName(Messages messages, CommandSender viewer, Symbol symbol) {
        return messages.forSenderOr(viewer, "panel.symbols." + symbol.id(), symbol.id());
    }

    private String symbolName(CommandSender viewer, Symbol symbol) {
        return symbolName(plugin.messages(), viewer, symbol);
    }

    private static Material iconOf(String id) {
        return switch (id) {
            case "cherry" -> Material.RED_DYE;
            case "lemon" -> Material.YELLOW_DYE;
            case "bell" -> Material.BELL;
            case "diamond" -> Material.DIAMOND;
            case "seven" -> Material.GOLD_INGOT;
            case "star" -> Material.NETHER_STAR;
            default -> Material.EMERALD;
        };
    }

    /**
     * Machine panel: shows the reels and lets you spin again.
     */
    private final class SlotsGui extends Gui {

        private final SlotsGame game;
        private final List<Symbol> reels;
        private final double lastPayout;

        SlotsGui(MultiverseGamblingPlugin plugin, Player player, SlotsGame game, List<Symbol> reels, double lastPayout) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.slots.title"));
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
                        .name(symbol == null
                                ? label(player(), "panel.slots.hidden")
                                : label(player(), "panel.slots.symbol", "symbol", symbol.glyph(),
                                "name", symbolName(player(), symbol)))
                        .lore(symbol == null
                                ? label(player(), "panel.slots.click-spin")
                                : label(player(), "panel.slots.triple-lore",
                                "multiplier", Text.multiplier(symbol.triple())))
                        .glow(symbol != null)
                        .build());
            }

            double bet = Math.min(game.maxBet(), Math.max(game.minBet(), plugin.config().minBet() * 10));
            double balance = plugin.economy().balance(player().getUniqueId());
            boolean afford = balance >= game.minBet();

            set(4, Items.of(Material.PAPER)
                    .name(label(player(), "panel.slots.prize-table"))
                    .lore(game.table().symbols().stream()
                            .map(s -> label(player(), "panel.slots.table-line",
                                    "symbol", s.glyph(), "name", symbolName(player(), s),
                                    "multiplier", Text.multiplier(s.triple()),
                                    "chance", Text.percent(game.table().chanceOf(s.id()))))
                            .toList())
                    .build());

            set(40, Items.of(afford ? Material.EMERALD_BLOCK : Material.RED_CONCRETE)
                    .name(label(player(), afford ? "panel.slots.spin" : "panel.common.not-enough"))
                    .lore(
                            label(player(), "panel.slots.will-stake", "bet",
                                    plugin.economy().format(bet)),
                            label(player(), "panel.common.balance", "balance",
                                    plugin.economy().format(balance)),
                            lastPayout > 0
                                    ? label(player(), "panel.slots.last-prize", "prize",
                                    plugin.economy().format(lastPayout))
                                    : "",
                            "",
                            afford ? label(player(), "panel.common.click-to-spin")
                                    : label(player(), "panel.slots.need-money"))
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
                    .name(label(player(), "panel.common.close"))
                    .build(), e -> close());

            set(44, Items.of(Material.ARROW)
                    .name(label(player(), "panel.common.change-bet"))
                    .build(), e -> {
                close();
                plugin.guis().openBetSelector(player(), game, amount -> game.start(player(), amount));
            });
        }

        @Override
        public String sessionId() {
            return "slots";
        }
    }
}
