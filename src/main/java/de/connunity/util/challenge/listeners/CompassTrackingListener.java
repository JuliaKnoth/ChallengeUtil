package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import de.connunity.util.challenge.manhunt.ManhuntManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles compass right-click interactions for manhunt tracking
 */
public class CompassTrackingListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public CompassTrackingListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCompassRightClick(PlayerInteractEvent event) {
        // Only handle right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if holding a compass
        if (item == null || item.getType() != Material.COMPASS) {
            return;
        }
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        
        // Handle Team Race mode
        if (teamRaceEnabled != null && teamRaceEnabled) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            if (team != null && !team.isEmpty()) {
                // Check if timer is running
                if (!plugin.getTimerManager().isRunning()) {
                    return;
                }
                
                // Switch tracked team
                plugin.getTeamRaceManager().switchTrackedTeam(player);
                
                // Cancel the event to prevent normal compass behavior
                event.setCancelled(true);
                return;
            }
        }
        
        // Handle Manhunt mode
        if (manhuntEnabled == null || !manhuntEnabled) {
            return;
        }
        
        // Check if player is a hunter
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (!"hunter".equals(team)) {
            return;
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning()) {
            return;
        }
        
        ManhuntManager manhuntManager = plugin.getManhuntManager();
        
        // Try to use the compass charge
        boolean success = manhuntManager.useCompassCharge(player);
        
        if (success) {
            player.sendMessage(lang.getComponent("compass.charge-activated"));
        } else {
            // Get remaining cooldown time and show in chat
            long remainingTime = manhuntManager.getCompassCooldownRemaining(player);
            if (remainingTime > 0) {
                long seconds = (remainingTime / 1000) % 60;
                long minutes = (remainingTime / 1000) / 60;
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("time", String.format("%d:%02d", minutes, seconds));
                player.sendMessage(lang.getComponent("compass.cooldown", placeholders));
            } else {
                player.sendMessage(lang.getComponent("compass.not-charged"));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        }
        
        // Cancel the event to prevent normal compass behavior
        event.setCancelled(true);
    }
}
