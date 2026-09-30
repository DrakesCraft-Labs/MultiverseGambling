package com.freebuff.casino.games.group;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.DicePoker;
import com.freebuff.casino.fair.FairnessService;
import com.freebuff.casino.game.AbstractGroupGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Poker de dados.
 *
 * <p>Todo el mundo tira cinco dados y gana la mejor mano. Como cada tirada sale del
 * generador verificable, dos jugadores pueden reproducir la partida entera con
 * {@code /casino verificar} y comprobar que nadie hizo trampas.</p>
 */
public final class DicePokerGame extends AbstractGroupGame {

    private final Map<UUID, int[]> hands = new LinkedHashMap<>();
    private int resolvedCount;

    public DicePokerGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("poker-dados", "Poker de Dados", GameCategory.GRUPO, Material.DRAGON_BREATH)
                .desc("&7Cada jugador tira cinco dados.",
                        "&7Gana la mejor mano: repoker, poker,",
                        "&7full, escalera, trio...")
                .players(2, 16)
                .build());
    }

    @Override
    protected void onRoundStart() {
        hands.clear();
        resolvedCount = 0;
        timer = 0;
        broadcastRaw(roundHeader());
        broadcastRaw("&7Repartiendo &f" + pot.size() + " &7manos de cinco dados...");
        // Las tiradas se generan aqui mismo: antes de que nadie pueda reaccionar.
        for (UUID id : pot.participants()) {
            int[] dice = new int[DicePoker.DICE];
            for (int i = 0; i < dice.length; i++) {
                dice[i] = plugin.fair().rollInt(FairnessService.HOUSE, 6) + 1;
            }
            hands.put(id, dice);
        }
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer <= 40) {
            if (timer % 20 == 0) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.1f);
                actionBarAll("&7Los dados ruedan sobre la mesa...");
            }
            return;
        }
        // Una mano por cada dos segundos, para dar suspense.
        int index = (timer - 40) / 40;
        List<UUID> order = new ArrayList<>(hands.keySet());
        if (index < order.size()) {
            if ((timer - 40) % 40 == 0) {
                UUID id = order.get(index);
                int[] dice = hands.get(id);
                broadcastRaw("&8» &f" + playerName(id) + " &7saca &f" + DicePoker.describe(dice)
                        + " &8(&6" + DicePoker.handOf(dice).label() + "&8)");
                soundAll(Sound.BLOCK_ANVIL_LAND, 0.6f, 1.0f + index * 0.1f);
            }
            return;
        }

        // Todos han enseñado: se resuelve el ganador.
        resolvedCount++;
        UUID winner = null;
        int bestScore = Integer.MIN_VALUE;
        List<UUID> tied = new ArrayList<>();
        for (Map.Entry<UUID, int[]> entry : hands.entrySet()) {
            int score = DicePoker.score(entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                winner = entry.getKey();
                tied.clear();
                tied.add(entry.getKey());
            } else if (score == bestScore) {
                tied.add(entry.getKey());
            }
        }

        double total = pot.total();
        if (tied.size() > 1) {
            // Empate real: el bote se reparte a partes iguales.
            broadcastRaw("&6Empate entre " + tied.stream().map(this::playerName).toList()
                    + "&6; el bote se reparte.");
            pot.shareAmong(new java.util.LinkedHashSet<>(tied));
        } else if (winner != null) {
            pot.payAllTo(winner, plugin.config().groupHouseCut());
            broadcastRaw("&8&m        &r &6POKER DE DADOS &8&m        ");
            broadcastRaw("&6Gana &f" + playerName(winner) + " &6con &f"
                    + DicePoker.handOf(hands.get(winner)).label() + "&6: &f"
                    + DicePoker.describe(hands.get(winner)));
        }
        broadcastRaw("&7Bote repartido: &f" + plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        Player winnerPlayer = winner == null ? null : online(winner);
        if (winnerPlayer != null) {
            winnerPlayer.showTitle(net.kyori.adventure.title.Title.title(
                    com.freebuff.casino.util.Text.c("&6&lMEJOR MANO"),
                    com.freebuff.casino.util.Text.c("&f" + DicePoker.handOf(hands.get(winner)).label()),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2200),
                            java.time.Duration.ofMillis(400))));
        }
        endRound();
    }

    @Override
    protected void onRoundEnd() {
        hands.clear();
        resolvedCount = 0;
    }
}
