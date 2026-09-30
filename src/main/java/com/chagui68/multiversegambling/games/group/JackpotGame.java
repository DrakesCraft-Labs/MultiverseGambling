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
 * Shared jackpot.
 *
 * <p>Everybody puts money in and the tickets are proportional to what was staked, so
 * doubling the bet doubles the chances without changing anybody's edge. The winner
 * takes the whole pot minus the commission the server configures.</p>
 */
public final class JackpotGame extends AbstractGroupGame {

    private static final int SPIN_TICKS = 80;

    public JackpotGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("jackpot", "Jackpot", GameCategory.GROUP, Material.CHEST)
                .desc("&7Everybody drops money into one pot",
                        "&7and a weighted draw decides who",
                        "&7takes it all.")
                .players(2, 24)
                .build());
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        broadcastRaw(roundHeader());
        broadcastRaw("&8&m                                              ");
        broadcastRaw("&6Total pot: &f" + plugin.economy().format(pot.total()));
        for (UUID id : pot.participants()) {
            double stake = pot.amountOf(id);
            double chance = stake / Math.max(0.0001, pot.total());
            broadcastRaw("&7  " + playerName(id) + ": &f" + plugin.economy().format(stake)
                    + " &8(" + String.format("%.1f%%", chance * 100) + " of the chances)");
        }
        broadcastRaw("&8&m                                              ");
        broadcastRaw("&7The draw starts in a few seconds.");
    }

    @Override
    protected void tickRound() {
        timer++;
        int elapsed = timer;

        if (elapsed <= 40) {
            if (elapsed % 20 == 0) {
                broadcastRaw("&7Preparing the draw...");
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
                            + " &8| &6pot " + plugin.economy().format(pot.total()));
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress * 1.0f);
                }
            }
            return;
        }

        // The real draw: tickets proportional to the stake, with provably fair rolls.
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

        broadcastRaw("&8&m        &r &6JACKPOT &8&m        ");
        broadcastRaw("&6Winner: &f" + playerName(winner) + " &7&8| &6prize: &f"
                + plugin.economy().format(prize));
        if (cut > 0) {
            broadcastRaw("&7House commission: &f" + plugin.economy().format(total - prize));
        }
        broadcastRaw("&7Aposto &f" + plugin.economy().format(pot.total() > 0 ? total : 0)
                + " &7and takes a pot of &f" + plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        Player winnerPlayer = online(winner);
        if (winnerPlayer != null) {
            winnerPlayer.showTitle(net.kyori.adventure.title.Title.title(
                    com.chagui68.multiversegambling.util.Text.c("&6&lTHE POT IS YOURS!"),
                    com.chagui68.multiversegambling.util.Text.c("&f" + plugin.economy().format(prize)),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2500),
                            java.time.Duration.ofMillis(400))));
        }
        endRound();
    }
}
