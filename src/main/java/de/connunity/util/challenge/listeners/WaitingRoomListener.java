package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Handles waiting room/lobby mechanics:
 * - Clears inventory when entering waiting room
 * - Sets health and hunger to max
 * - Prevents health and hunger decline in waiting room
 */
public class WaitingRoomListener implements Listener {
    
    private final ChallengeUtil plugin;
    private String waitingRoomName;
    
    public WaitingRoomListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
    }
    
    /**
     * Update cached waiting room name (call when config is reloaded)
     */
    public void updateWaitingRoomName() {
        this.waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
    }
    
    /**
     * Apply waiting room state when player joins directly into waiting room
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in waiting room
        if (player.getWorld().getName().equals(waitingRoomName)) {
            // Apply waiting room state with a small delay to ensure other listeners have run
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyWaitingRoomState(player);
                // Ensure we kill them if they are below the threshold right after join
                checkAndKillIfBelow(player);
            }, 10L);
        }
    }
    
    /**
     * Apply waiting room state when player changes worlds
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        // Check if player entered waiting room
        if (player.getWorld().getName().equals(waitingRoomName)) {
            applyWaitingRoomState(player);
            // If player is below the kill threshold immediately after changing worlds, kill them
            checkAndKillIfBelow(player);
        }
    }

    /**
     * Monitor player movement in case they fall below the world border in the waiting room
     * OPTIMIZED: Only checks if player actually moved position and is in waiting room
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // CRITICAL PERFORMANCE: Check if player actually moved position (not just head rotation)
        if (event.getFrom().getX() == event.getTo().getX() &&
            event.getFrom().getY() == event.getTo().getY() &&
            event.getFrom().getZ() == event.getTo().getZ()) {
            return; // Player didn't move, just rotated head - skip all checks
        }
        
        Player player = event.getPlayer();
        
        // CRITICAL PERFORMANCE: Only check if player is in waiting room world
        if (!player.getWorld().getName().equals(waitingRoomName)) {
            return;
        }
        
        checkAndKillIfBelow(player);
    }

    /**
     * Monitor teleports (e.g., ender pearl, commands) to ensure teleporting below threshold is handled
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        // Run the check slightly later to allow the teleport to complete
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> checkAndKillIfBelow(player), 1L);
    }
    
    /**
     * Prevent food level from decreasing in waiting room
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // If player is in waiting room, cancel food level changes
        if (player.getWorld().getName().equals(waitingRoomName)) {
            event.setCancelled(true);
            // Ensure food level stays at max
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }
    
    /**
     * Prevent damage in waiting room
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // If player is in waiting room, cancel all damage
        if (player.getWorld().getName().equals(waitingRoomName)) {
            event.setCancelled(true);
            // Ensure health stays at max
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        }
    }
    
    /**
     * Handle item entities in waiting room
     * Allow items to drop naturally but remove them after a short time
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getWorld().getName().equals(waitingRoomName)) {
            // Don't cancel - let items drop naturally
            // Instead, remove them after 5 seconds (100 ticks)
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getEntity().isValid() && !event.getEntity().isDead()) {
                    event.getEntity().remove();
                }
            }, 100L);
            plugin.getLogger().fine("Item spawned in waiting room (will be removed after 5s): " + event.getEntity().getItemStack().getType());
        }
    }
    
    /**
     * Prevent creatures from spawning in waiting room
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld().getName().equals(waitingRoomName)) {
            event.setCancelled(true);
            plugin.getLogger().fine("Prevented creature spawn in waiting room: " + event.getEntityType());
        }
    }
    
    /**
     * Prevent any non-player entities from spawning in waiting room
     * This is a catch-all for any entity types not covered by specific events
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        
        // Only allow players, remove all other entities
        if (!(entity instanceof Player) && entity.getWorld().getName().equals(waitingRoomName)) {
            event.setCancelled(true);
            plugin.getLogger().fine("Prevented entity spawn in waiting room: " + event.getEntityType());
        }
    }
    
    /**
     * Disable spawn chunk loading for the waiting room world when it loads
     * This prevents chunks from staying loaded unnecessarily
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(WorldLoadEvent event) {
        if (event.getWorld().getName().equals(waitingRoomName)) {
            event.getWorld().setKeepSpawnInMemory(false);
            plugin.getLogger().info("Disabled spawn chunk loading for waiting room: " + waitingRoomName);
        }
    }
    
    /**
     * Apply waiting room state to a player:
     * - Clear inventory (including torches)
     * - Set health to max
     * - Set hunger to max
     */
    private void applyWaitingRoomState(Player player) {
        // Clear inventory (this already removes all items including torches)
        player.getInventory().clear();
        
        // Additional safety: explicitly remove any torches that might have gotten through
        player.getInventory().remove(org.bukkit.Material.TORCH);
        player.getInventory().remove(org.bukkit.Material.WALL_TORCH);
        player.getInventory().remove(org.bukkit.Material.SOUL_TORCH);
        player.getInventory().remove(org.bukkit.Material.SOUL_WALL_TORCH);
        player.getInventory().remove(org.bukkit.Material.REDSTONE_TORCH);
        player.getInventory().remove(org.bukkit.Material.REDSTONE_WALL_TORCH);
        
        // Set health to maximum
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(maxHealth);
        
        // Set food level and saturation to maximum
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        
        plugin.getLogger().info("Applied waiting room state to player: " + player.getName());
    }

    // Minimum Y coordinate allowed in the waiting room; falling below this kills the player
    private static final double WAITING_ROOM_MIN_Y = -10.0;

    /**
     * Kill the player if they are in the waiting room and below the minimum Y threshold
     */
    private void checkAndKillIfBelow(Player player) {
        if (player == null || !player.isOnline()) return;
        // World check already done in onPlayerMove for performance
        // This method is now only called for players already confirmed to be in waiting room

        double y = player.getLocation().getY();
        if (y < WAITING_ROOM_MIN_Y) {
            // Set health to zero to kill the player
            try {
                player.setHealth(0.0);
            } catch (Throwable t) {
                // Fallback: damage the player if setHealth throws for some reason
                player.damage(Float.MAX_VALUE);
            }
        }
    }
}
