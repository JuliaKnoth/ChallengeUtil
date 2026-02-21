package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Handles portal travel to ensure players go to the correct Nether/End dimensions
 * OPTIMIZED: Pre-loads destination chunks to prevent lag when traveling between dimensions
 */
public class PortalTravelListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public PortalTravelListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPortalTravel(PlayerPortalEvent event) {
        // Only handle Nether and End portals
        if (event.getCause() != TeleportCause.NETHER_PORTAL && 
            event.getCause() != TeleportCause.END_PORTAL) {
            return;
        }
        
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        World fromWorld = event.getFrom().getWorld();
        
        // Only handle portals from the speedrun world or its dimensions
        if (fromWorld == null) {
            return;
        }
        
        String fromWorldName = fromWorld.getName();
        boolean isSpeedrunWorld = fromWorldName.equals(speedrunWorldName) ||
                                  fromWorldName.equals(speedrunWorldName + "_nether") ||
                                  fromWorldName.equals(speedrunWorldName + "_the_end");
        
        if (!isSpeedrunWorld) {
            // Cancel the event to prevent Bukkit from auto-generating nether/end dimensions
            // for non-speedrun worlds (e.g. waiting_room_nether, waiting_room_the_end)
            event.setCancelled(true);
            return;
        }
        
        // Handle Nether portal
        if (event.getCause() == TeleportCause.NETHER_PORTAL) {
            World targetWorld;
            
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                // Going from Overworld to Nether
                String netherWorldName = speedrunWorldName + "_nether";
                targetWorld = Bukkit.getWorld(netherWorldName);
                
                if (targetWorld == null) {
                    plugin.logWarning("Nether world not found: " + netherWorldName);
                    plugin.logWarning("Creating it now...");
                    // This shouldn't happen if FullResetCommand works correctly
                    return;
                }
                
            } else if (fromWorld.getEnvironment() == World.Environment.NETHER) {
                // Going from Nether to Overworld
                targetWorld = Bukkit.getWorld(speedrunWorldName);
                
                if (targetWorld == null) {
                    plugin.logWarning("Speedrun world not found: " + speedrunWorldName);
                    return;
                }
                
            } else {
                return; // Shouldn't happen
            }
            
            // Calculate the correct destination
            Location from = event.getFrom();
            Location to;
            
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                // Overworld -> Nether: divide by 8
                to = new Location(targetWorld, 
                        from.getX() / 8.0, 
                        from.getY(), 
                        from.getZ() / 8.0);
            } else {
                // Nether -> Overworld: multiply by 8
                to = new Location(targetWorld, 
                        from.getX() * 8.0, 
                        from.getY(), 
                        from.getZ() * 8.0);
            }
            
            event.setTo(to);
            
            // OPTIMIZATION: Pre-load chunks at destination to prevent lag
            preloadChunksAsync(targetWorld, to, event.getPlayer());
            
            plugin.logDebug(event.getPlayer().getName() + " traveling through Nether portal to " + targetWorld.getName());
        }
        
        // Handle End portal
        else if (event.getCause() == TeleportCause.END_PORTAL) {
            World targetWorld;
            
            if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
                // Going from Overworld to End
                String endWorldName = speedrunWorldName + "_the_end";
                targetWorld = Bukkit.getWorld(endWorldName);
                
                if (targetWorld == null) {
                    plugin.logWarning("End world not found: " + endWorldName);
                    plugin.logWarning("Creating it now...");
                    return;
                }
                
                // Teleport to End spawn platform
                Location to = new Location(targetWorld, 100, 49, 0);
                event.setTo(to);
                
                // Check if team race mode is enabled and notify TeamRaceManager
                Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
                if (teamRaceEnabled != null && teamRaceEnabled && plugin.getTimerManager().isRunning() && !plugin.getTimerManager().isPaused()) {
                    // Store the portal location (in the overworld) for compass tracking
                    plugin.getTeamRaceManager().setEndPortalLocation(event.getFrom());
                }
                
                // OPTIMIZATION: Pre-load End platform chunks
                preloadChunksAsync(targetWorld, to, event.getPlayer());
                
            } else if (fromWorld.getEnvironment() == World.Environment.THE_END) {
                // Going from End to Overworld (return portal after dragon)
                Player player = event.getPlayer();
                
                // Check if this is the egg holder escaping during custom end fight
                if (plugin.getCustomEndFightManager().isActive() && 
                    plugin.getCustomEndFightManager().getEggHolder() != null &&
                    plugin.getCustomEndFightManager().getEggHolder().getUniqueId().equals(player.getUniqueId())) {
                    
                    // Egg holder is escaping to overworld - they win!
                    targetWorld = Bukkit.getWorld(speedrunWorldName);
                    
                    if (targetWorld == null) {
                        plugin.logWarning("Speedrun world not found: " + speedrunWorldName);
                        return;
                    }
                    
                    // Teleport to world spawn
                    Location to = targetWorld.getSpawnLocation();
                    event.setTo(to);
                    
                    // OPTIMIZATION: Pre-load spawn chunks
                    preloadChunksAsync(targetWorld, to, player);
                    
                    // Trigger the win condition (delayed to ensure player arrives safely)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        plugin.getCustomEndFightManager().onEggHolderEscapedToOverworld();
                    }, 20L); // 1 second delay
                    
                    plugin.logInfo(player.getName() + " (egg holder) escaped to Overworld - team wins!");
                    
                } else if (plugin.getCustomEndFightManager().isActive() && plugin.getCustomEndFightManager().isEggCollected()) {
                    // Non-egg holder trying to escape during end fight - allow it but game continues
                    targetWorld = Bukkit.getWorld(speedrunWorldName);
                    
                    if (targetWorld == null) {
                        plugin.logWarning("Speedrun world not found: " + speedrunWorldName);
                        return;
                    }
                    
                    // Teleport to world spawn
                    Location to = targetWorld.getSpawnLocation();
                    event.setTo(to);
                    
                    // OPTIMIZATION: Pre-load spawn chunks
                    preloadChunksAsync(targetWorld, to, player);
                    
                    plugin.logDebug(player.getName() + " escaped to Overworld (not egg holder, game continues)");
                    
                } else {
                    // Normal end portal behavior (not during custom end fight)
                    targetWorld = Bukkit.getWorld(speedrunWorldName);
                    
                    if (targetWorld == null) {
                        plugin.logWarning("Speedrun world not found: " + speedrunWorldName);
                        return;
                    }
                    
                    // Teleport to world spawn
                    Location to = targetWorld.getSpawnLocation();
                    event.setTo(to);
                    
                    // OPTIMIZATION: Pre-load spawn chunks
                    preloadChunksAsync(targetWorld, to, player);
                }
                
            } else {
                return; // Shouldn't happen
            }
            
            plugin.logDebug(event.getPlayer().getName() + " traveling through End portal to " + targetWorld.getName());
        }
    }
    
    /**
     * OPTIMIZATION: Pre-load chunks around the destination to prevent lag during teleportation
     * Loads a 3x3 chunk area (centered on destination) asynchronously to avoid blocking the main thread
     */
    private void preloadChunksAsync(World world, Location destination, Player player) {
        int centerChunkX = destination.getBlockX() >> 4;
        int centerChunkZ = destination.getBlockZ() >> 4;
        
        // Load 3x3 chunks around destination (9 chunks total)
        // This provides smooth transition without excessive chunk loading
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                
                // Load chunk asynchronously if not already loaded
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.getChunkAtAsync(chunkX, chunkZ);
                }
            }
        }
    }
}
