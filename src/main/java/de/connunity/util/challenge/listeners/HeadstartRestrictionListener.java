package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Prevents hunters (manhunt) and viewers (connunity hunt) from:
 * - Breaking blocks during the 2-minute headstart
 * - Picking up items during the 2-minute headstart
 */
public class HeadstartRestrictionListener implements Listener {

    private final ChallengeUtil plugin;
    private final LanguageManager lang;

    public HeadstartRestrictionListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }

        // Check manhunt mode - prevent hunters from breaking blocks during headstart
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled != null && manhuntEnabled) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            
            if ("hunter".equals(team) && plugin.getManhuntManager().isHunterMovementRestricted()) {
                event.setCancelled(true);
                
                // Send message occasionally
                if (player.getTicksLived() % 40 == 0) {
                    long remainingSeconds = plugin.getManhuntManager().getHunterRestrictionTimeRemaining();
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("seconds", String.valueOf(remainingSeconds));
                    player.sendMessage(lang.getComponent("restrictions.cannot-break-blocks-yet", placeholders));
                }
                return;
            }
        }

        // Check connunity hunt mode - prevent viewers from breaking blocks during headstart
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        if (connunityHuntEnabled != null && connunityHuntEnabled) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            
            if ("Viewer".equals(team) && plugin.getConnunityHuntManager().isViewerMovementRestricted()) {
                event.setCancelled(true);
                
                // Send message occasionally
                if (player.getTicksLived() % 40 == 0) {
                    long remainingSeconds = plugin.getConnunityHuntManager().getViewerRestrictionTimeRemaining();
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("seconds", String.valueOf(remainingSeconds));
                    player.sendMessage(lang.getComponent("restrictions.cannot-break-blocks-yet", placeholders));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }

        // Check manhunt mode - prevent hunters from picking up items during headstart
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled != null && manhuntEnabled) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            
            if ("hunter".equals(team) && plugin.getManhuntManager().isHunterMovementRestricted()) {
                event.setCancelled(true);
                
                // Send message occasionally
                if (player.getTicksLived() % 60 == 0) { // Every 3 seconds for pickups
                    long remainingSeconds = plugin.getManhuntManager().getHunterRestrictionTimeRemaining();
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("seconds", String.valueOf(remainingSeconds));
                    player.sendMessage(lang.getComponent("restrictions.cannot-pickup-items-yet", placeholders));
                }
                return;
            }
        }

        // Check connunity hunt mode - prevent viewers from picking up items during headstart
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        if (connunityHuntEnabled != null && connunityHuntEnabled) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            
            if ("Viewer".equals(team) && plugin.getConnunityHuntManager().isViewerMovementRestricted()) {
                event.setCancelled(true);
                
                // Send message occasionally
                if (player.getTicksLived() % 60 == 0) { // Every 3 seconds for pickups
                    long remainingSeconds = plugin.getConnunityHuntManager().getViewerRestrictionTimeRemaining();
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("seconds", String.valueOf(remainingSeconds));
                    player.sendMessage(lang.getComponent("restrictions.cannot-pickup-items-yet", placeholders));
                }
            }
        }
    }
}
