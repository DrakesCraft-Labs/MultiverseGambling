package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
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
    private int passTicks;

    public HotBombGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("hot-bomb", "Hot Bomb", GameCategory.GROUP, Material.TNT)
                .desc("&7The bomb passes from hand to hand.",
                        "&7Whoever is holding it when it blows is out.",
                        "&7The last one standing takes the pot.")
                .players(2, 12)
                .build());
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        alive.clear();
        alive.addAll(pot.participants());
        holder = pickHolder(null);
        fuseTicks = rollFuse();
        passTicks = 20;
        broadcastRaw(roundHeader());
        broadcastRaw("&7Pot: &6" + plugin.economy().format(pot.total())
                + " &8| &7players: &f" + alive.size());
        broadcastRaw("&cThe bomb starts in the hands of &f" + playerName(holder) + "&c.");
        broadcastRaw("&7Fuse lit: between &f" + (int) plugin.config().hotBombMinSeconds()
                + " &7y &f" + (int) plugin.config().hotBombMaxSeconds() + " &7segundos.");
        soundAll(Sound.ENTITY_TNT_PRIMED, 0.8f, 1.0f);
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
                tell(previous, "&7You passed the bomb to &f" + playerName(holder) + "&7.");
                Player target = online(holder);
                if (target != null) {
                    target.playSound(target.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.9f, 1.4f);
                    target.showTitle(Title.title(Text.c("&cYOU HAVE IT!"),
                            Text.c("&7Pass it on, quick!"), Title.Times.times(
                                    java.time.Duration.ofMillis(100),
                                    java.time.Duration.ofMillis(600),
                                    java.time.Duration.ofMillis(100))));
                }
            }
        }

        fuseTicks--;
        if (timer % 10 == 0) {
            Player current = online(holder);
            if (current != null) {
                // Only the holder sees the constant reminder that they are carrying it.
                current.sendActionBar(Text.c("&cBomb in your hands &8| &7pot &6"
                        + plugin.economy().format(pot.total())));
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
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
        Player victimPlayer = online(victim);
        if (victimPlayer != null) {
            victimPlayer.showTitle(Title.title(Text.c("&c&lBOOM"),
                    Text.c("&7Estabas sosteniendola"), Title.Times.times(
                            java.time.Duration.ofMillis(100),
                            java.time.Duration.ofMillis(1400),
                            java.time.Duration.ofMillis(300))));
        }
        broadcastRaw("&c&lBOOM &8» &f" + playerName(victim)
                + " &7blows up and their stake of &6"
                + plugin.economy().format(pot.amountOf(victim)) + " &7stays in the pot.");
        broadcastRaw("&7Pot now: &6" + plugin.economy().format(pot.total())
                + " &8| &7left: &f" + alive.size());

        if (alive.size() <= 1) {
            settle();
            return;
        }
        holder = pickHolder(victim);
        fuseTicks = rollFuse();
        passTicks = 20;
        broadcastRaw("&7The bomb reappears in the hands of &f" + playerName(holder) + "&7.");
    }

    private void settle() {
        if (alive.isEmpty()) {
            // Nobody survived: the pot stays with the house.
            broadcastRaw("&cNobody survived; the pot goes to the house.");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastRaw("&8&m        &r &6HOT BOMB &8&m        ");
        broadcastRaw("&aGana &f" + playerName(winner) + " &acon &6"
                + plugin.economy().format(total) + " &adel jackpot.");
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        Player winnerPlayer = online(winner);
        if (winnerPlayer != null) {
            winnerPlayer.showTitle(Title.title(Text.c("&a&lSOBREVIVISTE"),
                    Text.c("&f" + plugin.economy().format(total)), Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2500),
                            java.time.Duration.ofMillis(400))));
        }
        endRound();
    }

    @Override
    protected void onQuitDuringRound(UUID playerId) {
        alive.remove(playerId);
        if (playerId.equals(holder) && !alive.isEmpty()) {
            holder = pickHolder(playerId);
        }
    }

    @Override
    protected void onRoundEnd() {
        alive.clear();
        holder = null;
        fuseTicks = 0;
        passTicks = 0;
    }
}
