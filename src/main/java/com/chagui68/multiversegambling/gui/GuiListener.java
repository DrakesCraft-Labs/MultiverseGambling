package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

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
        if (gui.acceptsItems() && allowItemMove(event, gui)) {
            // The click only moved items through the chest: let it happen and refresh
            // what the menu says about them once the move is done.
            plugin.getServer().getScheduler().runTask(plugin, gui::contentsChanged);
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
        if (!(event.getView().getTopInventory().getHolder() instanceof Gui gui)) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        boolean allowed = gui.acceptsItems() && event.getWhoClicked().getUniqueId().equals(gui.playerId());
        for (int raw : event.getRawSlots()) {
            if (raw < top && !gui.editable(raw)) {
                allowed = false;
            }
        }
        if (!allowed) {
            event.setCancelled(true);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, gui::contentsChanged);
    }

    /**
     * True when a click in a menu with an item chest only moves items between the
     * player's inventory and the chest. Collecting to the cursor is always refused: it
     * would sweep matching items out of any slot, locked ones included.
     */
    private static boolean allowItemMove(InventoryClickEvent event, Gui gui) {
        if (!event.getWhoClicked().getUniqueId().equals(gui.playerId())) {
            return false;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            return false;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            // Outside the window: dropping what is on the cursor.
            return true;
        }
        if (clicked.getType() == InventoryType.PLAYER) {
            // A shift click sends items up into the first free slots, and in such a menu
            // the only free slots are the chest.
            return true;
        }
        return clicked.getHolder() == gui && gui.editable(event.getSlot());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.handleClose();
        }
    }
}
