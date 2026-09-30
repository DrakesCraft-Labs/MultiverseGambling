package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.WeightedTable;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Raffle with three prizes.
 *
 * <p>Tickets are sold at a fixed price: each one costs what the configuration says
 * and gives one more chance. The draw splits the pot into three prizes (70%, 20% and
 * 10%), so being lucky once is not enough: you want to be near the top of the
 * draw order.</p>
 */
public final class RaffleGame extends AbstractGroupGame {

    private static final double[] PRIZES = {0.70, 0.20, 0.10};
    private static final int DRAW_TIMER = 100;

    private final Map<UUID, Integer> tickets = new LinkedHashMap<>();

    public RaffleGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("raffle", "Raffle", GameCategory.GROUP, Material.FIREWORK_STAR)
                .desc("&7Fixed price tickets. The draw splits",
                        "&7the pot into three prizes:",
                        "&f70%&7, &f20% &7and &f10%&7.")
                .players(2, 24)
                .build());
    }

    private double ticketPrice() {
        return plugin.config().raffleTicketPrice();
    }

    @Override
    protected void onBetPlaced(Player player, double amount) {
        int bought = Math.max(1, (int) Math.floor(amount / ticketPrice()));
        int capped = Math.min(bought, plugin.config().raffleMaxTickets());
        tickets.put(player.getUniqueId(), capped);
        int total = tickets.values().stream().mapToInt(Integer::intValue).sum();
        broadcastPlain("group.raffle.tickets-bought",
                "player", player.getName(),
                "tickets", capped,
                "amount", plugin.economy().format(amount),
                "total", total);
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        broadcastRoundHeader();
        int total = tickets.values().stream().mapToInt(Integer::intValue).sum();
        broadcastPlain("group.raffle.sold",
                "total", total, "price", plugin.economy().format(ticketPrice()));
        broadcastPlain("group.raffle.pot", "pot", plugin.economy().format(pot.total()));
        for (Map.Entry<UUID, Integer> entry : tickets.entrySet()) {
            double share = pot.amountOf(entry.getKey()) / Math.max(0.0001, pot.total());
            broadcastPlain("group.raffle.line",
                    "player", playerName(entry.getKey()),
                    "tickets", entry.getValue(),
                    "chance", String.format("%.1f%%", share * 100));
        }
        broadcastPlain("group.raffle.starting");
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer <= 40) {
            if (timer % 20 == 0) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                actionBarAllKey("group.raffle.preparing");
            }
            return;
        }
        int elapsed = timer - 40;
        if (elapsed <= DRAW_TIMER) {
            double progress = (double) elapsed / DRAW_TIMER;
            int wait = 1 + (int) (progress * progress * 8);
            if (elapsed % wait == 0) {
                List<UUID> pool = new ArrayList<>(tickets.keySet());
                if (!pool.isEmpty()) {
                    actionBarAllKey("group.raffle.drawing",
                            "player", playerName(Rng.pick(pool)),
                            "pot", plugin.economy().format(pot.total()));
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress);
                }
            }
            return;
        }

        // Draw without replacement, weighted by tickets and with provably fair rolls.
        List<UUID> order = new ArrayList<>();
        WeightedTable<UUID> draw = new WeightedTable<>();
        Map<UUID, Integer> remaining = new LinkedHashMap<>(tickets);
        for (int prize = 0; prize < PRIZES.length && !remaining.isEmpty(); prize++) {
            draw = new WeightedTable<>();
            for (Map.Entry<UUID, Integer> entry : remaining.entrySet()) {
                draw.add(entry.getKey(), Math.max(1, entry.getValue()));
            }
            UUID winner = draw.roll(plugin.fair().roll(FairnessService.HOUSE));
            order.add(winner);
            remaining.remove(winner);
        }

        double total = pot.total();
        Map<UUID, Double> prizes = new LinkedHashMap<>();
        for (int i = 0; i < order.size() && i < PRIZES.length; i++) {
            prizes.merge(order.get(i), total * PRIZES[i], Double::sum);
        }
        pot.payoutByMultiplier(id -> prizes.getOrDefault(id, 0.0));

        broadcastPlain("group.raffle.banner");
        String[] labels = {"group.raffle.first-prize", "group.raffle.second-prize",
                "group.raffle.third-prize"};
        for (int i = 0; i < order.size() && i < labels.length; i++) {
            final int index = i;
            broadcastPlainFor(player -> new Object[]{
                    "prize", plugin.messages().forSender(player, labels[index]),
                    "player", playerName(order.get(index)),
                    "amount", plugin.economy().format(total * PRIZES[index])},
                    "group.raffle.prize");
        }
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        if (!order.isEmpty()) {
            showTitle(online(order.get(0)), "group.raffle.title", "group.raffle.title-subtitle",
                    "prize", plugin.economy().format(total * PRIZES[0]));
        }
        endRound();
    }

    @Override
    protected void onRoundEnd() {
        tickets.clear();
    }
}
