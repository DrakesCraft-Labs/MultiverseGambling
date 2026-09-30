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
import net.kyori.adventure.title.Title;
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

        broadcastRaw(roundHeader());
        broadcastRaw("&7Pot: &6" + plugin.economy().format(pot.total()));
        broadcastRaw("&7Revolver: &f" + bullets() + " bullet(s) &7in &f" + chambers()
                + " chambers &8(&7" + Text.percent((double) bullets() / chambers())
                + " per pull&8)");
        broadcastRaw("&7Turn of &f" + playerName(current()) + "&7. You have &f"
                + plugin.config().groupCountdownSeconds() + " &7segundos.");
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
            player.sendActionBar(Text.c("&7Your turn &8| &f" + left
                    + "s &8| &7pot &6" + plugin.economy().format(pot.total())));
        }
        if (turnTicks % 20 == 0 && turnTicks / 20 > 0) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.5f);
        }
        if (turnTicks >= plugin.config().groupCountdownSeconds() * 20) {
            broadcastRaw("&7Out of time for &f" + playerName(currentId)
                    + "&7, the trigger is pulled for them.");
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
            broadcastRaw("&8» &f" + playerName(shooter) + " &7pulls the trigger... &a&lclick&7.");
            advance();
            return;
        }

        alive.remove(shooter);
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        if (player != null) {
            player.showTitle(Title.title(Text.c("&c&lBANG"),
                    Text.c("&7Your stake feeds the pot"), Title.Times.times(
                            java.time.Duration.ofMillis(100),
                            java.time.Duration.ofMillis(1400),
                            java.time.Duration.ofMillis(300))));
        }
        broadcastRaw("&c&lBANG &8» &f" + playerName(shooter)
                + " &7cae y sus &6" + plugin.economy().format(pot.amountOf(shooter))
                + " &7go to the pot.");
        broadcastRaw("&7Pot: &6" + plugin.economy().format(pot.total())
                + " &8| &7left: &f" + alive.size());

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
        player.sendMessage(Text.c("&8» &7Your turn: pull the trigger whenever you want.")
                .append(chatButton("&c&lPULL THE TRIGGER", "shoot",
                        "&7Chance of a shot: "
                                + Text.percent((double) bullets() / chambers()))));
    }

    private void settle() {
        if (alive.isEmpty()) {
            broadcastRaw("&cNobody was left standing; the pot goes to the house.");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastRaw("&8&m        &r &6RULETA RUSA &8&m        ");
        broadcastRaw("&a&f" + playerName(winner) + " &ais the last one standing and takes &6"
                + plugin.economy().format(total) + "&a.");
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
