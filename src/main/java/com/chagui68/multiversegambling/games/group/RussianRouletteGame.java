package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Turn based Russian roulette.
 *
 * <p>Every player pulls the trigger on their turn. The chance of a shot is exactly
 * {@code bullets / chambers} on each pull, decided by the provably fair generator, so
 * the round is as auditable as any other casino bet. Whoever falls leaves their money
 * in the pot for the survivor.</p>
 */
public final class RussianRouletteGame extends AbstractGroupGame {

    private static final int FIRST_DELAY_TICKS = 40;

    private final List<UUID> order = new ArrayList<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private int turnIndex;
    private int turnTicks;
    private boolean started;

    public RussianRouletteGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("russian-roulette", "Russian Roulette", GameCategory.GROUP, Material.FLINT_AND_STEEL)
                .desc("&7Players take turns pulling the trigger.",
                        "&7With &f1 bullet in 6 chambers&7, luck",
                        "&7decides who stays on their feet.")
                .players(2, 8)
                .build());
    }

    private int chambers() {
        return plugin.config().russianRouletteChambers();
    }

    private int bullets() {
        return Math.min(plugin.config().russianRouletteBullets(), chambers() - 1);
    }

    private UUID current() {
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        return order.get(turnIndex % Math.max(1, order.size()));
    }

    @Override
    protected void onRoundStart() {
        order.clear();
        order.addAll(pot.participants());
        alive.clear();
        alive.addAll(order);
        turnIndex = 0;
        turnTicks = 0;
        started = false;
        timer = 0;

        broadcastRoundHeader();
        broadcastPlain("panel.common.pot", "pot", plugin.economy().format(pot.total()));
        broadcastPlain("group.russian-roulette.revolver",
                "bullets", bullets(), "chambers", chambers(),
                "chance", Text.percent((double) bullets() / chambers()));
        broadcastPlain("group.russian-roulette.turn",
                "player", playerName(current()),
                "seconds", plugin.config().groupCountdownSeconds());
        prompt();
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer < FIRST_DELAY_TICKS) {
            return;
        }
        if (!started) {
            started = true;
            return;
        }
        if (alive.size() <= 1) {
            settle();
            return;
        }
        turnTicks++;
        UUID currentId = current();
        Player player = online(currentId);
        if (player != null) {
            int left = Math.max(0, plugin.config().groupCountdownSeconds() - turnTicks / 20);
            actionBarKey(player, "group.russian-roulette.your-turn",
                    "seconds", left, "pot", plugin.economy().format(pot.total()));
        }
        if (turnTicks % 20 == 0 && turnTicks / 20 > 0) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.5f);
        }
        if (turnTicks >= plugin.config().groupCountdownSeconds() * 20) {
            broadcastPlain("group.russian-roulette.out-of-time", "player", playerName(currentId));
            pull(currentId);
        }
    }

    /** Pulls the trigger. */
    public void trigger(Player player, boolean fromChat) {
        if (alive.size() <= 1) {
            message(player, "group.russian-roulette-finished");
            return;
        }
        UUID currentId = current();
        if (!currentId.equals(player.getUniqueId())) {
            message(player, "group.russian-roulette-not-your-turn", "player", playerName(currentId));
            return;
        }
        pull(currentId);
    }

    private void pull(UUID shooter) {
        boolean fires = plugin.fair().rollInt(FairnessService.HOUSE, chambers()) < bullets();
        turnTicks = 0;
        Player player = online(shooter);

        if (!fires) {
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.6f);
            }
            broadcastPlain("group.russian-roulette.empty", "player", playerName(shooter));
            advance();
            return;
        }

        alive.remove(shooter);
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        if (player != null) {
            showTitle(player, "group.russian-roulette.bang-title",
                    "group.russian-roulette.bang-subtitle");
        }
        broadcastPlain("group.russian-roulette.bang",
                "player", playerName(shooter),
                "amount", plugin.economy().format(pot.amountOf(shooter)));
        broadcastPlain("group.russian-roulette.pot-now",
                "pot", plugin.economy().format(pot.total()), "alive", alive.size());

        if (alive.size() <= 1) {
            settle();
            return;
        }
        // The cylinder spins again and the revolver moves on to the next player.
        order.remove(shooter);
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        prompt();
    }

    private void advance() {
        turnIndex++;
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        prompt();
    }

    private void prompt() {
        if (alive.size() <= 1) {
            return;
        }
        UUID currentId = current();
        Player player = online(currentId);
        if (player == null) {
            return;
        }
        player.sendMessage(plugin.messages().componentPlainFor(player, "group.russian-roulette.prompt")
                .append(chatButton(
                        plugin.messages().forSender(player, "group.russian-roulette.pull"), "shoot",
                        plugin.messages().forSender(player, "group.russian-roulette.pull-hover",
                                "chance", Text.percent((double) bullets() / chambers())))));
    }

    private void settle() {
        if (alive.isEmpty()) {
            broadcastPlain("group.russian-roulette.no-survivors");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastPlain("group.russian-roulette.banner");
        broadcastPlain("group.russian-roulette.winner",
                "player", playerName(winner), "pot", plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        showTitle(online(winner), "group.russian-roulette.title",
                "group.russian-roulette.title-subtitle",
                "prize", plugin.economy().format(total));
        endRound();
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("shoot".equals(action)) {
            trigger(player, true);
            return;
        }
        super.handleAction(player, action, args);
    }

    @Override
    protected void onQuitDuringRound(UUID playerId) {
        alive.remove(playerId);
        order.remove(playerId);
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        if (alive.size() <= 1 && !alive.isEmpty()) {
            settle();
        }
    }

    @Override
    protected void onRoundEnd() {
        order.clear();
        alive.clear();
        turnIndex = 0;
        turnTicks = 0;
        started = false;
    }
}
