package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Handles waiting room/lobby mechanics:
 * - Clears inventory when entering waiting room
 * - Sets health and hunger to max
 * - Prevents health and hunger decline in waiting room
 */
public class WaitingRoomListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public WaitingRoomListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Apply waiting room state when player joins directly into waiting room
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        
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
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        
        // Check if player entered waiting room
        if (player.getWorld().getName().equals(waitingRoomName)) {
            applyWaitingRoomState(player);
            // If player is below the kill threshold immediately after changing worlds, kill them
            checkAndKillIfBelow(player);
        }
    }

    /**
     * Monitor player movement in case they fall below the world border in the waiting room
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
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
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        
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
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        
        // If player is in waiting room, cancel all damage
        if (player.getWorld().getName().equals(waitingRoomName)) {
            event.setCancelled(true);
            // Ensure health stays at max
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        }
    }
    
    /**
     * Apply waiting room state to a player:
     * - Clear inventory
     * - Set health to max
     * - Set hunger to max
     */
    private void applyWaitingRoomState(Player player) {
        // Clear inventory
        player.getInventory().clear();
        
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
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        if (!player.getWorld().getName().equals(waitingRoomName)) return;

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
