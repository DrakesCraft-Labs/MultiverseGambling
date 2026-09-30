package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Pot;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Group game with chained rounds.
 *
 * <p>This class drives the cycle and it is always the same one, so no game can skip
 * either charging or refunding the money:</p>
 * <ol>
 *   <li>{@code WAITING}: people come in and stake. Reaching the minimum opens the round.</li>
 *   <li>{@code BETTING}: visible countdown; whoever does not stake is left out.</li>
 *   <li>{@code IN_GAME}: the subclass runs the round and calls {@link #endRound()}.</li>
 * </ol>
 *
 * <p>Players who disconnect during a round do not get their money back: their stake
 * stays in the pot and somebody else can win it. If they leave before the round
 * starts, it is refunded in full.</p>
 */
public abstract class AbstractGroupGame extends AbstractGame {

    public enum Phase {
        WAITING, BETTING, IN_GAME
    }

    protected final Pot pot;
    /** Players queued for the next round who have not staked yet. */
    protected final Set<UUID> waiting = new LinkedHashSet<>();
    /** Players who left mid round: still in the pot, but they can no longer win. */
    protected final Set<UUID> gone = new LinkedHashSet<>();

    protected Phase phase = Phase.WAITING;
    protected int timer;
    protected int round;

    protected AbstractGroupGame(MultiverseGamblingPlugin plugin, GameMeta meta) {
        super(plugin, meta);
        this.pot = new Pot(plugin.economy());
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public final void open(Player player) {
        if (!enabled()) {
            message(player, "games.disabled", "game", name());
            return;
        }
        if (!player.hasPermission(permission())) {
            message(player, "general.no-permission");
            return;
        }
        UUID id = player.getUniqueId();

        if (phase == Phase.IN_GAME) {
            if (waiting.add(id)) {
                message(player, "group.queued-next", "game", name());
            } else {
                message(player, "group.already-queued");
            }
            return;
        }

        int seated = pot.size() + waiting.size();
        if (maxPlayers() > 0 && seated >= maxPlayers() && !pot.contains(id) && !waiting.contains(id)) {
            message(player, "group.room-full", "max", maxPlayers());
            return;
        }

        plugin.guis().openBetSelector(player, this, bet -> placeBet(player, bet));
    }

    /** Records (or replaces) the player's stake. */
    protected final void placeBet(Player player, double amount) {
        if (phase == Phase.IN_GAME) {
            message(player, "group.round-in-progress");
            return;
        }
        UUID id = player.getUniqueId();
        if (!pot.add(player, amount)) {
            message(player, "economy.not-enough-money", "bet", plugin.economy().format(amount));
            return;
        }
        waiting.remove(id);
        gone.remove(id);
        message(player, "group.bet-accepted",
                "amount", plugin.economy().format(amount), "game", name());
        onBetPlaced(player, amount);
        broadcastLobby();
        if (phase == Phase.WAITING && pot.size() >= minPlayers()) {
            startBettingWindow();
        }
    }

    // ---------------------------------------------------------------- life cycle

    /** Called once per tick by the game registry. */
    public final void tick() {
        switch (phase) {
            case WAITING -> {
                if (pot.size() >= minPlayers()) {
                    startBettingWindow();
                }
            }
            case BETTING -> {
                if (pot.size() < minPlayers()) {
                    phase = Phase.WAITING;
                    timer = 0;
                    broadcast("group.round-cancelled", "min", minPlayers());
                    return;
                }
                timer--;
                if (timer <= 0) {
                    beginRound();
                } else if (timer <= 5) {
                    broadcast("group.countdown", "seconds", timer);
                    tickSound();
                } else if (timer % 10 == 0) {
                    broadcast("group.time-left", "seconds", timer);
                }
            }
            case IN_GAME -> tickRound();
        }
    }

    private void startBettingWindow() {
        phase = Phase.BETTING;
        timer = plugin.config().groupBettingSeconds();
        broadcast("group.betting-open", "seconds", timer);
    }

    private void beginRound() {
        round++;
        phase = Phase.IN_GAME;
        timer = 0;
        broadcast("group.round-starting", "round", round);
        onRoundStart();
    }

    /**
     * Runs the round. The subclass decides when it ends and must call
     * {@link #endRound()} after paying out the pot.
     */
    protected abstract void tickRound();

    /** Sets up a fresh round. */
    protected abstract void onRoundStart();

    /** Clears the state when a round ends. */
    protected void onRoundEnd() {
    }

    /** Optional hook for when somebody stakes. */
    protected void onBetPlaced(Player player, double amount) {
    }

    /** Hook for when somebody leaves mid round. */
    protected void onQuitDuringRound(UUID playerId) {
    }

    /**
     * Action requested from a chat button, for example
     * {@code /mvgam action shoot}. This is what lets people play without menus.
     */
    @Override
    public void handleAction(Player player, String action, String[] args) {
        message(player, "group.unknown-action");
    }

    @Override
    public final boolean ownsPlayer(UUID playerId) {
        return contains(playerId);
    }

    /** @return true when the player is staking in this game right now. */
    public final boolean contains(UUID playerId) {
        return pot.contains(playerId) || waiting.contains(playerId);
    }

    /** Chat button label, ready to send. */
    protected final Component chatButton(String label, String action, String hover) {
        return Text.button(label, "/mvgam action " + action, hover);
    }

    /** Closes the round: refunds whatever was not settled and goes back to waiting. */
    protected final void endRound() {
        pot.refundAll();
        gone.clear();
        phase = Phase.WAITING;
        timer = 0;
        onRoundEnd();
        broadcast("group.round-finished");
        promptWaiting();
    }

    /** Voids the running round and refunds all the money. */
    public final void abortRound() {
        if (phase == Phase.IN_GAME) {
            broadcast("group.round-voided");
        }
        pot.refundAll();
        waiting.clear();
        gone.clear();
        phase = Phase.WAITING;
        timer = 0;
        onRoundEnd();
    }

    /** Reopens the bet selector for whoever was waiting for their turn. */
    private void promptWaiting() {
        for (UUID id : new ArrayList<>(waiting)) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                waiting.remove(id);
                continue;
            }
            message(player, "group.betting-open-again", "game", name());
        }
    }

    @Override
    public void shutdown() {
        abortRound();
    }

    // ------------------------------------------------------------------ salidas

    /** Handles a player disconnecting. */
    public final void handleQuit(UUID playerId) {
        waiting.remove(playerId);
        if (!pot.contains(playerId)) {
            gone.remove(playerId);
            return;
        }
        if (phase == Phase.IN_GAME) {
            // Their stake stays in the pot but they can no longer win it.
            gone.add(playerId);
            onQuitDuringRound(playerId);
        } else {
            pot.remove(playerId);
            gone.remove(playerId);
            broadcastLobby();
        }
    }

    @Override
    public int activePlayers() {
        return pot.size() + waiting.size();
    }

    @Override
    public List<String> statusLore(CommandSender viewer) {
        List<String> lore = new ArrayList<>();
        if (phase == Phase.IN_GAME) {
            lore.add(label(viewer, "panel.status.in-progress", "round", round));
        } else if (phase == Phase.BETTING) {
            lore.add(label(viewer, "panel.status.betting-open", "seconds", timer));
        } else {
            lore.add(label(viewer, "panel.status.waiting"));
        }
        lore.add(label(viewer, "panel.status.in-room",
                "current", activePlayers(), "max", maxPlayers()));
        lore.add(label(viewer, "panel.status.pot",
                "pot", plugin.economy().shortFormat(pot.total())));
        lore.add(label(viewer, "panel.status.minimum", "min", minPlayers()));
        return lore;
    }

    // --------------------------------------------------------------- utilidades

    protected final boolean isGone(UUID playerId) {
        return gone.contains(playerId);
    }

    // ----------------------------------------------------------------- world shows

    /**
     * The arena of this game in the casino world, ready to paint a show on, or
     * {@code null} when there is nothing to paint on.
     *
     * <p>With {@code world.animations.teleport-players} on the room is gathered round
     * the arena, spread out and all of them facing the middle, so everybody can watch
     * the round instead of reading it in the action bar.</p>
     */
    protected final ArenaStage gatherArena() {
        return gatherArena(ArenaStage.TABLE_PITCH);
    }

    /**
     * Same as {@link #gatherArena()} but with the pitch of the room, so a show standing
     * on the arena is looked at from below.
     */
    protected final ArenaStage gatherArena(float pitch) {
        if (plugin.world() == null || !plugin.config().worldAnimationsEnabled()) {
            return null;
        }
        ArenaStage stage = plugin.world().stage(id());
        if (stage == null || !plugin.config().worldAnimationsTeleport()) {
            return stage;
        }
        stage.load();
        List<Player> audience = new ArrayList<>();
        for (UUID id : pot.participants()) {
            Player player = online(id);
            if (player != null) {
                audience.add(player);
            }
        }
        int distance = plugin.config().worldAnimationsViewDistance();
        for (int index = 0; index < audience.size(); index++) {
            audience.get(index).teleport(stage.watcher(distance, pitch, index, audience.size()));
        }
        return stage;
    }

    /** Participants still around, and therefore able to win. */
    protected final List<UUID> contenders() {
        List<UUID> out = new ArrayList<>();
        for (UUID id : pot.participants()) {
            if (!gone.contains(id) && plugin.getServer().getPlayer(id) != null) {
                out.add(id);
            }
        }
        return out;
    }

    protected final Player online(UUID playerId) {
        return plugin.getServer().getPlayer(playerId);
    }

    protected final String playerName(UUID playerId) {
        Player player = online(playerId);
        if (player != null) {
            return player.getName();
        }
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    /** Sends a language key with the prefix, each player reading it in their own language. */
    protected final void broadcast(String key, Object... replacements) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentFor(player, key, replacements));
            }
        }
    }

    /** Sends already composed text to one player of the room. */
    protected final void tell(UUID playerId, String legacyText) {
        tell(playerId, Text.c(legacyText));
    }

    /** Sends a component (handy for text with buttons) to one player of the room. */
    protected final void tell(UUID playerId, Component component) {
        Player player = online(playerId);
        if (player != null) {
            player.sendMessage(component);
        }
    }

    protected final void broadcastRaw(String legacyText) {
        Component component = Text.c(legacyText);
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    /** Sends a language key without the prefix, each player reading it in their own language. */
    protected final void broadcastPlain(String key, Object... replacements) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentPlainFor(player, key, replacements));
            }
        }
    }

    /**
     * Same as {@link #broadcastPlain(String, Object...)} but the replacements are built
     * for each reader: needed when a value inside the line is itself translated, such as
     * the name of a dice poker hand.
     */
    protected final void broadcastPlainFor(Function<Player, Object[]> replacements, String key) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentPlainFor(player, key,
                        replacements.apply(player)));
            }
        }
    }

    /** Action bar line in the language of every player of the room. */
    protected final void actionBarAllKey(String key, Object... replacements) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendActionBar(plugin.messages().componentPlainFor(player, key, replacements));
            }
        }
    }

    /** Action bar line whose replacements are built for each reader. */
    protected final void actionBarFor(Function<Player, Object[]> replacements, String key) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendActionBar(plugin.messages().componentPlainFor(player, key,
                        replacements.apply(player)));
            }
        }
    }

    /** Sends a language key without the prefix to one player of the room. */
    protected final void tellKeyed(UUID playerId, String key, Object... replacements) {
        Player player = online(playerId);
        if (player != null) {
            player.sendMessage(plugin.messages().componentPlainFor(player, key, replacements));
        }
    }

    /** Round header, each player reading it in their own language. */
    protected final void broadcastRoundHeader() {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentPlainFor(player, "panel.round-header",
                        "game", displayName(player), "round", round));
            }
        }
    }

    protected final void actionBarAll(String legacyText) {
        Component component = Text.c(legacyText);
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendActionBar(component);
            }
        }
    }

    protected final void soundAll(Sound sound, float volume, float pitch) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private Set<UUID> audience() {
        Set<UUID> ids = new LinkedHashSet<>(pot.participants());
        ids.addAll(waiting);
        return ids;
    }

    protected final void broadcastLobby() {
        if (phase == Phase.WAITING && pot.size() < minPlayers()) {
            broadcast("group.waiting-players",
                    "current", pot.size(), "min", minPlayers());
        }
    }

    private void tickSound() {
        soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
    }

}
