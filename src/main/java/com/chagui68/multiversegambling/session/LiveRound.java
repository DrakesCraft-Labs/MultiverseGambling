package com.chagui68.multiversegambling.session;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.world.anim.ArenaStage;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A round played in a pavilion with floating buttons instead of a menu, for as long as
 * the player takes to decide.
 *
 * <p>It keeps the round registered on the central clock, so the player cannot start a
 * second game meanwhile, and it notices when the round can no longer be played: the
 * player logs out, the server stops, or they walk away from the pavilion. The subclass
 * decides what that means for the money (a refund, an automatic cash out, a stand),
 * exactly like closing the menu of the same game would.</p>
 */
public abstract class LiveRound implements SoloSession {

    /** Blocks a player can walk away from the pavilion before the round is given up. */
    private static final double ABANDON_DISTANCE = 40.0;

    private final MultiverseGamblingPlugin plugin;
    private final UUID playerId;
    private final String sessionId;
    private final ArenaStage stage;
    private boolean over;
    private int age;

    protected LiveRound(MultiverseGamblingPlugin plugin, Player player, String sessionId, ArenaStage stage) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.sessionId = sessionId;
        this.stage = stage;
    }

    /**
     * Registers the round on the central clock.
     */
    public final void begin() {
        plugin.sessions().track(this);
    }

    @Override
    public final UUID playerId() {
        return playerId;
    }

    @Override
    public final String sessionId() {
        return sessionId;
    }

    @Override
    public final void tick() {
        if (over) {
            return;
        }
        age++;
        Player player = player();
        if (player == null) {
            cancel();
            return;
        }
        if (age % 10 == 0 && walkedAway(player)) {
            over = true;
            onAbandon(player);
            return;
        }
        onTick(age);
    }

    private boolean walkedAway(Player player) {
        Location where = player.getLocation();
        if (where.getWorld() != stage.world()) {
            return true;
        }
        Location middle = stage.center();
        double dx = where.getX() - middle.getX();
        double dz = where.getZ() - middle.getZ();
        return dx * dx + dz * dz > ABANDON_DISTANCE * ABANDON_DISTANCE;
    }

    @Override
    public final boolean active() {
        return !over;
    }

    /**
     * The player logged out or the plugin is shutting down.
     */
    @Override
    public final void cancel() {
        if (over) {
            return;
        }
        over = true;
        onCancel();
    }

    /**
     * The round reached its result through the game itself: stop watching it.
     */
    protected final void finish() {
        over = true;
    }

    protected final boolean over() {
        return over;
    }

    protected final Player player() {
        return plugin.getServer().getPlayer(playerId);
    }

    protected final ArenaStage stage() {
        return stage;
    }

    /**
     * Every tick while the round is open.
     */
    protected void onTick(int age) {
    }

    /**
     * The player walked away from the pavilion with the round still open.
     */
    protected abstract void onAbandon(Player player);

    /**
     * The player logged out or the server stopped with the round still open.
     */
    protected abstract void onCancel();
}
