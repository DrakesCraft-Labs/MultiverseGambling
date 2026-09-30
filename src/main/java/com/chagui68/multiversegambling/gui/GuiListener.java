package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Blocks anything that is not clicking a button inside a casino menu.
 */
public final class GuiListener implements Listener {

    private final MultiverseGamblingPlugin plugin;

    public GuiListener(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Gui gui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(gui.playerId())) {
            return;
        }
        var clicked = event.getClickedInventory();
        if (clicked == null || clicked.getType() == InventoryType.PLAYER) {
            return;
        }
        if (clicked.getHolder() != gui) {
            return;
        }
        try {
            gui.handleClick(event);
        } catch (RuntimeException error) {
            plugin.getLogger().severe("Error while clicking in the casino menu: " + error);
            error.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Gui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.handleClose();
        }
    }
}
