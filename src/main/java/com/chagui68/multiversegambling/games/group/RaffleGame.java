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
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.chagui68.multiversegambling.util.Text;

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
        broadcastRaw("&8» &f" + player.getName() + " &7bought &f" + capped + " &7ticket(s) for &6"
                + plugin.economy().format(amount) + "&7. Tickets sold: &f" + total);
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        broadcastRaw(roundHeader());
        int total = tickets.values().stream().mapToInt(Integer::intValue).sum();
        broadcastRaw("&7Boletas vendidas: &f" + total + " &7a &6"
                + plugin.economy().format(ticketPrice()) + " &7each.");
        broadcastRaw("&7Pot to be paid out: &6" + plugin.economy().format(pot.total()));
        for (Map.Entry<UUID, Integer> entry : tickets.entrySet()) {
            double share = pot.amountOf(entry.getKey()) / Math.max(0.0001, pot.total());
            broadcastRaw("&7  " + playerName(entry.getKey()) + ": &f" + entry.getValue()
                    + " &7boleta(s) &8(" + String.format("%.1f%%", share * 100) + ")");
        }
        broadcastRaw("&7The draw starts in a few seconds.");
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer <= 40) {
            if (timer % 20 == 0) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                actionBarAll("&7Preparing the draw...");
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
                    actionBarAll("&7Bombo... &f" + playerName(Rng.pick(pool)) + " &8| &6"
                            + plugin.economy().format(pot.total()));
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

        broadcastRaw("&8&m        &r &6RAFFLE &8&m        ");
        String[] labels = {"&6First prize &7(&f70%&7)", "&eSecond prize &7(&f20%&7)",
                "&7Third prize &7(&f10%&7)"};
        for (int i = 0; i < order.size() && i < labels.length; i++) {
            broadcastRaw(labels[i] + ": &f" + playerName(order.get(i)) + " &8- &6"
                    + plugin.economy().format(total * PRIZES[i]));
        }
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        if (!order.isEmpty()) {
            Player first = online(order.get(0));
            if (first != null) {
                first.showTitle(Title.title(Text.c("&6&lFIRST PRIZE"),
                        Text.c("&f" + plugin.economy().format(total * PRIZES[0])), Title.Times.times(
                                java.time.Duration.ofMillis(200),
                                java.time.Duration.ofMillis(2500),
                                java.time.Duration.ofMillis(400))));
            }
        }
        endRound();
    }

    @Override
    protected void onRoundEnd() {
        tickets.clear();
    }
}
