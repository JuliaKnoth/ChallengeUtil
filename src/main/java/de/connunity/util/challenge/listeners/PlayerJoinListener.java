package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.FoliaSchedulerUtil;
import de.connunity.util.challenge.VersionChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.Statistic;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles player state reset and safe spawning when joining
 */
public class PlayerJoinListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public PlayerJoinListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        String currentWorldName = player.getWorld().getName();
        
        // Check if this player should be reset (after a full reset)
        boolean shouldReset = plugin.shouldResetPlayer(player.getName());
        
        // Check if this is the player's first time joining
        boolean firstJoin = !player.hasPlayedBefore();
        
        // Teleport player to the correct spawn location based on which world they're in
        FoliaSchedulerUtil.runTaskLater(plugin, () -> {
            
            if (currentWorldName.equals(waitingRoomName)) {
                // Player is in waiting room - always teleport to waiting room spawn
                teleportToWaitingRoom(player);
                
            } else if (currentWorldName.equals(speedrunWorldName)) {
                // Player is in speedrun world
                // If this is after a full reset, send to waiting room instead
                if (shouldReset) {
                    teleportToWaitingRoom(player);
                } else if (firstJoin) {
                    // First join - send to speedrun spawn
                    teleportToSpeedrunSpawn(player);
                } else {
                    // Regular rejoin - player spawns at their logout location (Minecraft default)
                    plugin.getLogger().info(player.getName() + " rejoined at their logout location");
                }
                
            } else {
                // Player is in some other world - send them to waiting room for safety
                plugin.getLogger().warning("Player " + player.getName() + " joined in unexpected world: " + currentWorldName);
                plugin.getLogger().warning("Sending to waiting room for safety...");
                teleportToWaitingRoom(player);
            }
            
            // Only reset player state if they were marked for reset (after /fullreset)
            if (shouldReset) {
                resetPlayerState(player);
            }
            
            // Give compass to hunters if manhunt is active and timer is running
            giveCompassToHunterIfNeeded(player);
            
            // Notify ops/hosts about available updates
            notifyAboutUpdate(player);
            
        }, 5L); // Small delay to ensure world is fully loaded
    }
    
    /**
     * Teleport player to waiting room spawn
     */
    private void teleportToWaitingRoom(Player player) {
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        World waitingRoom = Bukkit.getWorld(waitingRoomName);
        
        if (waitingRoom == null) {
            plugin.getLogger().severe("Waiting room world '" + waitingRoomName + "' not found!");
            return;
        }
        
        // Get spawn coordinates from config
        int x = plugin.getConfig().getInt("world.teleport.waiting-room-spawn.x", 0);
        int y = plugin.getConfig().getInt("world.teleport.waiting-room-spawn.y", 65);
        int z = plugin.getConfig().getInt("world.teleport.waiting-room-spawn.z", 0);
        
        Location spawn = new Location(waitingRoom, x + 0.5, y, z + 0.5);
        spawn.setPitch(0);
        spawn.setYaw(0);
        
        FoliaSchedulerUtil.teleport(player, spawn);
        
        // Set player to adventure mode in waiting room
        player.setGameMode(GameMode.ADVENTURE);
        
        plugin.getLogger().info("Teleported " + player.getName() + " to waiting room spawn");
    }
    
    /**
     * Teleport player to speedrun world spawn
     */
    private void teleportToSpeedrunSpawn(Player player) {
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
        
        if (speedrunWorld == null) {
            plugin.getLogger().warning("Speedrun world '" + speedrunWorldName + "' not found!");
            plugin.getLogger().warning("Sending player to waiting room instead...");
            teleportToWaitingRoom(player);
            return;
        }
        
        // Use the world's spawn location (set by FullResetCommand after chunks load)
        Location spawn = speedrunWorld.getSpawnLocation();
        FoliaSchedulerUtil.teleport(player, spawn);
        plugin.getLogger().info("Teleported " + player.getName() + " to speedrun world spawn at Y=" + spawn.getBlockY());
    }
    
    /**
     * Reset player to completely fresh survival state
     * This should only be called after a full reset
     */
    public void resetPlayerState(Player player) {
        // Clear inventory
        player.getInventory().clear();
        
        // Reset health to maximum
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        
        // Reset food level and saturation
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        
        // Reset experience
        player.setLevel(0);
        player.setExp(0.0f);
        
        // Remove all potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        // Set to survival mode
        player.setGameMode(GameMode.SURVIVAL);
        
        // Reset fire ticks
        player.setFireTicks(0);
        
        // Reset fall distance
        player.setFallDistance(0);
        
        // Clear arrows stuck in player
        player.setArrowsInBody(0);
        
        // Reset phantom timer (time since last rest)
        player.setStatistic(Statistic.TIME_SINCE_REST, 0);
        
        // Reset all advancements/achievements (only on full reset)
        FoliaSchedulerUtil.runTask(plugin, () -> {
            // Reset all advancements by iterating through all of them
            Bukkit.advancementIterator().forEachRemaining(advancement -> {
                if (advancement != null) {
                    advancement.getCriteria().forEach(criteria -> {
                        if (player.getAdvancementProgress(advancement).getAwardedCriteria().contains(criteria)) {
                            player.getAdvancementProgress(advancement).revokeCriteria(criteria);
                        }
                    });
                }
            });
            plugin.getLogger().info("Reset advancements for player: " + player.getName());
        });
        
        plugin.getLogger().info("Reset state for player: " + player.getName());
    }
    
    /**
     * Give compass to hunter if manhunt is active and timer is running
     */
    private void giveCompassToHunterIfNeeded(Player player) {
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled == null || !manhuntEnabled) {
            return;
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        // Check if player is a hunter
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (!"hunter".equals(team)) {
            return;
        }
        
        // Give the hunter a compass
        plugin.getManhuntManager().giveCompassToHunter(player);
        plugin.getLogger().info("Gave compass to rejoining hunter: " + player.getName());
    }
    
    /**
     * Notify ops/hosts about available plugin updates
     */
    private void notifyAboutUpdate(Player player) {
        // Only notify ops or players with host permission
        if (!player.isOp() && !player.hasPermission("challenge.host")) {
            return;
        }
        
        VersionChecker versionChecker = plugin.getVersionChecker();
        if (versionChecker == null) {
            return;
        }
        
        // Check asynchronously to avoid blocking player login (important for 24/7 servers)
        FoliaSchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                versionChecker.checkForUpdate().thenAccept(updateAvailable -> {
                    if (updateAvailable && versionChecker.getLatestVersion() != null) {
                        // Schedule message on main thread with player validation
                        FoliaSchedulerUtil.runTask(plugin, () -> {
                            try {
                                // Validate player is still online (could have logged out during async check)
                                if (player == null || !player.isOnline()) {
                                    return;
                                }
                                
                                // Use language manager with placeholders
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("current", versionChecker.getCurrentVersion());
                                placeholders.put("latest", versionChecker.getLatestVersion());
                                String downloadUrlFinal = versionChecker.getDownloadUrl() != null ? 
                                    versionChecker.getDownloadUrl() : 
                                    "https://modrinth.com/plugin/EPkgUkCn";
                                placeholders.put("url", downloadUrlFinal);
                                
                                // Get components from language file
                                Component prefix = plugin.getLanguageManager().getComponent("version.prefix");
                                Component updateMessage = plugin.getLanguageManager().getComponent("version.update-available");
                                Component versionInfo = plugin.getLanguageManager().getComponent("version.version-info", placeholders);
                                Component versionLatest = plugin.getLanguageManager().getComponent("version.version-latest", placeholders);
                                Component downloadLink = plugin.getLanguageManager().getComponent("version.download-link", placeholders);
                                
                                // Send rich components
                                player.sendMessage(prefix.append(updateMessage));
                                player.sendMessage(versionInfo);
                                player.sendMessage(versionLatest);
                                player.sendMessage(downloadLink);
                            } catch (Exception e) {
                                // Silently catch to avoid spam in 24/7 server logs
                                plugin.getLogger().fine("Could not notify player about update: " + e.getMessage());
                            }
                        });
                    }
                }).exceptionally(throwable -> {
                    // Silently handle errors to avoid spam in 24/7 server logs
                    plugin.getLogger().fine("Version check failed for player notification: " + throwable.getMessage());
                    return null;
                });
            } catch (Exception e) {
                // Catch any exceptions to prevent issues with player login
                plugin.getLogger().fine("Error during version check notification: " + e.getMessage());
            }
        });
    }
}
