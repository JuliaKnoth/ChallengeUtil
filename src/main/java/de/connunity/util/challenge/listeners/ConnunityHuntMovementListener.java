package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prevents viewers from moving for the first 2 minutes in Connunity Hunt mode
 * OPTIMIZED: Uses caching to avoid constant disk reads on every player movement
 */
public class ConnunityHuntMovementListener implements Listener {

    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    // Cache player teams to minimize disk reads
    private final Map<UUID, String> teamCache = new HashMap<>();
    private final Map<UUID, Long> teamCacheTime = new HashMap<>();
    private static final long TEAM_CACHE_DURATION = 10000; // 10 seconds

    // Cache challenge enabled status to avoid checking config path on every move event
    private boolean connunityHuntEnabled = false;
    private long lastCacheUpdate = 0;
    private static final long CACHE_REFRESH_INTERVAL = 5000; // 5 seconds

    public ConnunityHuntMovementListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    /**
     * Get cached team for a player (minimizes disk I/O)
     */
    private String getCachedTeam(UUID playerId) {
        long now = System.currentTimeMillis();
        Long cacheTime = teamCacheTime.get(playerId);

        if (cacheTime == null || (now - cacheTime) > TEAM_CACHE_DURATION) {
            String team = plugin.getDataManager().getPlayerTeam(playerId);
            teamCache.put(playerId, team);
            teamCacheTime.put(playerId, now);
            return team;
        }

        return teamCache.get(playerId);
    }

    private void refreshChallengeCache() {
        Boolean enabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        connunityHuntEnabled = enabled != null && enabled;
        lastCacheUpdate = System.currentTimeMillis();
    }

    private boolean hasPlayerMoved(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return false;
        }
        if (event.getFrom().getX() != event.getTo().getX()) return true;
        if (event.getFrom().getY() != event.getTo().getY()) return true;
        if (event.getFrom().getZ() != event.getTo().getZ()) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!hasPlayerMoved(event)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_REFRESH_INTERVAL) {
            refreshChallengeCache();
        }

        if (!connunityHuntEnabled) {
            return;
        }

        // Only restrict movement while timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Get player's cached team (minimal disk I/O)
        String team = getCachedTeam(player.getUniqueId());
        
        // Only restrict viewers
        if (!"Viewer".equals(team)) {
            return;
        }
        
        // Check if viewer movement is restricted
        if (plugin.getConnunityHuntManager().isViewerMovementRestricted()) {
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
                long remainingSeconds = plugin.getConnunityHuntManager().getViewerRestrictionTimeRemaining();
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("seconds", String.valueOf(remainingSeconds));
                player.sendMessage(lang.getComponent("restrictions.cannot-move-yet", placeholders));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        teamCache.remove(playerId);
        teamCacheTime.remove(playerId);
    }
}
