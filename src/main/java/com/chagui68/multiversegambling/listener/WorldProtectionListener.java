package com.chagui68.multiversegambling.listener;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.world.anim.Props;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Keeps the casino world exactly as it was built.
 *
 * <ul>
 *   <li>No creature appears in it: natural spawns, spawners, eggs, breeding... are all
 *   refused. Only what the plugin itself places (the dealer, the race horses and the
 *   other props of the shows) is allowed, and any mob that was saved in a chunk is
 *   removed when the chunk loads.</li>
 *   <li>Only an administrator ({@code mvgam_admin}) can break or place blocks, empty or
 *   fill buckets, or take down frames and paintings. Fire, explosions, falling leaves,
 *   melting ice and trampled farmland cannot change it either.</li>
 * </ul>
 */
public final class WorldProtectionListener implements Listener {

    private static final String ADMIN = "mvgam_admin";

    private final MultiverseGamblingPlugin plugin;

    public WorldProtectionListener(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean casino(World world) {
        return plugin.world() != null && plugin.world().world() != null && plugin.world().world().equals(world);
    }

    private static boolean admin(Entity entity) {
        return entity instanceof Player player && player.hasPermission(ADMIN);
    }

    /**
     * Refuses a change made by a player who is not an administrator, telling them why.
     */
    private void guard(Cancellable event, Player player, World world) {
        if (!casino(world) || player.hasPermission(ADMIN)) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(plugin.messages().componentPlainFor(player, "world.protected"));
    }

    // -------------------------------------------------------------- creatures

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!casino(event.getLocation().getWorld())) {
            return;
        }
        // The props of the shows are spawned by the plugin itself.
        // Armour stands are placed (and guarded) like blocks, see onEntityPlace.
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getEntity() instanceof ArmorStand
                || event.getEntity().getScoreboardTags().contains(Props.TAG)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!casino(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand)
                    && !entity.getScoreboardTags().contains(Props.TAG)) {
                entity.remove();
            }
        }
    }

    // ----------------------------------------------------------------- blocks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        guard(event, event.getPlayer(), event.getBlock().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        guard(event, event.getPlayer(), event.getBlock().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        guard(event, event.getPlayer(), event.getBlock().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        guard(event, event.getPlayer(), event.getBlock().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null) {
            guard(event, event.getPlayer(), event.getEntity().getWorld());
        } else if (casino(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!casino(event.getEntity().getWorld())) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEntity && byEntity.getRemover() instanceof Player player) {
            guard(event, player, event.getEntity().getWorld());
            return;
        }
        event.setCancelled(true);
    }

    /** Armour stands, boats, minecarts and end crystals. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getPlayer() != null) {
            guard(event, event.getPlayer(), event.getEntity().getWorld());
        } else if (casino(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        guard(event, event.getPlayer(), event.getRightClicked().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.FARMLAND
                && casino(event.getClickedBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (casino(event.getBlock().getWorld()) && !admin(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------- fire and nature

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (casino(event.getBlock().getWorld()) && !admin(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (casino(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (casino(event.getBlock().getWorld()) && event.getSource().getType() == Material.FIRE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (casino(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent event) {
        if (casino(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (casino(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (casino(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }
}
