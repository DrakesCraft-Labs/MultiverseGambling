package com.chagui68.multiversegambling.listener;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.games.group.PokerGame;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;

/**
 * A player getting off a chair of the poker table (sneaking, a teleport, anything) is
 * standing up: the table cashes them out, at once or at the end of the hand.
 */
public final class TableListener implements Listener {

    private final MultiverseGamblingPlugin plugin;

    public TableListener(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        plugin.games().byId(PokerGame.ID).ifPresent(game -> {
            if (game instanceof PokerGame poker) {
                poker.handleDismount(player, event.getDismounted());
            }
        });
    }
}
