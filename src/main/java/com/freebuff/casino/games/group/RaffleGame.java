package com.freebuff.casino.games.group;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.Rng;
import com.freebuff.casino.engine.WeightedTable;
import com.freebuff.casino.fair.FairnessService;
import com.freebuff.casino.game.AbstractGroupGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.freebuff.casino.util.Text;

/**
 * Rifa con tres premios.
 *
 * <p>Se venden boletas a precio fijo: cada boleta cuesta lo que marque la
 * configuracion y da una opcion mas. El sorteo reparte el bote en tres premios
 * (70%, 20% y 10%), asi que no basta con tener suerte una vez: hay que estar
 * arriba en el orden de salida.</p>
 */
public final class RaffleGame extends AbstractGroupGame {

    private static final double[] PRIZES = {0.70, 0.20, 0.10};
    private static final int DRAW_TIMER = 100;

    private final Map<UUID, Integer> tickets = new LinkedHashMap<>();

    public RaffleGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("rifa", "Rifa", GameCategory.GRUPO, Material.FIREWORK_STAR)
                .desc("&7Boletas a precio fijo. El sorteo",
                        "&7reparte el bote en tres premios:",
                        "&f70%&7, &f20% &7y &f10%&7.")
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
        broadcastRaw("&8» &f" + player.getName() + " &7compro &f" + capped + " &7boleta(s) por &6"
                + plugin.economy().format(amount) + "&7. Boletas vendidas: &f" + total);
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        broadcastRaw(roundHeader());
        int total = tickets.values().stream().mapToInt(Integer::intValue).sum();
        broadcastRaw("&7Boletas vendidas: &f" + total + " &7a &6"
                + plugin.economy().format(ticketPrice()) + " &7cada una.");
        broadcastRaw("&7Bote a repartir: &6" + plugin.economy().format(pot.total()));
        for (Map.Entry<UUID, Integer> entry : tickets.entrySet()) {
            double share = pot.amountOf(entry.getKey()) / Math.max(0.0001, pot.total());
            broadcastRaw("&7  " + playerName(entry.getKey()) + ": &f" + entry.getValue()
                    + " &7boleta(s) &8(" + String.format("%.1f%%", share * 100) + ")");
        }
        broadcastRaw("&7El sorteo empieza en unos segundos.");
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer <= 40) {
            if (timer % 20 == 0) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                actionBarAll("&7Preparando el bombo...");
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

        // Sorteo sin reemplazo, ponderado por boletas y con azar verificable.
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

        broadcastRaw("&8&m        &r &6SORTEO &8&m        ");
        String[] labels = {"&6Primer premio &7(&f70%&7)", "&eSegundo premio &7(&f20%&7)",
                "&7Tercer premio &7(&f10%&7)"};
        for (int i = 0; i < order.size() && i < labels.length; i++) {
            broadcastRaw(labels[i] + ": &f" + playerName(order.get(i)) + " &8- &6"
                    + plugin.economy().format(total * PRIZES[i]));
        }
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        if (!order.isEmpty()) {
            Player first = online(order.get(0));
            if (first != null) {
                first.showTitle(Title.title(Text.c("&6&lPRIMER PREMIO"),
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
