package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents hunters and team race players from dropping their tracking compass and ensures they keep it on death
 */
public class CompassProtectionListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public CompassProtectionListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        
        // Check if dropping a compass
        if (item.getType() != Material.COMPASS) {
            return;
        }
        
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled != null && manhuntEnabled && "hunter".equals(team)) {
            // Prevent hunters from dropping compass in manhunt mode
            event.setCancelled(true);
            player.sendMessage(lang.getComponent("compass.cannot-drop"));
            return;
        }
        
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled != null && teamRaceEnabled && team != null && !team.isEmpty()) {
            // Prevent team race players from dropping compass
            event.setCancelled(true);
            player.sendMessage(lang.getComponent("compass.cannot-drop"));
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled != null && manhuntEnabled && "hunter".equals(team)) {
            // Remove compass from drops for hunters in manhunt mode
            event.getDrops().removeIf(item -> item.getType() == Material.COMPASS);
            return;
        }
        
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled != null && teamRaceEnabled && team != null && !team.isEmpty()) {
            // Remove compass from drops for team race players
            event.getDrops().removeIf(item -> item.getType() == Material.COMPASS);
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled != null && manhuntEnabled && "hunter".equals(team)) {
            // Give compass back to hunters in manhunt mode
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Check if hunter already has a compass
                boolean hasCompass = false;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.COMPASS) {
                        hasCompass = true;
                        break;
                    }
                }
                
                if (!hasCompass) {
                    plugin.getManhuntManager().giveCompassToHunter(player);
                }
            }, 1L); // 1 tick delay
            return;
        }
        
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled != null && teamRaceEnabled && team != null && !team.isEmpty()) {
            // Give compass back to team race players
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Check if player already has a compass
                boolean hasCompass = false;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.COMPASS) {
                        hasCompass = true;
                        break;
                    }
                }
                
                if (!hasCompass) {
                    plugin.getTeamRaceManager().giveCompassToPlayer(player, team);
                }
            }, 1L); // 1 tick delay
            return;
        }
    }
}
