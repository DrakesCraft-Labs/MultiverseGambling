package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;

import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.BombShow;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Hot bomb.
 *
 * <p>The bomb passes from hand to hand and blows up when you least expect it. Whoever
 * is holding it at that moment is out and their money feeds the pot, which goes to
 * the last one standing. The fuse is drawn with the provably fair generator, so
 * nobody can know in advance how long it will last.</p>
 */
public final class HotBombGame extends AbstractGroupGame {

    private final Set<UUID> alive = new LinkedHashSet<>();
    private UUID holder;
    private int fuseTicks;
    /** Length the fuse was drawn with, to draw the burning one in proportion. */
    private int fuseTotal = 1;
    private int passTicks;
    /** Bomb painted on the arena, when there is one. */
    private BombShow show;

    public HotBombGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("hot-bomb", "Hot Bomb", GameCategory.GROUP, Material.TNT)
                .desc("&7The bomb passes from hand to hand.",
                        "&7Whoever is holding it when it blows is out.",
                        "&7The last one standing takes the pot.")
                .players(2, 12)
                .build());
    }

    @Override
    protected boolean houseDuelAvailable() {
        return true;
    }

    /**
     * Against the house, the dealer takes the other chair: whoever holds the bomb when it
     * goes off loses, which is an even game, paid with the house edge.
     */
    @Override
    protected double houseDuelMultiplier(Player player) {
        return fairDuelMultiplier(0.5);
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        alive.clear();
        alive.addAll(pot.participants());
        if (houseDuelActive()) {
            alive.add(FairnessService.HOUSE);
        }
        holder = pickHolder(null);
        fuseTicks = rollFuse();
        passTicks = 20;
        broadcastRoundHeader();
        broadcastPlain("group.hot-bomb.pot-line",
                "pot", plugin.economy().format(pot.total()), "players", alive.size());
        broadcastPlain("group.hot-bomb.starts-with", "player", playerName(holder));
        broadcastPlain("group.hot-bomb.fuse",
                "min", (int) plugin.config().hotBombMinSeconds(),
                "max", (int) plugin.config().hotBombMaxSeconds());
        soundAll(Sound.ENTITY_TNT_PRIMED, 0.8f, 1.0f);

        fuseTotal = Math.max(1, fuseTicks);
        ArenaStage stage = gatherArena();
        if (stage != null) {
            show = new BombShow(plugin, stage, Math.max(40, (int) (plugin.config().hotBombMaxSeconds() * 20)));
            show.start();
            show.holder(online(holder), playerName(holder));
        }
    }

    private int rollFuse() {
        double min = plugin.config().hotBombMinSeconds();
        double max = plugin.config().hotBombMaxSeconds();
        double seconds = min + plugin.fair().roll(FairnessService.HOUSE) * (max - min);
        return Math.max(20, (int) (seconds * 20));
    }

    private UUID pickHolder(UUID except) {
        List<UUID> candidates = new ArrayList<>();
        for (UUID id : alive) {
            if (except == null || !id.equals(except)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return except;
        }
        // Picking the next holder is also a casino roll.
        return candidates.get(plugin.fair().rollInt(FairnessService.HOUSE, candidates.size()));
    }

    @Override
    protected void tickRound() {
        timer++;
        if (alive.size() <= 1) {
            settle();
            return;
        }

        passTicks--;
        if (passTicks <= 0) {
            passTicks = Math.max(8, 20 - timer / 100);
            UUID next = pickHolder(holder);
            if (!next.equals(holder)) {
                UUID previous = holder;
                holder = next;
                if (show != null) {
                    show.holder(online(holder), playerName(holder));
                }
                Player previousPlayer = online(previous);
                if (previousPlayer != null) {
                    previousPlayer.sendMessage(plugin.messages().componentPlainFor(previousPlayer,
                            "group.hot-bomb.passed", "player", playerName(holder)));
                }
                Player target = online(holder);
                if (target != null) {
                    target.playSound(target.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.9f, 1.4f);
                    showTitle(target, "group.hot-bomb.you-have-it", "group.hot-bomb.pass-quick");
                }
            }
        }

        fuseTicks--;
        if (show != null) {
            show.burn(fuseTicks / (double) Math.max(1, fuseTotal));
        }
        if (timer % 10 == 0) {
            Player current = online(holder);
            if (current != null) {
                // Only the holder sees the constant reminder that they are carrying it.
                actionBarKey(current, "group.hot-bomb.carrying",
                        "pot", plugin.economy().format(pot.total()));
            }
        }
        if (fuseTicks <= 0) {
            // Provably fair roll: decides whether the fuse runs out on this tick.
            if (plugin.fair().roll(FairnessService.HOUSE) < 0.25) {
                explode();
            } else {
                fuseTicks = 4;
            }
        }
    }

    private void explode() {
        UUID victim = holder;
        alive.remove(victim);
        if (show != null) {
            show.blast();
        }
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
        Player victimPlayer = online(victim);
        if (victimPlayer != null) {
            showTitle(victimPlayer, "group.hot-bomb.boom-title", "group.hot-bomb.boom-subtitle");
        }
        broadcastPlain("group.hot-bomb.boom",
                "player", playerName(victim),
                "amount", plugin.economy().format(isHouse(victim)
                        ? pot.amountOf(houseDuelPlayer()) : pot.amountOf(victim)));
        broadcastPlain("group.hot-bomb.pot-now",
                "pot", plugin.economy().format(pot.total()), "alive", alive.size());

        if (alive.size() <= 1) {
            settle();
            return;
        }
        holder = pickHolder(victim);
        if (show != null) {
            show.holder(online(holder), playerName(holder));
        }
        fuseTicks = rollFuse();
        fuseTotal = Math.max(1, fuseTicks);
        passTicks = 20;
        broadcastPlain("group.hot-bomb.reappears", "player", playerName(holder));
    }

    private void settle() {
        if (houseDuelActive()) {
            UUID survivor = alive.isEmpty() ? FairnessService.HOUSE : alive.iterator().next();
            settleHouseDuel(isHouse(survivor) ? -1 : 1, houseDuelMultiplier(null));
            return;
        }
        if (alive.isEmpty()) {
            // Nobody survived: the pot stays with the house.
            broadcastPlain("group.hot-bomb.no-survivors");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastPlain("group.hot-bomb.banner");
        broadcastPlain("group.hot-bomb.winner",
                "player", playerName(winner), "pot", plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        showTitle(online(winner), "group.hot-bomb.survived", "group.hot-bomb.survived-subtitle",
                "prize", plugin.economy().format(total));
        endRound();
    }

    @Override
    protected void onQuitDuringRound(UUID playerId) {
        alive.remove(playerId);
        if (playerId.equals(holder) && !alive.isEmpty()) {
            holder = pickHolder(playerId);
            if (show != null) {
                show.holder(online(holder), playerName(holder));
            }
        }
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            show.settle();
            show = null;
        }
        alive.clear();
        holder = null;
        fuseTicks = 0;
        fuseTotal = 1;
        passTicks = 0;
    }
}
