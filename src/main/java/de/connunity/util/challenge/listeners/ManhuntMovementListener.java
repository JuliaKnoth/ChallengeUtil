package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Prevents hunters from moving for the first 2 minutes in Manhunt mode
 * OPTIMIZED: Uses caching to avoid constant disk reads on every player movement
 */
public class ManhuntMovementListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    // OPTIMIZATION: Cache challenge enabled status to avoid constant disk reads
    private boolean manhuntEnabled = false;
    private long lastCacheUpdate = 0;
    private static final long CACHE_REFRESH_INTERVAL = 5000; // 5 seconds
    
    // OPTIMIZATION: Cache player teams to avoid constant disk reads
    private final java.util.Map<java.util.UUID, String> teamCache = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> teamCacheTime = new java.util.HashMap<>();
    private static final long TEAM_CACHE_DURATION = 10000; // 10 seconds
    
    public ManhuntMovementListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // PERFORMANCE: Check if player actually moved position first (not just head rotation)
        if (!hasPlayerMoved(event)) {
            return; // Player didn't move, just rotated head - skip all checks
        }
        
        // OPTIMIZATION: Refresh cache periodically (avoid constant disk reads)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate > CACHE_REFRESH_INTERVAL) {
            refreshManhuntCache();
        }
        
        // Check cached manhunt mode status (no disk I/O)
        if (!manhuntEnabled) {
            return;
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Get player's cached team (minimal disk I/O)
        String team = getCachedTeam(player.getUniqueId());
        
        // Only restrict hunters
        if (!"hunter".equals(team)) {
            return;
        }
        
        // Check if hunter movement is restricted
        if (plugin.getManhuntManager().isHunterMovementRestricted()) {
            // Only restrict X/Z movement, allow Y (falling)
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();
            
            if (to != null) {
                // Keep original X/Z coordinates, allow Y to change (for falling)
                to.setX(from.getX());
                to.setZ(from.getZ());
                event.setTo(to);
            }
            
            // Send message occasionally (not spam)
            if (player.getTicksLived() % 40 == 0) { // Every 2 seconds
                long remainingSeconds = plugin.getManhuntManager().getHunterRestrictionTimeRemaining();
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("seconds", String.valueOf(remainingSeconds));
                player.sendMessage(lang.getComponent("restrictions.cannot-move-yet", placeholders));
            }
        }
    }
    
    /**
     * OPTIMIZATION: Refresh manhunt enabled cache from disk (called periodically)
     */
    private void refreshManhuntCache() {
        Boolean manhunt = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        manhuntEnabled = manhunt != null && manhunt;
        lastCacheUpdate = System.currentTimeMillis();
    }
    
    /**
     * OPTIMIZATION: Get cached team for player (avoid constant disk reads)
     */
    private String getCachedTeam(java.util.UUID playerId) {
        long currentTime = System.currentTimeMillis();
        Long cacheTime = teamCacheTime.get(playerId);
        
        // Refresh cache if expired or missing
        if (cacheTime == null || (currentTime - cacheTime) > TEAM_CACHE_DURATION) {
            String team = plugin.getDataManager().getPlayerTeam(playerId);
            teamCache.put(playerId, team);
            teamCacheTime.put(playerId, currentTime);
            return team;
        }
        
        return teamCache.get(playerId);
    }
    
    /**
     * Check if player actually moved position (not just head rotation)
     */
    private boolean hasPlayerMoved(PlayerMoveEvent event) {
        if (event.getFrom().getX() != event.getTo().getX()) return true;
        if (event.getFrom().getY() != event.getTo().getY()) return true;
        if (event.getFrom().getZ() != event.getTo().getZ()) return true;
        return false;
    }
}
