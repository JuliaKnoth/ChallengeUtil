package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player respawn to ensure they respawn in the correct world
 */
public class PlayerRespawnListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public PlayerRespawnListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        
        // Get the world where the player died
        World deathWorld = player.getWorld();
        String deathWorldName = deathWorld.getName();
        
        // Check if manhunt mode is enabled and timer is running
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        boolean isTimerPaused = plugin.getTimerManager().isPaused();
        boolean allowRespawn = plugin.getConfig().getBoolean("challenge.allow-respawn", true);
        
        // In manhunt mode, handle team-specific respawn logic
        if (manhuntEnabled != null && manhuntEnabled && isTimerRunning && !isTimerPaused) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            
            // Hunters: Use bed spawn if available, otherwise world spawn
            if ("hunter".equals(team)) {
                if (player.getBedSpawnLocation() != null) {
                    Location bedSpawn = player.getBedSpawnLocation();
                    event.setRespawnLocation(bedSpawn);
                    plugin.logDebug("Hunter " + player.getName() + " respawning at bed spawn");
                    return;
                } else {
                    World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
                    if (speedrunWorld != null) {
                        Location respawnLocation = speedrunWorld.getSpawnLocation();
                        event.setRespawnLocation(respawnLocation);
                        plugin.logDebug("Hunter " + player.getName() + " respawning at speedrun spawn (no bed)");
                        return;
                    }
                }
            }
            
            // Spectators: Keep in spectator mode
            if ("spectator".equals(team)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                }, 1L);
                return;
            }
            
            // Runners: Don't respawn (handled in PlayerDeathListener - they become spectators)
            // This code shouldn't be reached for runners, but just in case
            return;
        }
        
        // Not in manhunt mode - use bed spawn if available and respawns are enabled
        if (allowRespawn && player.getBedSpawnLocation() != null) {
            Location bedSpawn = player.getBedSpawnLocation();
            event.setRespawnLocation(bedSpawn);
            plugin.logDebug("Player " + player.getName() + " respawning at bed spawn");
            return;
        }
        
        // Check if player died in speedrun world or its dimensions (Nether/End)
        // Minecraft creates dimensions as: world_name_nether and world_name_the_end
        boolean isInSpeedrunDimension = deathWorldName.equals(speedrunWorldName) ||
                                        deathWorldName.equals(speedrunWorldName + "_nether") ||
                                        deathWorldName.equals(speedrunWorldName + "_the_end");
        
        // If player died in speedrun world or its dimensions, respawn them in speedrun world spawn
        if (isInSpeedrunDimension) {
            World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
            if (speedrunWorld != null) {
                Location respawnLocation = speedrunWorld.getSpawnLocation();
                event.setRespawnLocation(respawnLocation);
                plugin.logDebug("Player " + player.getName() + " died in speedrun dimension (" + deathWorldName + "), respawning at speedrun spawn");
            }
        }
        // If player died in waiting room, respawn them in waiting room
        else if (deathWorldName.equals(waitingRoomName)) {
            World waitingRoom = Bukkit.getWorld(waitingRoomName);
            if (waitingRoom != null) {
                int x = plugin.getConfig().getInt("world.teleport.waiting-room-spawn.x", 0);
                int y = plugin.getConfig().getInt("world.teleport.waiting-room-spawn.y", 65);
                int z = plugin.getConfig().getInt("world.teleport.waiting-room-spawn.z", 0);
                
                Location respawnLocation = new Location(waitingRoom, x + 0.5, y, z + 0.5);
                respawnLocation.setPitch(0);
                respawnLocation.setYaw(0);
                
                event.setRespawnLocation(respawnLocation);
                
                // Set player to adventure mode in waiting room after respawn
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                }, 1L);
                
                plugin.logDebug("Player " + player.getName() + " died in waiting room, respawning at waiting room spawn");
            }
        }
    }
}
