package com.freebuff.casino.gui;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.session.SoloSession;
import com.freebuff.casino.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Menu de cofre con acciones por slot.
 *
 * <p>Se registra como sesion, asi que un menu abierto sobrevive a los ticks, se
 * refresca sin parpadeos y se cierra solo si el jugador se va.</p>
 */
public abstract class Gui implements InventoryHolder, SoloSession {

    protected final CasinoPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private boolean opened;
    private boolean swapping;

    protected Gui(CasinoPlugin plugin, Player player, int rows, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Text.c(title));
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    protected final Player player() {
        return player;
    }

    protected final CasinoPlugin plugin() {
        return plugin;
    }

    protected final int size() {
        return inventory.getSize();
    }

    // -------------------------------------------------------------- construccion

    /** Pinta el contenido del menu. Se llama al abrir y en cada refresco. */
    protected abstract void render();

    /** Coloca un item decorativo. */
    protected final void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    protected final void clearActions() {
        actions.clear();
    }

    protected final void fill(ItemStack item) {
        for (int slot = 0; slot < size(); slot++) {
            set(slot, item);
        }
    }

    protected final void fillRow(int row, ItemStack item) {
        int start = row * 9;
        for (int slot = start; slot < start + 9; slot++) {
            set(slot, item);
        }
    }

    /** Rodea el borde del menu con un item, dejando hueco el interior. */
    protected final void border(ItemStack item) {
        int rows = size() / 9;
        fillRow(0, item);
        fillRow(rows - 1, item);
        for (int row = 1; row < rows - 1; row++) {
            set(row * 9, item);
            set(row * 9 + 8, item);
        }
    }

    // ------------------------------------------------------------------ apertura

    public final void show() {
        if (!player.isOnline()) {
            return;
        }
        render();
        opened = true;
        plugin.sessions().track(this);
        player.openInventory(inventory);
    }

    /** Redibuja y reabre el menu manteniendo la accion en curso. */
    public final void refresh() {
        if (!opened || !player.isOnline()) {
            return;
        }
        render();
        swapping = true;
        try {
            player.openInventory(inventory);
        } finally {
            swapping = false;
        }
    }

    /** Cierra el menu de forma intencionada, sin disparar la logica de salida. */
    public final void close() {
        cancel();
    }

    // ------------------------------------------------------------------- eventos

    public final void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    /** Llamado por el listener cuando el jugador cierra el menu a mano. */
    public final void handleClose() {
        if (swapping || !opened) {
            return;
        }
        opened = false;
        onClose();
    }

    protected void onClose() {
    }

    // --------------------------------------------------------------- SoloSession

    @Override
    public final UUID playerId() {
        return player.getUniqueId();
    }

    @Override
    public String sessionId() {
        return "menu";
    }

    @Override
    public final void tick() {
        if (!opened) {
            return;
        }
        onTick();
    }

    /** Para menus animados. */
    protected void onTick() {
    }

    @Override
    public final boolean active() {
        return opened && player.isOnline();
    }

    @Override
    public void cancel() {
        if (!opened) {
            return;
        }
        opened = false;
        if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == this) {
            player.closeInventory();
        }
        onClose();
    }
}
