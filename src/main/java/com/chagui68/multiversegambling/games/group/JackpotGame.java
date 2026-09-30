package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.WeightedTable;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Bote comun.
 *
 * <p>Todos ponen dinero y las papeletas son proporcionales a lo apostado, asi que
 * doblar la apuesta dobla las opciones sin cambiar la ventaja de nadie. El ganador
 * se lleva el bote entero menos la comision que configure el servidor.</p>
 */
public final class JackpotGame extends AbstractGroupGame {

    private static final int SPIN_TICKS = 80;

    public JackpotGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("bote", "Bote Comun", GameCategory.GRUPO, Material.CHEST)
                .desc("&7Todos ponen dinero en un mismo bote",
                        "&7y un sorteo ponderado decide quien",
                        "&7se lo lleva todo.")
                .players(2, 24)
                .build());
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        broadcastRaw(roundHeader());
        broadcastRaw("&8&m                                              ");
        broadcastRaw("&6Bote total: &f" + plugin.economy().format(pot.total()));
        for (UUID id : pot.participants()) {
            double stake = pot.amountOf(id);
            double chance = stake / Math.max(0.0001, pot.total());
            broadcastRaw("&7  " + playerName(id) + ": &f" + plugin.economy().format(stake)
                    + " &8(" + String.format("%.1f%%", chance * 100) + " de opciones)");
        }
        broadcastRaw("&8&m                                              ");
        broadcastRaw("&7El sorteo empieza en unos segundos.");
    }

    @Override
    protected void tickRound() {
        timer++;
        int elapsed = timer;

        if (elapsed <= 40) {
            if (elapsed % 20 == 0) {
                broadcastRaw("&7Preparando el sorteo...");
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
            }
            return;
        }

        int spin = elapsed - 40;
        if (spin <= SPIN_TICKS) {
            double progress = (double) spin / SPIN_TICKS;
            int wait = 1 + (int) (progress * progress * 8);
            if (spin % wait == 0) {
                List<UUID> pool = new ArrayList<>(pot.participants());
                if (!pool.isEmpty()) {
                    actionBarAll("&7Sorteando... &f" + playerName(Rng.pick(pool))
                            + " &8| &6bote " + plugin.economy().format(pot.total()));
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress * 1.0f);
                }
            }
            return;
        }

        // Sorteo real: papeletas proporcionales a lo apostado, con azar verificable.
        WeightedTable<UUID> draw = new WeightedTable<>();
        pot.participants().forEach(id -> draw.add(id, Math.max(0.0001, pot.amountOf(id))));
        if (draw.isEmpty()) {
            endRound();
            return;
        }
        UUID winner = draw.roll(plugin.fair().roll(FairnessService.HOUSE));
        double total = pot.total();
        double cut = plugin.config().groupHouseCut();
        double prize = total * (1.0 - cut);

        pot.payAllTo(winner, cut);

        broadcastRaw("&8&m        &r &6BOTE COMUN &8&m        ");
        broadcastRaw("&6Ganador: &f" + playerName(winner) + " &7&8| &6premio: &f"
                + plugin.economy().format(prize));
        if (cut > 0) {
            broadcastRaw("&7Comision de la casa: &f" + plugin.economy().format(total - prize));
        }
        broadcastRaw("&7Aposto &f" + plugin.economy().format(pot.total() > 0 ? total : 0)
                + " &7y se lleva un bote de &f" + plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        Player winnerPlayer = online(winner);
        if (winnerPlayer != null) {
            winnerPlayer.showTitle(net.kyori.adventure.title.Title.title(
                    com.chagui68.multiversegambling.util.Text.c("&6&l¡BOTE PARA TI!"),
                    com.chagui68.multiversegambling.util.Text.c("&f" + plugin.economy().format(prize)),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2500),
                            java.time.Duration.ofMillis(400))));
        }
        endRound();
    }
}
