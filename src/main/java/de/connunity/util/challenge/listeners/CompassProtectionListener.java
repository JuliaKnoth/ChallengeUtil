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
 * Prevents hunters from dropping their tracking compass and ensures they keep it on death
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
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled == null || !manhuntEnabled) {
            return;
        }
        
        // Check if player is a hunter
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (!"hunter".equals(team)) {
            return;
        }
        
        // Cancel the drop
        event.setCancelled(true);
        player.sendMessage(lang.getComponent("compass.cannot-drop"));
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled == null || !manhuntEnabled) {
            return;
        }
        
        // Check if player is a hunter
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (!"hunter".equals(team)) {
            return;
        }
        
        // Remove compass from drops
        event.getDrops().removeIf(item -> item.getType() == Material.COMPASS);
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled == null || !manhuntEnabled) {
            return;
        }
        
        // Check if player is a hunter
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (!"hunter".equals(team)) {
            return;
        }
        
        // Give compass back after a short delay (to ensure inventory is ready)
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
    }
}
