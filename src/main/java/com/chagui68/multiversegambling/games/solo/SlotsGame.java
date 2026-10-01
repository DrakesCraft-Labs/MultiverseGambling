package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.engine.SlotsTable;
import com.chagui68.multiversegambling.engine.SlotsTable.Symbol;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.session.TimedSession;
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

    /** Stake of the last spin of every player, for "spin again". */
    private final java.util.Map<UUID, Double> lastStakes = new java.util.HashMap<>();
    /** True while settling a spin staked with items. */
    private boolean lastWasItems;

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
    public boolean supportsItemBets() {
        return true;
    }

    /** Three of every symbol, best first, and the cherry pair. */
    @Override
    public List<com.chagui68.multiversegambling.game.ItemOutcome> itemOutcomes(Player viewer) {
        SlotsTable table = table();
        List<Symbol> symbols = new ArrayList<>(table.symbols());
        symbols.sort((a, b) -> Double.compare(b.triple(), a.triple()));
        List<com.chagui68.multiversegambling.game.ItemOutcome> out = new ArrayList<>();
        for (Symbol symbol : symbols) {
            double reel = table.chanceOf(symbol.id());
            out.add(new com.chagui68.multiversegambling.game.ItemOutcome(
                    plugin.messages().forSender(viewer, "items.outcome.slots-triple",
                            "symbol", symbol.glyph(), "name", Text.strip(symbolName(viewer, symbol))),
                    symbol.triple(), reel * reel * reel));
            if (symbol.pair() > 0) {
                out.add(new com.chagui68.multiversegambling.game.ItemOutcome(
                        plugin.messages().forSender(viewer, "items.outcome.slots-pair",
                                "symbol", symbol.glyph(), "name", Text.strip(symbolName(viewer, symbol))),
                        symbol.pair(), -1));
            }
        }
        return out;
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
                    icons(result), icons(table.symbols()), total,
                    Text.strip(displayName(player)).toUpperCase(java.util.Locale.ROOT))
                    .paytable(paytable(player));
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
                    // Settled first, so the replay buttons are up before the show decides
                    // how long to stay standing.
                    settleSpin(playerId, wager, result, show);
                    show.settle();
                }

                @Override
                protected void onCancel() {
                    show.cancel();
                    settleSpin(playerId, wager, result, null);
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
                settleSpin(playerId, wager, result, null);
            }

            @Override
            protected void onCancel() {
                settleSpin(playerId, wager, result, null);
            }
        }.run();
    }

    /**
     * Pays a spin whose reels came from the provably fair generator.
     */
    private void settleSpin(UUID playerId, Wager wager, List<Symbol> result, SlotReelsShow show) {
        SlotsTable table = table();
        double multiplier = table.payout(result);
        Player online = plugin.getServer().getPlayer(playerId);
        if (online == null) {
            // Gone before the end: the result was already drawn, so it is paid as drawn.
            // Refunding here would let anybody cancel a round they saw coming out badly.
            settleOffline(playerId, wager, multiplier);
            return;
        }
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
        showResult(online, wager, payout);
        sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, payout > wager.amount() ? 1.3f : 0.9f);
        lastWasItems = wager instanceof com.chagui68.multiversegambling.economy.ItemWager;
        offerSpinAgain(online, wager.amount(), show);
    }

    // ------------------------------------------------------------------- replay

    /**
     * After a spin: spin again with the same stake, or pick another one. On the slot
     * machine of the pavilion these are two floating buttons; anywhere else (and always,
     * as a fallback) two buttons in the chat.
     */
    private void offerSpinAgain(Player player, double stake, SlotReelsShow show) {
        if (lastWasItems) {
            // Items cannot be staked again from a button: they have to be placed in the
            // item menu, so only the usual "play again" is offered.
            offerReplay(player);
            return;
        }
        lastStakes.put(player.getUniqueId(), stake);
        String amount = plugin.economy().format(stake);
        if (show != null) {
            show.offerReplay(player.getUniqueId(),
                    Text.c(plugin.messages().forSender(player, "panel.slots.button-again", "bet", amount)),
                    Text.c(plugin.messages().forSender(player, "panel.slots.button-change")),
                    this::spinAgain,
                    clicker -> plugin.guis().openBetSelector(clicker, this, begin(clicker)));
        }
        player.sendMessage(Text.c(plugin.messages().forSender(player, "panel.slots.again-prompt"))
                .append(Text.c(" "))
                .append(Text.button(plugin.messages().forSender(player, "panel.slots.chat-again", "bet", amount),
                        "/mvgam action spin",
                        plugin.messages().forSender(player, "panel.slots.chat-again-hover", "bet", amount)))
                .append(Text.c(" "))
                .append(Text.button(plugin.messages().forSender(player, "panel.slots.chat-change"),
                        "/mvgam play " + id(),
                        plugin.messages().forSender(player, "panel.slots.chat-change-hover"))));
    }

    /**
     * Spins again with the stake of the last spin, through the same checks as a new game.
     */
    void spinAgain(Player player) {
        Double stake = lastStakes.get(player.getUniqueId());
        if (stake == null) {
            open(player);
            return;
        }
        if (!enabled()) {
            message(player, "games.disabled", "game", name());
            return;
        }
        if (!player.hasPermission(permission())) {
            message(player, "general.no-permission");
            return;
        }
        if (plugin.sessions().busy(player.getUniqueId())) {
            message(player, "games.already-playing");
            return;
        }
        double bet = Math.max(minBet(), Math.min(maxBet(), stake));
        Wager wager = stake(player, bet);
        if (wager != null) {
            spin(player, wager);
        }
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("spin".equals(action)) {
            spinAgain(player);
            return;
        }
        super.handleAction(player, action, args);
    }

    /**
     * The prize table, written on a sign beside the slot machine.
     */
    private List<String> paytable(Player viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(plugin.messages().forSender(viewer, "panel.slots.prize-table"));
        for (Symbol symbol : table().symbols()) {
            String line = symbol.glyph() + " &f" + Text.strip(symbolName(viewer, symbol)) + "  &e"
                    + Text.multiplier(symbol.triple());
            if (symbol.pair() > 0) {
                line += plugin.messages().forSender(viewer, "panel.slots.pair-suffix",
                        "multiplier", Text.multiplier(symbol.pair()));
            }
            lines.add(line);
        }
        return lines;
    }

    /**
     * Symbols as the drums of the slot machine in the arena show them.
     */
    static List<Material> icons(List<Symbol> symbols) {
        List<Material> icons = new ArrayList<>(symbols.size());
        for (Symbol symbol : symbols) {
            icons.add(reelIconOf(symbol.id()));
        }
        return icons;
    }

    /**
     * Item drawn on a drum for each symbol: round fruit, a bell, a gem, a gold bar...
     */
    static Material reelIconOf(String id) {
        return switch (id) {
            case "cherry" -> Material.SWEET_BERRIES;
            case "lemon" -> Material.YELLOW_DYE;
            case "bell" -> Material.BELL;
            case "diamond" -> Material.DIAMOND;
            case "seven" -> Material.GOLD_INGOT;
            case "star" -> Material.NETHER_STAR;
            default -> Material.EMERALD;
        };
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
}
