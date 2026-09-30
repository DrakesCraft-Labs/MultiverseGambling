package com.freebuff.casino.games.group;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.HorseOdds;
import com.freebuff.casino.engine.HorseOdds.Runner;
import com.freebuff.casino.engine.Rng;
import com.freebuff.casino.engine.WeightedTable;
import com.freebuff.casino.fair.FairnessService;
import com.freebuff.casino.game.AbstractGroupGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.util.Text;
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
 * Carrera de caballos.
 *
 * <p>Cada caballo recibe una fuerza oculta de la que salen a la vez las
 * probabilidades publicadas y el ganador real. El ganador se sortea con el azar
 * verificable ponderado por esa fuerza, asi que el pago de cada caballo es siempre
 * {@code (1 - ventaja) / probabilidad} y apostar a cualquiera vale lo mismo.</p>
 */
public final class HorseRaceGame extends AbstractGroupGame {

    private final Map<UUID, Integer> picks = new LinkedHashMap<>();
    private List<Runner> field = List.of();
    private double[][] race = new double[0][];
    private int winner = -1;
    private int step;

    public HorseRaceGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("carrera", "Carrera de Caballos", GameCategory.GRUPO, Material.SADDLE)
                .desc("&7Ocho caballos con sus probabilidades.",
                        "&7Apostar al favorito paga poco;",
                        "&7al tapado, mucho.")
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
            tell(playerId, Text.c("&7Caballo &f" + (i + 1) + " &8- ")
                    .append(chatButton(label, "caballo " + (i + 1), "&7Apostar al caballo " + (i + 1))));
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
            odds.append("&f[").append(i + 1).append("]&7 paga &f")
                    .append(Text.multiplier(field.get(i).odds())).append(" &8| ");
        }
        broadcastRaw(odds.toString());
        broadcastRaw("&7Bote: &6" + plugin.economy().format(pot.total()));

        // Quien no haya elegido caballo recupera el dinero.
        for (UUID id : pot.participants()) {
            if (!picks.containsKey(id)) {
                pot.remove(id);
                tell(id, "&7No elegiste caballo, te devolvemos la apuesta.");
            }
        }
        if (pot.size() < minPlayers()) {
            endRound();
            return;
        }
        broadcastRaw("&7¡Empieza la carrera!");
        soundAll(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.0f);
    }

    private int drawWinner() {
        WeightedTable<Integer> draw = new WeightedTable<>();
        for (Runner runner : field) {
            draw.add(runner.index(), runner.strength());
        }
        return draw.roll(plugin.fair().roll(FairnessService.HOUSE));
    }

    /** Genera la carrera entera de golpe y garantiza que gane el caballo sorteado. */
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
            actionBarAll("&7Carrera &8| &f" + leaderBoard());
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

        broadcastRaw("&8&m        &r &6CARRERA &8&m        ");
        broadcastRaw("&6Gana el caballo &f" + (winner + 1) + " &6(pagaba &f"
                + Text.multiplier(odds[winner]) + "&6).");
        for (Map.Entry<UUID, Integer> entry : bets.entrySet()) {
            boolean won = entry.getValue() == winner;
            tell(entry.getKey(), won
                    ? "&aGanaste con el caballo &f" + (winner + 1) + "&a: cobras &f"
                            + Text.multiplier(odds[winner])
                    : "&cTu caballo &f" + (entry.getValue() + 1) + " &cno gano.");
        }
        broadcastRaw("&7Bote repartido: &f" + plugin.economy().format(total));
        soundAll(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
        for (Map.Entry<UUID, Integer> entry : bets.entrySet()) {
            if (entry.getValue() == winner) {
                Player player = online(entry.getKey());
                if (player != null) {
                    player.showTitle(Title.title(Text.c("&6&l¡GANASTE!"),
                            Text.c("&fCaballo " + (winner + 1)), Title.Times.times(
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
        if ("caballo".equals(action) && args.length > 0) {
            if (!pot.contains(player.getUniqueId())) {
                message(player, "grupo.no-estas-en-la-ronda");
                return;
            }
            try {
                int horse = Integer.parseInt(args[0]) - 1;
                if (horse < 0 || horse >= plugin.config().horseRaceRunners()) {
                    message(player, "grupo.caballo-invalido");
                    return;
                }
                picks.put(player.getUniqueId(), horse);
                Player online = online(player.getUniqueId());
                if (online != null) {
                    online.sendMessage(Text.c("&7Has apostado al caballo &f" + (horse + 1) + "&7."));
                }
                broadcastRaw("&8» &f" + player.getName() + " &7va con el caballo &f" + (horse + 1) + "&7.");
            } catch (NumberFormatException error) {
                message(player, "grupo.caballo-invalido");
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
