package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.HorseOdds;
import com.chagui68.multiversegambling.engine.HorseOdds.Runner;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.WeightedTable;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Horse race.
 *
 * <p>Every horse gets a hidden strength that produces both the published odds and the
 * real winner. The winner is drawn with the provably fair generator weighted by that
 * strength, so the payout of each horse is always {@code (1 - edge) / probability}
 * and backing any of them is worth the same.</p>
 */
public final class HorseRaceGame extends AbstractGroupGame {

    private final Map<UUID, Integer> picks = new LinkedHashMap<>();
    private List<Runner> field = List.of();
    private double[][] race = new double[0][];
    private int winner = -1;
    private int step;

    public HorseRaceGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("race", "Horse Race", GameCategory.GROUP, Material.SADDLE)
                .desc("&7Eight horses with their own odds.",
                        "&7Betting on the favourite pays little;",
                        "&7on the outsider, a lot.")
                .players(2, 24)
                .build());
    }

    @Override
    protected void onBetPlaced(Player player, double amount) {
        picks.putIfAbsent(player.getUniqueId(), Rng.intBetween(0, plugin.config().horseRaceRunners() - 1));
        sendHorseChooser(player.getUniqueId());
    }

    private void sendHorseChooser(UUID playerId) {
        int runners = plugin.config().horseRaceRunners();
        for (int i = 0; i < runners; i++) {
            final int horse = i;
            String label = "&7[" + (i + 1) + "]";
            tell(playerId, Text.c("&7Horse &f" + (i + 1) + " &8- ")
                    .append(chatButton(label, "horse " + (i + 1), "&7Bet on horse " + (i + 1))));
        }
    }

    @Override
    protected void onRoundStart() {
        picks.replaceAll((id, horse) -> horse);
        field = HorseOdds.field(plugin.config().horseRaceRunners(), plugin.config().houseEdge());
        winner = drawWinner();
        race = buildRace();
        step = 0;
        timer = 0;

        broadcastRaw(roundHeader());
        StringBuilder odds = new StringBuilder("&7Probabilidades: ");
        for (int i = 0; i < field.size(); i++) {
            odds.append("&f[").append(i + 1).append("]&7 pays &f")
                    .append(Text.multiplier(field.get(i).odds())).append(" &8| ");
        }
        broadcastRaw(odds.toString());
        broadcastRaw("&7Pot: &6" + plugin.economy().format(pot.total()));

        // Whoever did not pick a horse gets their money back.
        for (UUID id : pot.participants()) {
            if (!picks.containsKey(id)) {
                pot.remove(id);
                tell(id, "&7You did not pick a horse, so your stake is refunded.");
            }
        }
        if (pot.size() < minPlayers()) {
            endRound();
            return;
        }
        broadcastRaw("&7The race starts!");
        soundAll(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.0f);
    }

    private int drawWinner() {
        WeightedTable<Integer> draw = new WeightedTable<>();
        for (Runner runner : field) {
            draw.add(runner.index(), runner.strength());
        }
        return draw.roll(plugin.fair().roll(FairnessService.HOUSE));
    }

    /** Builds the whole race at once and makes sure the drawn horse is the winner. */
    private double[][] buildRace() {
        int runners = field.size();
        int steps = plugin.config().horseRaceSteps();
        double[][] positions = new double[runners][steps];
        for (int i = 0; i < runners; i++) {
            double position = 0;
            for (int s = 0; s < steps; s++) {
                position += field.get(i).strength() * (0.5 + Rng.next());
                positions[i][s] = position;
            }
        }
        double best = 0;
        for (int i = 0; i < runners; i++) {
            best = Math.max(best, positions[i][steps - 1]);
        }
        double winnerTotal = positions[winner][steps - 1];
        if (winnerTotal < best && winnerTotal > 0) {
            double boost = best * 1.08 / winnerTotal;
            for (int s = 0; s < steps; s++) {
                double progress = (s + 1.0) / steps;
                positions[winner][s] *= 1 + (boost - 1) * progress * progress;
            }
        }
        return positions;
    }

    @Override
    protected void tickRound() {
        timer++;
        int steps = plugin.config().horseRaceSteps();
        int currentStep = Math.min(steps - 1, timer / 3);
        if (currentStep != step) {
            step = currentStep;
            if (timer % 6 == 0) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.0f + (float) step / steps);
            }
        }
        if (timer % 5 == 0) {
            actionBarAll("&7Race &8| &f" + leaderBoard());
        }
        if (timer >= steps * 3 + 5) {
            resolve();
        }
    }

    private String leaderBoard() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < field.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(race[b][step], race[a][step]));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(3, order.size()); i++) {
            builder.append("[").append(order.get(i) + 1).append("] ");
        }
        return builder.toString();
    }

    private void resolve() {
        double[] odds = new double[field.size()];
        for (Runner runner : field) {
            odds[runner.index()] = runner.odds();
        }
        Map<UUID, Integer> bets = new LinkedHashMap<>(picks);
        double total = pot.total();

        pot.payoutByMultiplier(id -> {
            Integer horse = bets.get(id);
            return horse != null && horse == winner ? odds[horse] : 0;
        });

        broadcastRaw("&8&m        &r &6RACE &8&m        ");
        broadcastRaw("&6The winner is horse &f" + (winner + 1) + " &6(paying &f"
                + Text.multiplier(odds[winner]) + "&6).");
        for (Map.Entry<UUID, Integer> entry : bets.entrySet()) {
            boolean won = entry.getValue() == winner;
            tell(entry.getKey(), won
                    ? "&aYou won with horse &f" + (winner + 1) + "&a: you collect &f"
                            + Text.multiplier(odds[winner])
                    : "&cYour horse &f" + (entry.getValue() + 1) + " &cdid not win.");
        }
        broadcastRaw("&7Pot paid out: &f" + plugin.economy().format(total));
        soundAll(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
        for (Map.Entry<UUID, Integer> entry : bets.entrySet()) {
            if (entry.getValue() == winner) {
                Player player = online(entry.getKey());
                if (player != null) {
                    player.showTitle(Title.title(Text.c("&6&lYOU WON!"),
                            Text.c("&fHorse " + (winner + 1)), Title.Times.times(
                                    java.time.Duration.ofMillis(200),
                                    java.time.Duration.ofMillis(2200),
                                    java.time.Duration.ofMillis(400))));
                }
            }
        }
        endRound();
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("horse".equals(action) && args.length > 0) {
            if (!pot.contains(player.getUniqueId())) {
                message(player, "group.not-betting-this-round");
                return;
            }
            try {
                int horse = Integer.parseInt(args[0]) - 1;
                if (horse < 0 || horse >= plugin.config().horseRaceRunners()) {
                    message(player, "group.invalid-horse");
                    return;
                }
                picks.put(player.getUniqueId(), horse);
                Player online = online(player.getUniqueId());
                if (online != null) {
                    online.sendMessage(Text.c("&7You bet on horse &f" + (horse + 1) + "&7."));
                }
                broadcastRaw("&8» &f" + player.getName() + " &7goes with horse &f" + (horse + 1) + "&7.");
            } catch (NumberFormatException error) {
                message(player, "group.invalid-horse");
            }
            return;
        }
        super.handleAction(player, action, args);
    }

    @Override
    protected void onRoundEnd() {
        picks.clear();
        field = List.of();
        race = new double[0][];
        winner = -1;
        step = 0;
    }
}
