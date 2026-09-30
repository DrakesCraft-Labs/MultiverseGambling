package com.freebuff.casino.listener;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.game.AbstractGroupGame;
import com.freebuff.casino.game.Game;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Cierra partidas y salas cuando un jugador entra o sale del servidor. */
public final class PlayerListener implements Listener {

    private final CasinoPlugin plugin;

    public PlayerListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.economy().ensureAccount(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Primero cerrar la partida individual: devuelve lo que corresponda.
        plugin.sessions().cancel(id);
        // Despues sacar al jugador de cualquier sala activa.
        for (Game game : plugin.games().all()) {
            if (game instanceof AbstractGroupGame group) {
                group.handleQuit(id);
            }
        }
    }
}
