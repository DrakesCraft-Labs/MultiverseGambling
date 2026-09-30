package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.WeightedTable;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.WheelShow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Shared jackpot.
 *
 * <p>Everybody puts money in and the tickets are proportional to what was staked, so
 * doubling the bet doubles the chances without changing anybody's edge. The winner
 * takes the whole pot minus the commission the server configures.</p>
 */
public final class JackpotGame extends AbstractGroupGame {

    private static final int SPIN_TICKS = 80;

    /** Colour of each ticket on the wheel drawn on the arena, cycled when out of colours. */
    private static final Material[] SECTOR_COLOURS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.MAGENTA_CONCRETE, Material.CYAN_CONCRETE,
            Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE,
            Material.WHITE_CONCRETE, Material.BROWN_CONCRETE, Material.BLACK_CONCRETE
    };

    private UUID winner;
    /** Wheel painted on the arena, when there is one. */
    private ArenaShow show;

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
        broadcastRoundHeader();
        broadcastPlain("group.jackpot.total-pot",
                "pot", plugin.economy().format(pot.total()));
        for (UUID id : pot.participants()) {
            double stake = pot.amountOf(id);
            double chance = stake / Math.max(0.0001, pot.total());
            broadcastPlain("group.jackpot.line",
                    "player", playerName(id),
                    "amount", plugin.economy().format(stake),
                    "chance", String.format("%.1f%%", chance * 100));
        }
        broadcastPlain("group.jackpot.starting");
    }

    @Override
    protected void tickRound() {
        timer++;
        int elapsed = timer;

        if (elapsed <= 40) {
            if (elapsed % 20 == 0) {
                broadcastPlain("group.jackpot.preparing");
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
            }
            return;
        }

        int spin = elapsed - 40;
        if (spin == 1) {
            // The real draw: tickets proportional to the stake, with provably fair rolls.
            // It happens once, before the wheel starts, so the ball in the arena can land
            // on the ticket that really won.
            WeightedTable<UUID> draw = new WeightedTable<>();
            pot.participants().forEach(id -> draw.add(id, Math.max(0.0001, pot.amountOf(id))));
            winner = draw.isEmpty() ? null : draw.roll(plugin.fair().roll(FairnessService.HOUSE));
            show = startWheelShow(winner);
        }
        if (spin <= SPIN_TICKS) {
            if (show != null) {
                show.tick();
            } else {
                double progress = (double) spin / SPIN_TICKS;
                int wait = 1 + (int) (progress * progress * 8);
                if (spin % wait == 0) {
                    List<UUID> pool = new ArrayList<>(pot.participants());
                    if (!pool.isEmpty()) {
                        actionBarAllKey("group.jackpot.drawing",
                                "player", playerName(Rng.pick(pool)),
                                "pot", plugin.economy().format(pot.total()));
                        soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress);
                    }
                }
            }
            return;
        }
        if (show != null) {
            show.settle();
            show = null;
        }
        if (winner == null) {
            endRound();
            return;
        }
        double total = pot.total();
        double cut = plugin.config().groupHouseCut();
        double prize = total * (1.0 - cut);

        pot.payAllTo(winner, cut);

        broadcastPlain("group.jackpot.banner");
        broadcastPlain("group.jackpot.winner",
                "player", playerName(winner),
                "prize", plugin.economy().format(prize));
        if (cut > 0) {
            broadcastPlain("group.jackpot.commission",
                    "amount", plugin.economy().format(total - prize));
        }
        broadcastPlain("group.jackpot.took",
                "staked", plugin.economy().format(pot.total() > 0 ? total : 0),
                "pot", plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        showTitle(online(winner), "group.jackpot.title", "group.jackpot.winner-subtitle",
                "prize", plugin.economy().format(prize));
        endRound();
    }

    /** Builds the wheel of tickets in the arena; {@code null} when there is no arena. */
    private ArenaShow startWheelShow(UUID winner) {
        ArenaStage stage = gatherArena();
        if (stage == null) {
            return null;
        }
        List<UUID> pool = new ArrayList<>(pot.participants());
        List<Material> sectors = new ArrayList<>(pool.size());
        for (int index = 0; index < pool.size(); index++) {
            sectors.add(SECTOR_COLOURS[index % SECTOR_COLOURS.length]);
        }
        int landing = winner == null ? 0 : Math.max(0, pool.indexOf(winner));
        WheelShow wheel = new WheelShow(plugin, stage, sectors, landing, SPIN_TICKS);
        wheel.start();
        return wheel;
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            show.cancel();
            show = null;
        }
        winner = null;
    }
}
