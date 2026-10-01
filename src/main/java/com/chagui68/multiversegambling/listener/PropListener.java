package com.chagui68.multiversegambling.listener;

import com.chagui68.multiversegambling.world.anim.HoloButton;
import com.chagui68.multiversegambling.world.anim.Props;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Keeps the props of the shows out of reach and turns clicks on the floating buttons
 * into game actions: the race horses cannot be ridden, hit or led away, nothing a show
 * spawned can be hurt, and a right or left click on a button presses it.
 */
public final class PropListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isProp(entity)) {
            return;
        }
        event.setCancelled(true);
        // The off hand fires a second event for the same click.
        if (event.getHand() == EquipmentSlot.HAND) {
            HoloButton.press(event.getPlayer(), entity);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (isProp(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Entity entity = event.getAttacked();
        if (!isProp(entity)) {
            return;
        }
        event.setCancelled(true);
        HoloButton.press(event.getPlayer(), entity);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isProp(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private static boolean isProp(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(Props.TAG);
    }
}
